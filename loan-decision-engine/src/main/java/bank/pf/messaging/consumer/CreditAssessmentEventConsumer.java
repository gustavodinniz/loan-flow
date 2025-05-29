package bank.pf.messaging.consumer;

import bank.pf.dto.event.CreditAssessmentCompletedEvent;
import bank.pf.service.LoanDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditAssessmentEventConsumer {

    private final LoanDecisionService loanDecisionService;

    @KafkaListener(
            topics = "${app.kafka.topics.credit-assessment-completed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleCreditAssessmentCompletedEvent(CreditAssessmentCompletedEvent creditAssessmentCompletedEvent) {
        try {
            log.info("Received LoanApplicationReceivedEvent on topic {}", creditAssessmentCompletedEvent);
            loanDecisionService.processDecision(creditAssessmentCompletedEvent);
            log.info("Successfully processed decision for application ID: {}", creditAssessmentCompletedEvent.applicationId());
        } catch (Exception e) {
            log.error("Error processing CreditAssessmentCompletedEvent for application ID {}: {}", creditAssessmentCompletedEvent.applicationId(), e.getMessage());
        }
    }
}
