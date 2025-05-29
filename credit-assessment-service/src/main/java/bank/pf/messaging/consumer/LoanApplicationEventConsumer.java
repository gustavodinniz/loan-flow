package bank.pf.messaging.consumer;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.service.CreditAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanApplicationEventConsumer {

    private final CreditAssessmentService creditAssessmentService;

    @KafkaListener(topics = "${app.kafka.topic.loan-application-received:LoanApplicationReceivedEventTopic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void handleLoanApplicationReceivedEvent(LoanApplicationReceivedEvent loanApplicationReceivedEvent) {
        try {
            log.info("Received LoanApplicationReceivedEvent on topic {}", loanApplicationReceivedEvent);
            creditAssessmentService.assessCredit(loanApplicationReceivedEvent);
            log.info("Successfully processed application ID: {}", loanApplicationReceivedEvent.applicationId());
        } catch (Exception e) {
            log.error("Error processing LoanApplicationReceivedEvent for application ID: {}. Message: {}", loanApplicationReceivedEvent.applicationId(), e.getMessage());
        }
    }

}
