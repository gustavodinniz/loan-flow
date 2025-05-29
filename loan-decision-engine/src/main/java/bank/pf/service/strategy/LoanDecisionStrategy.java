package bank.pf.service.strategy;

import bank.pf.dto.DecisionResult;
import bank.pf.dto.event.CreditAssessmentCompletedEvent;
import io.micrometer.core.instrument.MeterRegistry;

public interface LoanDecisionStrategy {
    boolean canHandle(CreditAssessmentCompletedEvent completedEvent);

    DecisionResult makeDecision(CreditAssessmentCompletedEvent completedEvent, String initialReason, MeterRegistry meterRegistry);
}
