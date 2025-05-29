package bank.pf.service.strategy;

import bank.pf.dto.DecisionResult;
import bank.pf.dto.event.CreditAssessmentCompletedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class LoanDecisionStrategyContext {

    private final List<LoanDecisionStrategy> strategies;
    private final MeterRegistry meterRegistry;

    public DecisionResult determineDecision(CreditAssessmentCompletedEvent completedEvent) {
        String initialReason = completedEvent.justification();
        
        for (LoanDecisionStrategy strategy : strategies) {
            if (strategy.canHandle(completedEvent)) {
                log.debug("Using strategy: {} for application ID: {}", 
                        strategy.getClass().getSimpleName(), completedEvent.applicationId());
                return strategy.makeDecision(completedEvent, initialReason, meterRegistry);
            }
        }

        log.warn("No strategy found for application ID: {} with status: {}", 
                completedEvent.applicationId(), completedEvent.finalAssessmentStatus());

        LoanDecisionStrategy unknownStrategy = strategies.stream()
                .filter(UnknownStatusLoanDecisionStrategy.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No UnknownStatusLoanDecisionStrategy found"));
                
        return unknownStrategy.makeDecision(completedEvent, initialReason, meterRegistry);
    }
}