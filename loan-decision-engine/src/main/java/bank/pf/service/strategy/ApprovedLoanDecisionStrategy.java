package bank.pf.service.strategy;

import bank.pf.dto.DecisionResult;
import bank.pf.dto.LoanTerms;
import bank.pf.dto.event.CreditAssessmentCompletedEvent;
import bank.pf.enums.AssessmentStatus;
import bank.pf.enums.LoanDecision;
import bank.pf.service.LoanTermsCalculator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovedLoanDecisionStrategy implements LoanDecisionStrategy {

    private final LoanTermsCalculator loanTermsCalculator;

    @Override
    public boolean canHandle(CreditAssessmentCompletedEvent completedEvent) {
        return completedEvent.finalAssessmentStatus() == AssessmentStatus.APPROVED ||
               completedEvent.finalAssessmentStatus() == AssessmentStatus.ADJUSTED_CONDITIONS;
    }

    @Override
    public DecisionResult makeDecision(CreditAssessmentCompletedEvent completedEvent, String initialReason, MeterRegistry meterRegistry) {
        if (hasInvalidTerms(completedEvent)) {
            return handleInvalidApprovedTerms(completedEvent, meterRegistry);
        } else {
            return handleValidApprovedTerms(completedEvent, initialReason, meterRegistry);
        }
    }

    private boolean hasInvalidTerms(CreditAssessmentCompletedEvent event) {
        return event.approvedLimit() == null || 
               event.approvedLimit().compareTo(BigDecimal.ZERO) <= 0 ||
               event.interestRateApplied() == null || 
               event.interestRateApplied().compareTo(BigDecimal.ZERO) < 0;
    }

    private DecisionResult handleInvalidApprovedTerms(CreditAssessmentCompletedEvent event, MeterRegistry meterRegistry) {
        Counter manualReviewLoansCounter = Counter.builder("loan.decisions")
                .tag("status", "manual_review")
                .description("Number of loans sent to manual review")
                .register(meterRegistry);

        String reason = "Approved by credit assessment but terms are invalid/missing. Needs manual review.";
        manualReviewLoansCounter.increment();
        log.warn("Application {} sent to manual review: Approved by credit assessment but terms are invalid. Limit: {}, Rate: {}",
                event.applicationId(), event.approvedLimit(), event.interestRateApplied());
        return new DecisionResult(LoanDecision.PENDING_MANUAL_REVIEW, null, reason);
    }

    private DecisionResult handleValidApprovedTerms(CreditAssessmentCompletedEvent event, String reason, MeterRegistry meterRegistry) {
        Counter approvedLoansCounter = Counter.builder("loan.decisions")
                .tag("status", "approved")
                .description("Number of approved loans")
                .register(meterRegistry);

        LoanTerms finalTerms = loanTermsCalculator.calculateDefaultLoanTerms(event.approvedLimit(), event.interestRateApplied());
        String updatedReason = (reason == null ? "Approved" : reason + " Approved with standard terms.");
        approvedLoansCounter.increment();
        log.info("Application {} APPROVED. Terms: Amount={}, Rate={}, Installments={}, InstallmentAmount={}",
                event.applicationId(), finalTerms.getApprovedAmount(), finalTerms.getInterestRate(),
                finalTerms.getNumberOfInstallments(), finalTerms.getInstallmentAmount());
        return new DecisionResult(LoanDecision.APPROVED, finalTerms, updatedReason);
    }
}
