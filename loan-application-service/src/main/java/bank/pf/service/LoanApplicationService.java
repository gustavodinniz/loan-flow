package bank.pf.service;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.dto.request.LoanApplicationRequest;
import bank.pf.dto.request.UpdateLoanStatusRequest;
import bank.pf.dto.response.AccountValidationResponse;
import bank.pf.dto.response.CpfValidationResponse;
import bank.pf.dto.response.InternalRestrictResponse;
import bank.pf.entity.LoanApplication;
import bank.pf.enums.LoanStatus;
import bank.pf.exception.ApplicationNotFoundException;
import bank.pf.exception.EventPublishingFailedException;
import bank.pf.exception.ValidationException;
import bank.pf.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
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

    public String submitApplication(LoanApplicationRequest request) throws ValidationException, ExecutionException, InterruptedException {
        log.info("Thread (submitApplication start for CPF {}): {}", request.cpf(), Thread.currentThread());
        List<String> validationErrors = new ArrayList<>();

        validateAge(request, validationErrors);
        validateMinimumIncome(request, validationErrors);
        externalValidations(request, validationErrors);

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

        var savedLoan = saveLoanRequest(request);
        log.info("Solicitação de empréstimo persistida com ID: {}", savedLoan.getId());

        publishLoanApplicationReceivedEvent(savedLoan);
        return savedLoan.getId();
    }

    private void publishLoanApplicationReceivedEvent(LoanApplication savedLoan) {
        var event = LoanApplicationReceivedEvent.valueOf(savedLoan);
        CompletableFuture<SendResult<String, LoanApplicationReceivedEvent>> kafkaPublishFuture =
                CompletableFuture.supplyAsync(() -> {
                    log.info("Thread (Kafka publish for AppID {}): {}", event.applicationId(), Thread.currentThread());
                    try {
                        return kafkaTemplate.send(loanApplicationReceivedTopic, event.applicationId(), event).get();
                    } catch (InterruptedException | ExecutionException e) {
                        log.error("Erro ao publicar evento Kafka para AppID {}: {}", event.applicationId(), e.getMessage());
                        Thread.currentThread().interrupt();
                        throw new EventPublishingFailedException("Falha ao publicar evento Kafka", e);
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
            savedLoan.setStatus(LoanStatus.EVENT_PUBLISHING_FAILED);
            loanApplicationRepository.save(savedLoan);
            Thread.currentThread().interrupt();
            throw new EventPublishingFailedException("Erro crítico ao processar a solicitação e publicar evento.", e);
        }
    }


    private LoanApplication saveLoanRequest(LoanApplicationRequest request) throws ExecutionException, InterruptedException {
        var loanApplication = LoanApplication.valueOf(request);
        loanApplication.setStatus(LoanStatus.PENDING_ASSESSMENT);


        CompletableFuture<LoanApplication> dbPersistFuture = CompletableFuture.supplyAsync(() -> {
            log.info("Thread (DB persist for CPF {}): {}", loanApplication.getCpf(), Thread.currentThread());
            return loanApplicationRepository.save(loanApplication);
        }, virtualThreadExecutor);
        return dbPersistFuture.get();
    }


    private void externalValidations(LoanApplicationRequest request, List<String> validationErrors) throws InterruptedException, ExecutionException {
        CompletableFuture<CpfValidationResponse> cpfValidationFuture = externalValidationService.validateCpfStatus(request.cpf());
        CompletableFuture<AccountValidationResponse> accountValidationFuture = externalValidationService.checkAccountActive(request.cpf());
        CompletableFuture<InternalRestrictResponse> restrictionFuture = externalValidationService.checkInternalRestrictions(request.cpf());

        CompletableFuture.allOf(cpfValidationFuture, accountValidationFuture, restrictionFuture).join();

        CpfValidationResponse cpfResult = cpfValidationFuture.get();
        if (!cpfResult.isRegular()) {
            validationErrors.add(cpfResult.message());
        }

        AccountValidationResponse accountResult = accountValidationFuture.get();
        if (!accountResult.isActive()) {
            validationErrors.add(accountResult.message());
        }

        InternalRestrictResponse restrictionResult = restrictionFuture.get();
        if (restrictionResult.hasRestriction()) {
            validationErrors.add(restrictionResult.message());
        }
    }

    private void validateMinimumIncome(LoanApplicationRequest request, List<String> validationErrors) {
        if (request.monthlyIncome().compareTo(new BigDecimal("1200.00")) < 0) {
            validationErrors.add("Renda mensal mínima de R$ 1.200,00 não atingida.");
        }
    }

    private void validateAge(LoanApplicationRequest request, List<String> validationErrors) {
        int age = Period.between(request.dateOfBirth(), LocalDate.now()).getYears();
        if (age < 18 || age > 75) {
            validationErrors.add("Idade do solicitante deve ser entre 18 e 75 anos. Idade calculada: " + age);
        }
    }

    public void updateLoanStatus(UpdateLoanStatusRequest updateLoan) throws ApplicationNotFoundException {
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

        loanApplicationRepository.save(application);
        log.info("Status da solicitação {} atualizado para {}", updateLoan.applicationId(), updateLoan.status());
    }
}
