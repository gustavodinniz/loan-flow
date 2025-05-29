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
public class PendingManualReviewLoanDecisionStrategy implements LoanDecisionStrategy {

    @Override
    public boolean canHandle(CreditAssessmentCompletedEvent completedEvent) {
        return completedEvent.finalAssessmentStatus() == AssessmentStatus.PENDING_MANUAL_REVIEW;
    }

    @Override
    public DecisionResult makeDecision(CreditAssessmentCompletedEvent completedEvent, String initialReason, MeterRegistry meterRegistry) {
        Counter manualReviewLoansCounter = Counter.builder("loan.decisions")
                .tag("status", "manual_review")
                .description("Number of loans sent to manual review")
                .register(meterRegistry);
                
        String updatedReason = (initialReason == null ? "" : initialReason) + " Flagged for manual review by credit assessment.";
        manualReviewLoansCounter.increment();
        log.info("Application {} PENDING_MANUAL_REVIEW based on credit assessment: {}", completedEvent.applicationId(), updatedReason);
        return new DecisionResult(LoanDecision.PENDING_MANUAL_REVIEW, null, updatedReason);
    }
}