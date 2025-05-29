package bank.pf.service;

import bank.pf.client.LoanApplicationClient;
import bank.pf.dto.DecisionResult;
import bank.pf.dto.LoanTerms;
import bank.pf.dto.event.CreditAssessmentCompletedEvent;
import bank.pf.dto.event.LoanDecisionMadeEvent;
import bank.pf.dto.request.LoanApplicationUpdateStatusRequest;
import bank.pf.enums.LoanDecision;
import bank.pf.messaging.producer.LoanDecisionEventProducer;
import bank.pf.service.strategy.LoanDecisionStrategyContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

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
    private final LoanDecisionStrategyContext strategyContext;

    private Counter apiUpdateFailureCounter;
    private Timer apiUpdateTimer;

    @PostConstruct
    private void initCounters() {
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

        DecisionResult decisionResult = determineLoanDecision(creditAssessmentCompletedEvent);
        LoanDecision finalDecision = decisionResult.decision();
        LoanTerms finalTerms = decisionResult.terms();
        String reason = decisionResult.reason();

        LoanDecisionMadeEvent decisionEvent = createDecisionEvent(creditAssessmentCompletedEvent, finalDecision, reason, finalTerms);
        loanDecisionEventProducer.sendLoanDecisionMadeEvent(decisionEvent);

        LoanApplicationUpdateStatusRequest updateRequest = createUpdateRequest(
                creditAssessmentCompletedEvent.applicationId(), finalDecision, reason, finalTerms);

        updateLoanApplicationStatus(creditAssessmentCompletedEvent.applicationId(), updateRequest);

        decisionTimerSample.stop(meterRegistry.timer("loan.decision.processing.duration", "decision", finalDecision.name()));
    }


    private DecisionResult determineLoanDecision(CreditAssessmentCompletedEvent completedEvent) {
        return strategyContext.determineDecision(completedEvent);
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
                .installmentValue(loanTerms != null ? loanTerms.getInstallmentAmount() : null)
                .installments(loanTerms != null ? loanTerms.getNumberOfInstallments() : null)
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

    public void callUpdateLoanApplicationStatusApi(String applicationId, LoanApplicationUpdateStatusRequest updateStatusRequest) {
        loanApplicationClient.updateLoanApplicationStatus(applicationId, updateStatusRequest);
    }
}
