package bank.pf.service;

import bank.pf.client.LoanApplicationClient;
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

        LoanDecision finalDecision;
        LoanTerms finalTerms = null;
        String reason = creditAssessmentCompletedEvent.justification();
        AssessmentStatus upstreamStatus = creditAssessmentCompletedEvent.finalAssessmentStatus();

        if (upstreamStatus == AssessmentStatus.REJECTED) {
            finalDecision = LoanDecision.REJECTED;
            rejectedLoansCounter.increment();
            log.info("Application {} REJECTED based on credit assessment: {}", creditAssessmentCompletedEvent.applicationId(), reason);
        } else if (upstreamStatus == AssessmentStatus.PENDING_MANUAL_REVIEW) {
            finalDecision = LoanDecision.PENDING_MANUAL_REVIEW;
            reason = (reason == null ? "" : reason) + " Flagged for manual review by credit assessment.";
            manualReviewLoansCounter.increment();
            log.info("Application {} PENDING_MANUAL_REVIEW based on credit assessment: {}", creditAssessmentCompletedEvent.applicationId(), reason);
        } else if (upstreamStatus == AssessmentStatus.APPROVED || upstreamStatus == AssessmentStatus.ADJUSTED_CONDITIONS) {
            if (creditAssessmentCompletedEvent.approvedLimit() == null || creditAssessmentCompletedEvent.approvedLimit().compareTo(BigDecimal.ZERO) <= 0 ||
                    creditAssessmentCompletedEvent.interestRateApplied() == null || creditAssessmentCompletedEvent.interestRateApplied().compareTo(BigDecimal.ZERO) < 0) {
                finalDecision = LoanDecision.PENDING_MANUAL_REVIEW;
                reason = "Approved by credit assessment but terms are invalid/missing. Needs manual review.";
                manualReviewLoansCounter.increment();
                log.warn("Application {} sent to manual review: Approved by credit assessment but terms are invalid. Limit: {}, Rate: {}",
                        creditAssessmentCompletedEvent.applicationId(), creditAssessmentCompletedEvent.approvedLimit(), creditAssessmentCompletedEvent.interestRateApplied());
            } else {
                finalDecision = LoanDecision.APPROVED;
                finalTerms = calculateDefaultLoanTerms(creditAssessmentCompletedEvent.approvedLimit(), creditAssessmentCompletedEvent.interestRateApplied());
                reason = (reason == null ? "Approved" : reason + " Approved with standard terms.");
                approvedLoansCounter.increment();
                log.info("Application {} APPROVED. Terms: Amount={}, Rate={}, Installments={}, InstallmentAmount={}",
                        creditAssessmentCompletedEvent.applicationId(), finalTerms.getApprovedAmount(), finalTerms.getInterestRate(),
                        finalTerms.getNumberOfInstallments(), finalTerms.getInstallmentAmount());
            }
        } else {
            finalDecision = LoanDecision.PENDING_MANUAL_REVIEW;
            reason = "Unknown or unexpected status from credit assessment: " + upstreamStatus + ". Sent for manual review.";
            manualReviewLoansCounter.increment();
            log.warn("Application {} PENDING_MANUAL_REVIEW due to unexpected status: {}", creditAssessmentCompletedEvent.applicationId(), upstreamStatus);
        }

        LoanDecisionMadeEvent decisionEvent = LoanDecisionMadeEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventTimestamp(LocalDateTime.now())
                .applicationId(creditAssessmentCompletedEvent.applicationId())
                .cpf(creditAssessmentCompletedEvent.cpf())
                .decision(finalDecision)
                .reason(reason)
                .terms(finalTerms)
                .build();
        loanDecisionEventProducer.sendLoanDecisionMadeEvent(decisionEvent);

        String statusToUpdate = finalDecision.name();
        String decisionDetailsForUpdate = reason;
        if (finalDecision == LoanDecision.APPROVED && finalTerms != null) {
            decisionDetailsForUpdate = String.format("Approved. Amount: %.2f, Rate: %.4f, Installments: %d. %s",
                    finalTerms.getApprovedAmount(), finalTerms.getInterestRate(), finalTerms.getNumberOfInstallments(), reason);
        }

        LoanApplicationUpdateStatusRequest updateRequest = LoanApplicationUpdateStatusRequest.builder()
                .applicationId(creditAssessmentCompletedEvent.applicationId())
                .status(LoanDecision.valueOf(statusToUpdate))
                .reason(decisionDetailsForUpdate)
                .amountApproved(finalTerms != null ? finalTerms.getApprovedAmount() : null)
                .interestRate(finalTerms != null ? finalTerms.getInterestRate() : null)
                .build();


        try {
            long startTime = System.nanoTime();
            callUpdateLoanApplicationStatusApi(creditAssessmentCompletedEvent.applicationId(), updateRequest);
            apiUpdateTimer.record(System.nanoTime() - startTime, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            apiUpdateFailureCounter.increment();
            log.error("CRITICAL: All retries failed to update status via API for applicationId {}. Event published, but API update failed. Reason: {}. Manual intervention may be required.",
                    creditAssessmentCompletedEvent.applicationId(), e.getMessage());
            // A reconciliação é a próxima etapa para esse tipo de falha.
        }
        decisionTimerSample.stop(meterRegistry.timer("loan.decision.processing.duration", "decision", finalDecision.name()));
    }

    private LoanTerms calculateDefaultLoanTerms(BigDecimal approvedAmount, BigDecimal annualInterestRate) {
        int numberOfInstallments;

        if (approvedAmount.compareTo(LONG_TERM_THRESHOLD_AMOUNT) >= 0) {
            numberOfInstallments = DEFAULT_NUMBER_OF_INSTALLMENTS_LONG_TERM;
        } else if (approvedAmount.compareTo(MEDIUM_TERM_THRESHOLD_AMOUNT) >= 0) {
            numberOfInstallments = DEFAULT_NUMBER_OF_INSTALLMENTS_MEDIUM_TERM;
        } else {
            numberOfInstallments = DEFAULT_NUMBER_OF_INSTALLMENTS_SHORT_TERM;
        }

        if (annualInterestRate.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal installmentAmount = approvedAmount.divide(new BigDecimal(numberOfInstallments), 2, RoundingMode.HALF_UP);
            return LoanTerms.builder()
                    .approvedAmount(approvedAmount)
                    .interestRate(annualInterestRate)
                    .numberOfInstallments(numberOfInstallments)
                    .installmentAmount(installmentAmount)
                    .build();
        }

        BigDecimal monthlyInterestRate = annualInterestRate.divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
        BigDecimal onePlusIMonthly = BigDecimal.ONE.add(monthlyInterestRate);
        BigDecimal onePlusIMonthlyToN = onePlusIMonthly.pow(numberOfInstallments);

        BigDecimal numerator = monthlyInterestRate.multiply(onePlusIMonthlyToN);
        BigDecimal denominator = onePlusIMonthlyToN.subtract(BigDecimal.ONE);

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Denominator in installment calculation is zero for amount {}, rate {}, installments {}. Defaulting installment amount.",
                    approvedAmount, annualInterestRate, numberOfInstallments);

            BigDecimal installmentAmount = approvedAmount.divide(new BigDecimal(numberOfInstallments), 2, RoundingMode.HALF_UP);
            return LoanTerms.builder()
                    .approvedAmount(approvedAmount)
                    .interestRate(annualInterestRate)
                    .numberOfInstallments(numberOfInstallments)
                    .installmentAmount(installmentAmount)
                    .build();
        }

        BigDecimal installmentAmount = approvedAmount.multiply(numerator).divide(denominator, 2, RoundingMode.HALF_UP);

        return LoanTerms.builder()
                .approvedAmount(approvedAmount)
                .interestRate(annualInterestRate)
                .numberOfInstallments(numberOfInstallments)
                .installmentAmount(installmentAmount)
                .build();
    }

    @Retryable(backoff = @Backoff(delay = 1000, multiplier = 2))
    public void callUpdateLoanApplicationStatusApi(String applicationId, LoanApplicationUpdateStatusRequest request) {
        log.info("Attempting to update status for application ID {} to {} via API. Current attempt: {}",
                applicationId, request.status(), RetrySynchronizationManager.getContext().getRetryCount() + 1);
        loanApplicationClient.updateLoanApplicationStatus(applicationId, request);
    }
}
