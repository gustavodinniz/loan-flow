package bank.pf.service;

import bank.pf.dto.event.CreditAssessmentCompletedEvent;
import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import bank.pf.enums.AssessmentStatus;
import bank.pf.messaging.producer.CreditAssessmentEventProducer;
import bank.pf.service.chain.AssessmentRuleExecutor;
import bank.pf.service.external.AntiFraudService;
import bank.pf.service.external.BureauService;
import bank.pf.service.strategy.CreditRiskStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

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

        Optional<BureauScore> bureauScoreOpt = bureauService.getScore(loanApplicationReceivedEvent.cpf());
        if (bureauScoreOpt.isEmpty()) {
            log.warn("Could not retrieve bureau score for CPF: {}. Assessment cannot proceed.", loanApplicationReceivedEvent.cpf());
            // Aqui você pode decidir publicar um evento de falha ou tratar de outra forma.
            // Por simplicidade, vamos apenas logar e não prosseguir.
            // Em um cenário real, poderia publicar um evento de "AVALIAÇÃO_FALHOU_BUREAU"
            CreditAssessmentResult failedResult = CreditAssessmentResult.builder()
                    .applicationId(loanApplicationReceivedEvent.applicationId())
                    .cpf(loanApplicationReceivedEvent.cpf())
                    .status(AssessmentStatus.REJECTED) // Ou um status específico de falha
                    .justification("Falha ao obter score do bureau.")
                    .build();
            // eventProducer.sendCreditAssessmentCompletedEvent(failedResult); // (Producer ainda não implementado)
            log.warn("Credit assessment for application {} rejected due to bureau score failure.", loanApplicationReceivedEvent.applicationId());
            return;
        }
        BureauScore bureauScore = bureauScoreOpt.get();
        log.info("Bureau score for application {}: {}", loanApplicationReceivedEvent.applicationId(), bureauScore);


        Optional<AntiFraudScore> antiFraudScoreOpt = antiFraudService.checkFraud(loanApplicationReceivedEvent);
        if (antiFraudScoreOpt.isEmpty()) {
            log.warn("Could not retrieve anti-fraud score for application ID: {}. Assessment cannot proceed.", loanApplicationReceivedEvent.applicationId());
            // Similar ao bureau, tratar falha
            CreditAssessmentResult failedResult = CreditAssessmentResult.builder()
                    .applicationId(loanApplicationReceivedEvent.applicationId())
                    .cpf(loanApplicationReceivedEvent.cpf())
                    .status(AssessmentStatus.REJECTED)
                    .justification("Falha ao obter score antifraude.")
                    .build();
            // eventProducer.sendCreditAssessmentCompletedEvent(failedResult);
            log.warn("Credit assessment for application {} rejected due to anti-fraud score failure.", loanApplicationReceivedEvent.applicationId());
            return;
        }
        AntiFraudScore antiFraudScore = antiFraudScoreOpt.get();
        log.info("Anti-fraud score for application {}: {}", loanApplicationReceivedEvent.applicationId(), antiFraudScore);

        CreditAssessmentResult creditAssessmentResult = CreditAssessmentResult.builder()
                .applicationId(loanApplicationReceivedEvent.applicationId())
                .cpf(loanApplicationReceivedEvent.cpf())
                .status(AssessmentStatus.APPROVED)
                .finalScore(bureauScore.score())
                .build();

        assessmentRuleExecutor.executeChain(loanApplicationReceivedEvent, bureauScore, antiFraudScore, creditAssessmentResult);

        if (creditAssessmentResult.getStatus() != AssessmentStatus.REJECTED) {
            CreditRiskStrategy selectedStrategy = riskStrategies.stream()
                    .filter(strategy -> strategy.appliesTo(bureauScore.score()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No risk strategy found for score: " + bureauScore.score()));

            selectedStrategy.assessRisk(loanApplicationReceivedEvent, bureauScore, creditAssessmentResult);
        }

        log.info("Final credit assessment for application {}: Status - {}, Justification - {}",
                loanApplicationReceivedEvent.applicationId(), creditAssessmentResult.getStatus(), creditAssessmentResult.getJustification());

        CreditAssessmentCompletedEvent completedEvent = buildEvent(creditAssessmentResult, bureauScore, antiFraudScore);
        creditAssessmentEventProducer.sendCreditAssessmentCompletedEvent(completedEvent);
    }

    private CreditAssessmentCompletedEvent buildEvent(CreditAssessmentResult result, BureauScore bureauScore, AntiFraudScore antiFraudScore) {
        return CreditAssessmentCompletedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventTimestamp(java.time.LocalDateTime.now())
                .applicationId(result.getApplicationId())
                .cpf(result.getCpf())
                .finalAssessmentStatus(result.getStatus())
                .justification(result.getJustification())
                .creditScoreUsed(bureauScore.score())
                .antiFraudScoreUsed(antiFraudScore.fraudScore())
                .approvedLimit(result.getRecommendedLimit())
                .interestRateApplied(result.getRecommendedInterestRate())
                .build();
    }
}
