package bank.pf.messaging.producer;

import bank.pf.dto.event.LoanDecisionMadeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanDecisionEventProducer {

    @Value("${app.kafka.topics.loan-decision-made}")
    private String topicName;

    private final KafkaTemplate<String, LoanDecisionMadeEvent> kafkaTemplate;

    public void sendLoanDecisionMadeEvent(LoanDecisionMadeEvent loanDecisionMadeEvent) {
        try {
            log.info("Sending LoanDecisionMadeEvent for applicationId: {} to topic: {}", loanDecisionMadeEvent.getApplicationId(), topicName);
            CompletableFuture<SendResult<String, LoanDecisionMadeEvent>> future =
                    kafkaTemplate.send(topicName, loanDecisionMadeEvent.getApplicationId(), loanDecisionMadeEvent);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully sent LoanDecisionMadeEvent: {} with offset: {}", loanDecisionMadeEvent.getApplicationId(), result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send LoanDecisionMadeEvent for applicationId {}: {}", loanDecisionMadeEvent.getApplicationId(), ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            log.error("Exception occurred while sending LoanDecisionMadeEvent for applicationId {}: {}", loanDecisionMadeEvent.getApplicationId(), e.getMessage(), e);
        }
    }
}
