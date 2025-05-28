package bank.pf.service;

import bank.pf.dto.LoanApplicationReceivedEvent;
import bank.pf.dto.LoanApplicationRequest;
import bank.pf.dto.UpdateLoanStatusRequest;
import bank.pf.entity.LoanApplication;
import bank.pf.enums.LoanStatus;
import bank.pf.exception.ApplicationNotFoundException;
import bank.pf.exception.ValidationException;
import bank.pf.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final KafkaTemplate<String, LoanApplicationReceivedEvent> kafkaTemplate;
    private final ExternalValidationService externalValidationService;

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${app.kafka.topics.loan-application-received}")
    private String loanApplicationReceivedTopic;

    // Separar logica em responsabilidades
    // Padronizar logs
    // Remover RuntimeException e colocar exceções customizadas
    public String submitApplication(LoanApplicationRequest request) throws ValidationException, ExecutionException, InterruptedException {
        log.info("Thread (submitApplication start for CPF {}): {}", request.cpf(), Thread.currentThread());
        List<String> validationErrors = new ArrayList<>();

        int age = Period.between(request.dateOfBirth(), LocalDate.now()).getYears();
        if (age < 18 || age > 75) {
            validationErrors.add("Idade do solicitante deve ser entre 18 e 75 anos. Idade calculada: " + age);
        }


        if (request.monthlyIncome().compareTo(new BigDecimal("1200.00")) < 0) {
            validationErrors.add("Renda mensal mínima de R$ 1.200,00 não atingida.");
        }

        // --- Validações Externas (executadas em paralelo usando Virtual Threads via CompletableFuture) ---
        CompletableFuture<CpfValidationResult> cpfValidationFuture = externalValidationService.validateCpfStatus(request.cpf());
        CompletableFuture<AccountValidationResult> accountValidationFuture = externalValidationService.checkAccountActive(request.cpf());
        CompletableFuture<InternalRestrictionResult> restrictionFuture = externalValidationService.checkInternalRestrictions(request.cpf());

        // Espera todas as validações externas completarem
        CompletableFuture.allOf(cpfValidationFuture, accountValidationFuture, restrictionFuture).join();

        CpfValidationResult cpfResult = cpfValidationFuture.get();
        if (!cpfResult.isRegular()) {
            validationErrors.add(cpfResult.message());
        }

        AccountValidationResult accountResult = accountValidationFuture.get();
        if (!accountResult.isActive()) {
            validationErrors.add(accountResult.message());
        }

        InternalRestrictionResult restrictionResult = restrictionFuture.get();
        if (restrictionResult.hasRestriction()) {
            validationErrors.add(restrictionResult.message());
        }
        // --- Fim Validações Externas ---


        // Java 21 Switch Pattern Matching (exemplo simples para status de validação)
        // Poderia ser usado de forma mais complexa se tivéssemos diferentes tipos de validações
        String validationOutcome;
        switch (validationErrors.size()) {
            case 0 -> validationOutcome = "Validação inicial bem-sucedida.";
            case 1 -> validationOutcome = "Falha na validação inicial: " + validationErrors.getFirst();
            default ->
                    validationOutcome = "Múltiplas falhas na validação inicial: " + String.join(", ", validationErrors);
        }

        log.info("Resultado da validação para CPF {}: {}", request.cpf(), validationOutcome);
        if (!validationErrors.isEmpty()) {
            throw new ValidationException("Falha na validação da solicitação: " + String.join("; ", validationErrors));
        }

        LoanApplication loanApplication = LoanApplication.builder()
                .id(UUID.randomUUID().toString())
                .cpf(request.cpf())
                .dateOfBirth(request.dateOfBirth())
                .amountRequested(request.amountRequested())
                .numberOfInstallments(request.numberOfInstallments())
                .monthlyIncome(request.monthlyIncome())
                .status(LoanStatus.PENDING_ASSESSMENT)
                .build();

        // Persistir no MongoDB usando Virtual Thread
        CompletableFuture<LoanApplication> dbPersistFuture = CompletableFuture.supplyAsync(() -> {
            log.info("Thread (DB persist for CPF {}): {}", loanApplication.getCpf(), Thread.currentThread());
            return loanApplicationRepository.save(loanApplication);
        }, virtualThreadExecutor);

        LoanApplication savedApplication = dbPersistFuture.get(); // Espera a persistência
        log.info("Solicitação de empréstimo persistida com ID: {}", savedApplication.getId());

        LoanApplicationReceivedEvent event = new LoanApplicationReceivedEvent(
                savedApplication.getId(),
                savedApplication.getCpf(),
                savedApplication.getDateOfBirth(),
                savedApplication.getAmountRequested(),
                savedApplication.getNumberOfInstallments(),
                savedApplication.getMonthlyIncome(),
                Instant.now()
        );

        // Publicar no Kafka usando Virtual Thread
        CompletableFuture<SendResult<String, LoanApplicationReceivedEvent>> kafkaPublishFuture =
                CompletableFuture.supplyAsync(() -> {
                    log.info("Thread (Kafka publish for AppID {}): {}", event.applicationId(), Thread.currentThread());
                    try {
                        return kafkaTemplate.send(loanApplicationReceivedTopic, event.applicationId(), event).get();
                    } catch (InterruptedException | ExecutionException e) {
                        log.error("Erro ao publicar evento Kafka para AppID {}: {}", event.applicationId(), e.getMessage());
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Falha ao publicar evento Kafka", e);
                    }
                }, virtualThreadExecutor);


        try {
            SendResult<String, LoanApplicationReceivedEvent> sendResult = kafkaPublishFuture.get();
            log.info("Evento LoanApplicationReceivedEvent publicado no Kafka. Topic: {}, Partition: {}, Offset: {}, AppID: {}",
                    sendResult.getRecordMetadata().topic(),
                    sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset(),
                    event.applicationId());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Falha final ao publicar evento Kafka para AppID {}: {}", event.applicationId(), e.getMessage());
            // Aqui você precisaria de uma lógica de compensação, por exemplo,
            // marcar a solicitação no DB como "FALHA_PUBLICACAO_EVENTO" ou tentar reenviar.
            // Por simplicidade, vamos apenas relançar a exceção.
            Thread.currentThread().interrupt();
            throw new RuntimeException("Erro crítico ao processar a solicitação e publicar evento.", e);
        }

        return savedApplication.getId();
    }

    public LoanApplication updateLoanStatus(UpdateLoanStatusRequest updateLoan) throws ApplicationNotFoundException {
        log.info("Atualizando status da solicitação {} para {}", updateLoan.applicationId(), updateLoan.status());
        var application = loanApplicationRepository.findById(updateLoan.applicationId())
                .orElseThrow(() -> new ApplicationNotFoundException("Solicitação não encontrada com ID: " + updateLoan.applicationId()));

        application.setStatus(updateLoan.status());
        if (LoanStatus.REJECTED.equals(updateLoan.status())) {
            application.setRejectionReason(updateLoan.reason());
        }
        if (LoanStatus.APPROVED.equals(updateLoan.status())) {
            application.setAmountApproved(updateLoan.amountApproved());
            application.setInterestRate(updateLoan.interestRate());
            application.setApprovedInstallments(updateLoan.installments());
            application.setInstallmentValue(updateLoan.installmentValue());
        }

        var loanSaved = loanApplicationRepository.save(application);
        log.info("Status da solicitação {} atualizado para {}", updateLoan.applicationId(), updateLoan.status());
        return loanSaved;
    }
}
