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
public class RejectedLoanDecisionStrategy implements LoanDecisionStrategy {

    @Override
    public boolean canHandle(CreditAssessmentCompletedEvent completedEvent) {
        return completedEvent.finalAssessmentStatus() == AssessmentStatus.REJECTED;
    }

    @Override
    public DecisionResult makeDecision(CreditAssessmentCompletedEvent completedEvent, String initialReason, MeterRegistry meterRegistry) {
        Counter rejectedLoansCounter = Counter.builder("loan.decisions")
                .tag("status", "rejected")
                .description("Number of rejected loans")
                .register(meterRegistry);

        rejectedLoansCounter.increment();
        log.info("Application {} REJECTED based on credit assessment: {}", completedEvent.applicationId(), initialReason);
        return new DecisionResult(LoanDecision.REJECTED, null, initialReason);
    }
}
