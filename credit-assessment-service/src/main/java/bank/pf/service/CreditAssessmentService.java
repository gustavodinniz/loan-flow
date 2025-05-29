package bank.pf.service;

import bank.pf.dto.event.CreditAssessmentCompletedEvent;
import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import bank.pf.enums.AssessmentStatus;
import bank.pf.exception.*;
import bank.pf.messaging.producer.CreditAssessmentEventProducer;
import bank.pf.service.chain.AssessmentRuleExecutor;
import bank.pf.service.external.AntiFraudService;
import bank.pf.service.external.BureauService;
import bank.pf.service.strategy.CreditRiskStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditAssessmentService {

    private final BureauService bureauService;
    private final AntiFraudService antiFraudService;
    private final AssessmentRuleExecutor assessmentRuleExecutor;
    private final List<CreditRiskStrategy> riskStrategies;
    private final CreditAssessmentEventProducer creditAssessmentEventProducer;


    public void assessCredit(LoanApplicationReceivedEvent loanApplicationReceivedEvent) {
        log.info("Starting credit assessment for application ID: {}", loanApplicationReceivedEvent.applicationId());
        var bureauScore = handleBureauScore(loanApplicationReceivedEvent);
        if (bureauScore == null) return;

        var antiFraudScore = handleAntiFraudScore(loanApplicationReceivedEvent);
        if (antiFraudScore == null) return;

        var creditAssessmentResult = buildCreditAssessmentResult(loanApplicationReceivedEvent, bureauScore);

        assessmentRuleExecutor.buildRuleChain();
        assessmentRuleExecutor.executeChain(loanApplicationReceivedEvent, bureauScore, antiFraudScore, creditAssessmentResult);
        handleRiskStrategies(loanApplicationReceivedEvent, creditAssessmentResult, bureauScore);

        log.info("Final credit assessment for application {}: Status - {}, Justification - {}",
                loanApplicationReceivedEvent.applicationId(), creditAssessmentResult.getStatus(), creditAssessmentResult.getJustification());

        CreditAssessmentCompletedEvent completedEvent = CreditAssessmentCompletedEvent.valueOf(creditAssessmentResult, bureauScore, antiFraudScore);
        creditAssessmentEventProducer.sendCreditAssessmentCompletedEvent(completedEvent);
    }

    private BureauScore handleBureauScore(LoanApplicationReceivedEvent loanApplicationReceivedEvent) {
        try {
            BureauScore bureauScore = bureauService.getScore(loanApplicationReceivedEvent.cpf());
            log.info("Bureau score for application {}: {}", loanApplicationReceivedEvent.applicationId(), bureauScore);
            return bureauScore;
        } catch (BureauNullResponseException | BureauNotFoundException | BureauApiException e) {
            log.warn("Could not retrieve bureau score for CPF: {}. Assessment cannot proceed. Reason: {}",
                    loanApplicationReceivedEvent.cpf(), e.getMessage());
            CreditAssessmentResult failedResult = CreditAssessmentResult.builder()
                    .applicationId(loanApplicationReceivedEvent.applicationId())
                    .cpf(loanApplicationReceivedEvent.cpf())
                    .status(AssessmentStatus.FAILED)
                    .justification("Failed to retrieve bureau score: " + e.getMessage())
                    .build();
            var failedEvent = CreditAssessmentCompletedEvent.valueOf(failedResult);
            creditAssessmentEventProducer.sendCreditAssessmentCompletedEvent(failedEvent);
            log.warn("Credit assessment for application {} rejected due to bureau score failure.", loanApplicationReceivedEvent.applicationId());
            return null;
        }
    }

    private AntiFraudScore handleAntiFraudScore(LoanApplicationReceivedEvent loanApplicationReceivedEvent) {
        try {
            AntiFraudScore antiFraudScore = antiFraudService.checkFraud(loanApplicationReceivedEvent);
            log.info("Anti-fraud score for application {}: {}", loanApplicationReceivedEvent.applicationId(), antiFraudScore);
            return antiFraudScore;
        } catch (AntiFraudNullResponseException | AntiFraudApiException e) {
            log.warn("Could not retrieve anti-fraud score for application ID: {}. Assessment cannot proceed. Reason: {}",
                    loanApplicationReceivedEvent.applicationId(), e.getMessage());
            CreditAssessmentResult failedResult = CreditAssessmentResult.builder()
                    .applicationId(loanApplicationReceivedEvent.applicationId())
                    .cpf(loanApplicationReceivedEvent.cpf())
                    .status(AssessmentStatus.FAILED)
                    .justification("Failed to retrieve anti-fraud score: " + e.getMessage())
                    .build();
            var failedEvent = CreditAssessmentCompletedEvent.valueOf(failedResult);
            creditAssessmentEventProducer.sendCreditAssessmentCompletedEvent(failedEvent);
            log.warn("Credit assessment for application {} rejected due to anti-fraud score failure.", loanApplicationReceivedEvent.applicationId());
            return null;
        }
    }

    private static CreditAssessmentResult buildCreditAssessmentResult(LoanApplicationReceivedEvent loanApplicationReceivedEvent, BureauScore bureauScore) {
        return CreditAssessmentResult.builder()
                .applicationId(loanApplicationReceivedEvent.applicationId())
                .cpf(loanApplicationReceivedEvent.cpf())
                .status(AssessmentStatus.APPROVED)
                .finalScore(bureauScore.score())
                .build();
    }

    private void handleRiskStrategies(LoanApplicationReceivedEvent loanApplicationReceivedEvent, CreditAssessmentResult creditAssessmentResult, BureauScore bureauScore) {
        if (creditAssessmentResult.getStatus() != AssessmentStatus.REJECTED) {
            CreditRiskStrategy selectedStrategy = riskStrategies.stream()
                    .filter(strategy -> strategy.appliesTo(bureauScore.score()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No risk strategy found for score: " + bureauScore.score()));

            selectedStrategy.assessRisk(loanApplicationReceivedEvent, bureauScore, creditAssessmentResult);
        }
    }


}
