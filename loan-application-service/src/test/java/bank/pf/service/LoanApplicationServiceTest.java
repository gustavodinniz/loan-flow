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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceTest {

    @InjectMocks
    private LoanApplicationService loanApplicationService;

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Mock
    private KafkaTemplate<String, LoanApplicationReceivedEvent> kafkaTemplate;

    @Mock
    private ExternalValidationService externalValidationService;

    private LoanApplicationRequest loanApplicationRequest;
    private LoanApplication loanApplication;
    private UpdateLoanStatusRequest updateLoanStatusRequest;
    private CompletableFuture<CpfValidationResponse> cpfValidationFuture;
    private CompletableFuture<AccountValidationResponse> accountValidationFuture;
    private CompletableFuture<InternalRestrictResponse> internalRestrictFuture;
    private CompletableFuture<SendResult<String, LoanApplicationReceivedEvent>> sendResultFuture;

    @Test
    void shouldSubmitApplicationWithSuccess() throws ValidationException, ExecutionException, InterruptedException {
        // Given
        givenLoanApplicationRequest();
        givenCpfValidationFutureReturnsValid();
        givenAccountValidationFutureReturnsActive();
        givenInternalRestrictFutureReturnsNoRestriction();
        givenLoanApplicationRepositorySave();
        givenKafkaTemplateSendReturnsSuccess();
        givenLoanApplicationReceivedTopic();

        // When
        String applicationId = loanApplicationService.submitApplication(loanApplicationRequest);

        // Then
        assertThat(applicationId).isEqualTo(loanApplication.getId());
        verify(externalValidationService).validateCpfStatus(loanApplicationRequest.cpf());
        verify(externalValidationService).checkAccountActive(loanApplicationRequest.cpf());
        verify(externalValidationService).checkInternalRestrictions(loanApplicationRequest.cpf());
        verify(loanApplicationRepository).save(any(LoanApplication.class));
        verify(kafkaTemplate).send(any(), any(), any(LoanApplicationReceivedEvent.class));
    }

    @Test
    void shouldNotSubmitApplicationWhenAgeIsLessThan18() throws ExecutionException, InterruptedException {
        // Given
        givenLoanApplicationRequestWithAgeLessThan18();
        givenCpfValidationFutureReturnsValid();
        givenAccountValidationFutureReturnsActive();
        givenInternalRestrictFutureReturnsNoRestriction();

        // When/Then
        ValidationException exception = assertThrows(ValidationException.class,
                () -> loanApplicationService.submitApplication(loanApplicationRequest));
        assertThat(exception.getMessage()).contains("Idade do solicitante deve ser entre 18 e 75 anos");
        verify(loanApplicationRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void shouldNotSubmitApplicationWhenAgeIsGreaterThan75() throws ExecutionException, InterruptedException {
        // Given
        givenLoanApplicationRequestWithAgeGreaterThan75();
        givenCpfValidationFutureReturnsValid();
        givenAccountValidationFutureReturnsActive();
        givenInternalRestrictFutureReturnsNoRestriction();

        // When/Then
        ValidationException exception = assertThrows(ValidationException.class,
                () -> loanApplicationService.submitApplication(loanApplicationRequest));
        assertThat(exception.getMessage()).contains("Idade do solicitante deve ser entre 18 e 75 anos");
        verify(loanApplicationRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void shouldNotSubmitApplicationWhenMonthlyIncomeIsLessThanMinimum() throws ExecutionException, InterruptedException {
        // Given
        givenLoanApplicationRequestWithLowIncome();
        givenCpfValidationFutureReturnsValid();
        givenAccountValidationFutureReturnsActive();
        givenInternalRestrictFutureReturnsNoRestriction();

        // When/Then
        ValidationException exception = assertThrows(ValidationException.class,
                () -> loanApplicationService.submitApplication(loanApplicationRequest));
        assertThat(exception.getMessage()).contains("Renda mensal mínima de R$ 1.200,00 não atingida");
        verify(loanApplicationRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void shouldNotSubmitApplicationWhenCpfIsNotRegular() throws ExecutionException, InterruptedException {
        // Given
        givenLoanApplicationRequest();
        givenCpfValidationFutureReturnsNotRegular();
        givenAccountValidationFutureReturnsActive();
        givenInternalRestrictFutureReturnsNoRestriction();

        // When/Then
        assertThrows(ValidationException.class, () -> loanApplicationService.submitApplication(loanApplicationRequest));
        verify(externalValidationService).validateCpfStatus(loanApplicationRequest.cpf());
        verify(externalValidationService).checkAccountActive(loanApplicationRequest.cpf());
        verify(externalValidationService).checkInternalRestrictions(loanApplicationRequest.cpf());
        verify(loanApplicationRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void shouldNotSubmitApplicationWhenAccountIsNotActive() throws ExecutionException, InterruptedException {
        // Given
        givenLoanApplicationRequest();
        givenCpfValidationFutureReturnsValid();
        givenAccountValidationFutureReturnsInactive();
        givenInternalRestrictFutureReturnsNoRestriction();

        // When/Then
        assertThrows(ValidationException.class, () -> loanApplicationService.submitApplication(loanApplicationRequest));
        verify(externalValidationService).validateCpfStatus(loanApplicationRequest.cpf());
        verify(externalValidationService).checkAccountActive(loanApplicationRequest.cpf());
        verify(externalValidationService).checkInternalRestrictions(loanApplicationRequest.cpf());
        verify(loanApplicationRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void shouldNotSubmitApplicationWhenHasInternalRestriction() throws ExecutionException, InterruptedException {
        // Given
        givenLoanApplicationRequest();
        givenCpfValidationFutureReturnsValid();
        givenAccountValidationFutureReturnsActive();
        givenInternalRestrictFutureReturnsRestriction();

        // When/Then
        assertThrows(ValidationException.class, () -> loanApplicationService.submitApplication(loanApplicationRequest));
        verify(externalValidationService).validateCpfStatus(loanApplicationRequest.cpf());
        verify(externalValidationService).checkAccountActive(loanApplicationRequest.cpf());
        verify(externalValidationService).checkInternalRestrictions(loanApplicationRequest.cpf());
        verify(loanApplicationRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void shouldNotSubmitApplicationWhenKafkaPublishingFails() throws ExecutionException, InterruptedException {
        // Given
        givenLoanApplicationRequest();
        givenCpfValidationFutureReturnsValid();
        givenAccountValidationFutureReturnsActive();
        givenInternalRestrictFutureReturnsNoRestriction();
        givenLoanApplicationRepositorySave();
        givenKafkaTemplateSendThrowsException();
        givenLoanApplicationReceivedTopic();

        // When/Then
        assertThrows(EventPublishingFailedException.class, () -> loanApplicationService.submitApplication(loanApplicationRequest));
        verify(externalValidationService).validateCpfStatus(loanApplicationRequest.cpf());
        verify(externalValidationService).checkAccountActive(loanApplicationRequest.cpf());
        verify(externalValidationService).checkInternalRestrictions(loanApplicationRequest.cpf());
        verify(loanApplicationRepository, times(2)).save(any(LoanApplication.class));
        verify(kafkaTemplate).send(any(), any(), any(LoanApplicationReceivedEvent.class));
        verify(loanApplicationRepository).save(argThat(app -> app.getStatus() == LoanStatus.EVENT_PUBLISHING_FAILED));
    }

    @Test
    void shouldUpdateLoanStatusWithSuccess() throws ApplicationNotFoundException {
        // Given
        givenUpdateLoanStatusRequest();
        givenLoanApplicationRepositoryFindByIdReturnsLoanApplication();

        // When
        loanApplicationService.updateLoanStatus(updateLoanStatusRequest);

        // Then
        verify(loanApplicationRepository).findById(updateLoanStatusRequest.applicationId());
        verify(loanApplicationRepository).save(argThat(app ->
                app.getStatus() == updateLoanStatusRequest.status() &&
                        app.getRejectionReason() == null
        ));
    }

    @Test
    void shouldUpdateLoanStatusToRejectedWithSuccess() throws ApplicationNotFoundException {
        // Given
        givenUpdateLoanStatusRequestWithRejectedStatus();
        givenLoanApplicationRepositoryFindByIdReturnsLoanApplication();

        // When
        loanApplicationService.updateLoanStatus(updateLoanStatusRequest);

        // Then
        verify(loanApplicationRepository).findById(updateLoanStatusRequest.applicationId());
        verify(loanApplicationRepository).save(argThat(app ->
                app.getStatus() == LoanStatus.REJECTED &&
                        app.getRejectionReason().equals(updateLoanStatusRequest.reason())
        ));
    }

    @Test
    void shouldUpdateLoanStatusToApprovedWithSuccess() throws ApplicationNotFoundException {
        // Given
        givenUpdateLoanStatusRequestWithApprovedStatus();
        givenLoanApplicationRepositoryFindByIdReturnsLoanApplication();

        // When
        loanApplicationService.updateLoanStatus(updateLoanStatusRequest);

        // Then
        verify(loanApplicationRepository).findById(updateLoanStatusRequest.applicationId());
        verify(loanApplicationRepository).save(argThat(app ->
                app.getStatus() == LoanStatus.APPROVED &&
                        app.getAmountApproved().equals(updateLoanStatusRequest.amountApproved()) &&
                        app.getInterestRate().equals(updateLoanStatusRequest.interestRate()) &&
                        app.getApprovedInstallments().equals(updateLoanStatusRequest.installments()) &&
                        app.getInstallmentValue().equals(updateLoanStatusRequest.installmentValue())
        ));
    }

    @Test
    void shouldThrowApplicationNotFoundExceptionWhenLoanApplicationNotFound() {
        // Given
        givenUpdateLoanStatusRequest();
        givenLoanApplicationRepositoryFindByIdReturnsEmpty();

        // When/Then
        assertThrows(ApplicationNotFoundException.class, () -> loanApplicationService.updateLoanStatus(updateLoanStatusRequest));
        verify(loanApplicationRepository).findById(updateLoanStatusRequest.applicationId());
        verify(loanApplicationRepository, never()).save(any());
    }

    // Given methods
    private void givenLoanApplicationRequest() {
        loanApplicationRequest = new LoanApplicationRequest(
                "12345678901",
                "test@example.com",
                LocalDate.now().minusYears(30),
                new BigDecimal("5000.00"),
                12,
                new BigDecimal("3000.00")
        );
    }

    private void givenLoanApplicationRequestWithAgeLessThan18() {
        loanApplicationRequest = new LoanApplicationRequest(
                "12345678901",
                "test@example.com",
                LocalDate.now().minusYears(17),
                new BigDecimal("5000.00"),
                12,
                new BigDecimal("3000.00")
        );
    }

    private void givenLoanApplicationRequestWithAgeGreaterThan75() {
        loanApplicationRequest = new LoanApplicationRequest(
                "12345678901",
                "test@example.com",
                LocalDate.now().minusYears(76),
                new BigDecimal("5000.00"),
                12,
                new BigDecimal("3000.00")
        );
    }

    private void givenLoanApplicationRequestWithLowIncome() {
        loanApplicationRequest = new LoanApplicationRequest(
                "12345678901",
                "test@example.com",
                LocalDate.now().minusYears(30),
                new BigDecimal("5000.00"),
                12,
                new BigDecimal("1100.00")
        );
    }

    private void givenCpfValidationFutureReturnsValid() {
        cpfValidationFuture = CompletableFuture.completedFuture(new CpfValidationResponse(true, true, "CPF válido e regular"));
        when(externalValidationService.validateCpfStatus(loanApplicationRequest.cpf())).thenReturn(cpfValidationFuture);
    }

    private void givenCpfValidationFutureReturnsNotRegular() {
        cpfValidationFuture = CompletableFuture.completedFuture(new CpfValidationResponse(true, false, "CPF com pendências na Receita Federal"));
        when(externalValidationService.validateCpfStatus(loanApplicationRequest.cpf())).thenReturn(cpfValidationFuture);
    }

    private void givenAccountValidationFutureReturnsActive() {
        accountValidationFuture = CompletableFuture.completedFuture(new AccountValidationResponse(true, "Conta ativa"));
        when(externalValidationService.checkAccountActive(loanApplicationRequest.cpf())).thenReturn(accountValidationFuture);
    }

    private void givenAccountValidationFutureReturnsInactive() {
        accountValidationFuture = CompletableFuture.completedFuture(new AccountValidationResponse(false, "Cliente não possui conta ativa"));
        when(externalValidationService.checkAccountActive(loanApplicationRequest.cpf())).thenReturn(accountValidationFuture);
    }

    private void givenInternalRestrictFutureReturnsNoRestriction() {
        internalRestrictFuture = CompletableFuture.completedFuture(new InternalRestrictResponse(false, "Cliente sem restrições internas"));
        when(externalValidationService.checkInternalRestrictions(loanApplicationRequest.cpf())).thenReturn(internalRestrictFuture);
    }

    private void givenInternalRestrictFutureReturnsRestriction() {
        internalRestrictFuture = CompletableFuture.completedFuture(new InternalRestrictResponse(true, "Cliente possui restrições internas graves"));
        when(externalValidationService.checkInternalRestrictions(loanApplicationRequest.cpf())).thenReturn(internalRestrictFuture);
    }

    private void givenLoanApplicationRepositorySave() {
        loanApplication = LoanApplication.valueOf(loanApplicationRequest);
        loanApplication.setStatus(LoanStatus.PENDING_ASSESSMENT);
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenReturn(loanApplication);
    }

    private void givenKafkaTemplateSendReturnsSuccess() throws ExecutionException, InterruptedException {
        SendResult<String, LoanApplicationReceivedEvent> sendResult = mock(SendResult.class, RETURNS_DEEP_STUBS);
        sendResultFuture = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(), any(), any(LoanApplicationReceivedEvent.class))).thenReturn(sendResultFuture);
    }

    private void givenKafkaTemplateSendThrowsException() {
        CompletableFuture<SendResult<String, LoanApplicationReceivedEvent>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(any(), any(), any(LoanApplicationReceivedEvent.class))).thenReturn(future);
    }

    private void givenLoanApplicationReceivedTopic() {
        ReflectionTestUtils.setField(loanApplicationService, "loanApplicationReceivedTopic", "loan-application-received");
    }

    private void givenUpdateLoanStatusRequest() {
        updateLoanStatusRequest = new UpdateLoanStatusRequest(
                UUID.randomUUID().toString(),
                LoanStatus.PENDING_MANUAL_REVIEW,
                "Reason for manual review",
                new BigDecimal("5000.00"),
                new BigDecimal("0.05"),
                12,
                new BigDecimal("450.00")
        );
    }

    private void givenUpdateLoanStatusRequestWithRejectedStatus() {
        updateLoanStatusRequest = new UpdateLoanStatusRequest(
                UUID.randomUUID().toString(),
                LoanStatus.REJECTED,
                "Renda insuficiente para o valor solicitado",
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                0,
                new BigDecimal("0.00")
        );
    }

    private void givenUpdateLoanStatusRequestWithApprovedStatus() {
        updateLoanStatusRequest = new UpdateLoanStatusRequest(
                UUID.randomUUID().toString(),
                LoanStatus.APPROVED,
                "",
                new BigDecimal("4500.00"),
                new BigDecimal("0.05"),
                12,
                new BigDecimal("400.00")
        );
    }

    private void givenLoanApplicationRepositoryFindByIdReturnsLoanApplication() {
        loanApplication = LoanApplication.builder()
                .id(updateLoanStatusRequest.applicationId())
                .cpf("12345678901")
                .email("test@example.com")
                .dateOfBirth(LocalDate.now().minusYears(30))
                .amountRequested(new BigDecimal("5000.00"))
                .numberOfInstallments(12)
                .monthlyIncome(new BigDecimal("3000.00"))
                .status(LoanStatus.PENDING_ASSESSMENT)
                .createdAt(Instant.now())
                .build();
        when(loanApplicationRepository.findById(updateLoanStatusRequest.applicationId())).thenReturn(Optional.of(loanApplication));
    }

    private void givenLoanApplicationRepositoryFindByIdReturnsEmpty() {
        when(loanApplicationRepository.findById(updateLoanStatusRequest.applicationId())).thenReturn(Optional.empty());
    }
}
