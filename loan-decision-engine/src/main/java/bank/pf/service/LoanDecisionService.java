package bank.pf.service;

import bank.pf.client.LoanApplicationClient;
import bank.pf.dto.DecisionResult;
import bank.pf.dto.LoanTerms;
import bank.pf.dto.event.CreditAssessmentCompletedEvent;
import bank.pf.dto.event.LoanDecisionMadeEvent;
import bank.pf.dto.request.LoanApplicationUpdateStatusRequest;
import bank.pf.enums.AssessmentStatus;
import bank.pf.enums.LoanDecision;
import bank.pf.messaging.producer.LoanDecisionEventProducer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanDecisionService {

    private final LoanDecisionEventProducer loanDecisionEventProducer;
    private final LoanApplicationClient loanApplicationClient;
    private final MeterRegistry meterRegistry;

    private Counter approvedLoansCounter;
    private Counter rejectedLoansCounter;
    private Counter manualReviewLoansCounter;
    private Counter apiUpdateFailureCounter;
    private Timer apiUpdateTimer;

    private static final int DEFAULT_NUMBER_OF_INSTALLMENTS_SHORT_TERM = 12;
    private static final int DEFAULT_NUMBER_OF_INSTALLMENTS_MEDIUM_TERM = 24;
    private static final int DEFAULT_NUMBER_OF_INSTALLMENTS_LONG_TERM = 36;
    private static final BigDecimal MEDIUM_TERM_THRESHOLD_AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal LONG_TERM_THRESHOLD_AMOUNT = new BigDecimal("25000.00");


    @PostConstruct
    private void initCounters() {
        this.approvedLoansCounter = Counter.builder("loan.decisions")
                .tag("status", "approved")
                .description("Number of approved loans")
                .register(meterRegistry);

        this.rejectedLoansCounter = Counter.builder("loan.decisions")
                .tag("status", "rejected")
                .description("Number of rejected loans")
                .register(meterRegistry);

        this.manualReviewLoansCounter = Counter.builder("loan.decisions")
                .tag("status", "manual_review")
                .description("Number of loans sent to manual review")
                .register(meterRegistry);

        this.apiUpdateFailureCounter = Counter.builder("loan.api.update.failures")
                .description("Number of failures when updating loan status via API after retries")
                .register(meterRegistry);

        this.apiUpdateTimer = Timer.builder("loan.api.update.duration")
                .description("Duration of the loan status API update call")
                .publishPercentiles(0.5, 0.95, 0.99)
                .sla(Duration.ofSeconds(1))
                .register(meterRegistry);
    }


    public void processDecision(CreditAssessmentCompletedEvent creditAssessmentCompletedEvent) {
        log.info("Processing decision for application ID: {}", creditAssessmentCompletedEvent.applicationId());
        Timer.Sample decisionTimerSample = Timer.start(meterRegistry);

        // Step 1: Determine loan decision and terms
        DecisionResult decisionResult = determineLoanDecision(creditAssessmentCompletedEvent);
        LoanDecision finalDecision = decisionResult.decision();
        LoanTerms finalTerms = decisionResult.terms();
        String reason = decisionResult.reason();

        // Step 2: Create and send decision event
        LoanDecisionMadeEvent decisionEvent = createDecisionEvent(creditAssessmentCompletedEvent, finalDecision, reason, finalTerms);
        loanDecisionEventProducer.sendLoanDecisionMadeEvent(decisionEvent);

        // Step 3: Create update request
        LoanApplicationUpdateStatusRequest updateRequest = createUpdateRequest(
                creditAssessmentCompletedEvent.applicationId(), finalDecision, reason, finalTerms);

        // Step 4: Update loan application status via API
        updateLoanApplicationStatus(creditAssessmentCompletedEvent.applicationId(), updateRequest);

        // Record metrics
        decisionTimerSample.stop(meterRegistry.timer("loan.decision.processing.duration", "decision", finalDecision.name()));
    }


    private DecisionResult determineLoanDecision(CreditAssessmentCompletedEvent completedEvent) {
        AssessmentStatus upstreamStatus = completedEvent.finalAssessmentStatus();
        String initialReason = completedEvent.justification();

        if (upstreamStatus == AssessmentStatus.REJECTED) {
            return handleRejectedApplication(completedEvent, initialReason);
        } else if (upstreamStatus == AssessmentStatus.PENDING_MANUAL_REVIEW) {
            return handlePendingManualReviewApplication(completedEvent, initialReason);
        } else if (upstreamStatus == AssessmentStatus.APPROVED || upstreamStatus == AssessmentStatus.ADJUSTED_CONDITIONS) {
            return handleApprovedApplication(completedEvent, initialReason);
        } else {
            return handleUnknownStatusApplication(completedEvent, upstreamStatus);
        }
    }

    private DecisionResult handleRejectedApplication(CreditAssessmentCompletedEvent completedEvent, String reason) {
        rejectedLoansCounter.increment();
        log.info("Application {} REJECTED based on credit assessment: {}", completedEvent.applicationId(), reason);
        return new DecisionResult(LoanDecision.REJECTED, null, reason);
    }

    private DecisionResult handlePendingManualReviewApplication(CreditAssessmentCompletedEvent completedEvent, String reason) {
        String updatedReason = (reason == null ? "" : reason) + " Flagged for manual review by credit assessment.";
        manualReviewLoansCounter.increment();
        log.info("Application {} PENDING_MANUAL_REVIEW based on credit assessment: {}", completedEvent.applicationId(), updatedReason);
        return new DecisionResult(LoanDecision.PENDING_MANUAL_REVIEW, null, updatedReason);
    }

    private DecisionResult handleApprovedApplication(CreditAssessmentCompletedEvent completedEvent, String reason) {
        if (hasInvalidTerms(completedEvent)) {
            return handleInvalidApprovedTerms(completedEvent);
        } else {
            return handleValidApprovedTerms(completedEvent, reason);
        }
    }

    private boolean hasInvalidTerms(CreditAssessmentCompletedEvent completedEvent) {
        return completedEvent.approvedLimit() == null ||
                completedEvent.approvedLimit().compareTo(BigDecimal.ZERO) <= 0 ||
                completedEvent.interestRateApplied() == null ||
                completedEvent.interestRateApplied().compareTo(BigDecimal.ZERO) < 0;
    }

    private DecisionResult handleInvalidApprovedTerms(CreditAssessmentCompletedEvent completedEvent) {
        String reason = "Approved by credit assessment but terms are invalid/missing. Needs manual review.";
        manualReviewLoansCounter.increment();
        log.warn("Application {} sent to manual review: Approved by credit assessment but terms are invalid. Limit: {}, Rate: {}",
                completedEvent.applicationId(), completedEvent.approvedLimit(), completedEvent.interestRateApplied());
        return new DecisionResult(LoanDecision.PENDING_MANUAL_REVIEW, null, reason);
    }

    private DecisionResult handleValidApprovedTerms(CreditAssessmentCompletedEvent completedEvent, String reason) {
        LoanTerms finalTerms = calculateDefaultLoanTerms(completedEvent.approvedLimit(), completedEvent.interestRateApplied());
        String updatedReason = (reason == null ? "Approved" : reason + " Approved with standard terms.");
        approvedLoansCounter.increment();
        log.info("Application {} APPROVED. Terms: Amount={}, Rate={}, Installments={}, InstallmentAmount={}",
                completedEvent.applicationId(), finalTerms.getApprovedAmount(), finalTerms.getInterestRate(),
                finalTerms.getNumberOfInstallments(), finalTerms.getInstallmentAmount());
        return new DecisionResult(LoanDecision.APPROVED, finalTerms, updatedReason);
    }

    private DecisionResult handleUnknownStatusApplication(CreditAssessmentCompletedEvent completedEvent, AssessmentStatus assessmentStatus) {
        String reason = "Unknown or unexpected status from credit assessment: " + assessmentStatus + ". Sent for manual review.";
        manualReviewLoansCounter.increment();
        log.warn("Application {} PENDING_MANUAL_REVIEW due to unexpected status: {}", completedEvent.applicationId(), assessmentStatus);
        return new DecisionResult(LoanDecision.PENDING_MANUAL_REVIEW, null, reason);
    }

    private LoanDecisionMadeEvent createDecisionEvent(CreditAssessmentCompletedEvent completedEvent, LoanDecision loanDecision,
                                                      String reason, LoanTerms loanTerms) {
        return LoanDecisionMadeEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventTimestamp(LocalDateTime.now())
                .applicationId(completedEvent.applicationId())
                .cpf(completedEvent.cpf())
                .decision(loanDecision)
                .reason(reason)
                .terms(loanTerms)
                .build();
    }

    private LoanApplicationUpdateStatusRequest createUpdateRequest(String applicationId, LoanDecision loanDecision,
                                                                   String reason, LoanTerms loanTerms) {
        String decisionDetailsForUpdate = reason;
        if (loanDecision == LoanDecision.APPROVED && loanTerms != null) {
            decisionDetailsForUpdate = String.format("Approved. Amount: %.2f, Rate: %.4f, Installments: %d. %s",
                    loanTerms.getApprovedAmount(), loanTerms.getInterestRate(), loanTerms.getNumberOfInstallments(), reason);
        }

        return LoanApplicationUpdateStatusRequest.builder()
                .applicationId(applicationId)
                .status(loanDecision)
                .reason(decisionDetailsForUpdate)
                .amountApproved(loanTerms != null ? loanTerms.getApprovedAmount() : null)
                .interestRate(loanTerms != null ? loanTerms.getInterestRate() : null)
                .build();
    }

    private void updateLoanApplicationStatus(String applicationId, LoanApplicationUpdateStatusRequest updateStatusRequest) {
        try {
            long startTime = System.nanoTime();
            callUpdateLoanApplicationStatusApi(applicationId, updateStatusRequest);
            apiUpdateTimer.record(System.nanoTime() - startTime, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            apiUpdateFailureCounter.increment();
            log.error("CRITICAL: All retries failed to update status via API for applicationId {}. Event published, but API update failed. Reason: {}. Manual intervention may be required.",
                    applicationId, e.getMessage());
            // A reconciliação é a próxima etapa para esse tipo de falha.
        }
    }

    private LoanTerms calculateDefaultLoanTerms(BigDecimal approvedAmount, BigDecimal annualInterestRate) {
        int numberOfInstallments = determineNumberOfInstallments(approvedAmount);
        BigDecimal installmentAmount;

        if (annualInterestRate.compareTo(BigDecimal.ZERO) == 0) {
            installmentAmount = calculateZeroInterestInstallmentAmount(approvedAmount, numberOfInstallments);
        } else {
            installmentAmount = calculateInterestBasedInstallmentAmount(approvedAmount, annualInterestRate, numberOfInstallments);
        }

        return createLoanTerms(approvedAmount, annualInterestRate, numberOfInstallments, installmentAmount);
    }

    private int determineNumberOfInstallments(BigDecimal approvedAmount) {
        if (approvedAmount.compareTo(LONG_TERM_THRESHOLD_AMOUNT) >= 0) {
            return DEFAULT_NUMBER_OF_INSTALLMENTS_LONG_TERM;
        } else if (approvedAmount.compareTo(MEDIUM_TERM_THRESHOLD_AMOUNT) >= 0) {
            return DEFAULT_NUMBER_OF_INSTALLMENTS_MEDIUM_TERM;
        } else {
            return DEFAULT_NUMBER_OF_INSTALLMENTS_SHORT_TERM;
        }
    }

    private BigDecimal calculateZeroInterestInstallmentAmount(BigDecimal approvedAmount, int numberOfInstallments) {
        return approvedAmount.divide(new BigDecimal(numberOfInstallments), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateInterestBasedInstallmentAmount(BigDecimal approvedAmount, BigDecimal annualInterestRate, int numberOfInstallments) {
        BigDecimal monthlyInterestRate = convertToMonthlyRate(annualInterestRate);
        BigDecimal onePlusIMonthly = BigDecimal.ONE.add(monthlyInterestRate);
        BigDecimal onePlusIMonthlyToN = onePlusIMonthly.pow(numberOfInstallments);

        BigDecimal numerator = monthlyInterestRate.multiply(onePlusIMonthlyToN);
        BigDecimal denominator = onePlusIMonthlyToN.subtract(BigDecimal.ONE);

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Denominator in installment calculation is zero for amount {}, rate {}, installments {}. Defaulting installment amount.",
                    approvedAmount, annualInterestRate, numberOfInstallments);

            return calculateZeroInterestInstallmentAmount(approvedAmount, numberOfInstallments);
        }

        return approvedAmount.multiply(numerator).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal convertToMonthlyRate(BigDecimal annualInterestRate) {
        return annualInterestRate.divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
    }

    private LoanTerms createLoanTerms(BigDecimal approvedAmount, BigDecimal interestRate, int numberOfInstallments, BigDecimal installmentAmount) {
        return LoanTerms.builder()
                .approvedAmount(approvedAmount)
                .interestRate(interestRate)
                .numberOfInstallments(numberOfInstallments)
                .installmentAmount(installmentAmount)
                .build();
    }

    @Retryable(backoff = @Backoff(delay = 1000, multiplier = 2))
    public void callUpdateLoanApplicationStatusApi(String applicationId, LoanApplicationUpdateStatusRequest updateStatusRequest) {
        log.info("Attempting to update status for application ID {} to {} via API. Current attempt: {}",
                applicationId, updateStatusRequest.status(), RetrySynchronizationManager.getContext().getRetryCount() + 1);
        loanApplicationClient.updateLoanApplicationStatus(applicationId, updateStatusRequest);
    }
}
