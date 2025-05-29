package bank.pf.service.strategy;

import bank.pf.dto.DecisionResult;
import bank.pf.dto.event.CreditAssessmentCompletedEvent;
import bank.pf.enums.AssessmentStatus;
import bank.pf.enums.LoanDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UnknownStatusLoanDecisionStrategy implements LoanDecisionStrategy {

    @Override
    public boolean canHandle(CreditAssessmentCompletedEvent completedEvent) {
        return completedEvent.finalAssessmentStatus() != AssessmentStatus.REJECTED &&
               completedEvent.finalAssessmentStatus() != AssessmentStatus.PENDING_MANUAL_REVIEW &&
               completedEvent.finalAssessmentStatus() != AssessmentStatus.APPROVED &&
               completedEvent.finalAssessmentStatus() != AssessmentStatus.ADJUSTED_CONDITIONS;
    }

    @Override
    public DecisionResult makeDecision(CreditAssessmentCompletedEvent completedEvent, String initialReason, MeterRegistry meterRegistry) {
        Counter manualReviewLoansCounter = Counter.builder("loan.decisions")
                .tag("status", "manual_review")
                .description("Number of loans sent to manual review")
                .register(meterRegistry);
                
        String reason = "Unknown or unexpected status from credit assessment: " + completedEvent.finalAssessmentStatus() + ". Sent for manual review.";
        manualReviewLoansCounter.increment();
        log.warn("Application {} PENDING_MANUAL_REVIEW due to unexpected status: {}", completedEvent.applicationId(), completedEvent.finalAssessmentStatus());
        return new DecisionResult(LoanDecision.PENDING_MANUAL_REVIEW, null, reason);
    }
}