package bank.pf.messaging.producer;

import bank.pf.dto.event.CreditAssessmentCompletedEvent;
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
public class CreditAssessmentEventProducer {

    @Value("${app.kafka.topic.credit-assessment-completed:CreditAssessmentCompletedEventTopic}")
    private String topicName;

    private final KafkaTemplate<String, CreditAssessmentCompletedEvent> kafkaTemplate;

    public void sendCreditAssessmentCompletedEvent(CreditAssessmentCompletedEvent event) {
        log.info("Sending CreditAssessmentCompletedEvent for applicationId: {} to topic: {}", event.applicationId(), topicName);
        try {
            CompletableFuture<SendResult<String, CreditAssessmentCompletedEvent>> future =
                    kafkaTemplate.send(topicName, event.applicationId(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully sent CreditAssessmentCompletedEvent: {} with offset: {}", event.applicationId(), result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send CreditAssessmentCompletedEvent for applicationId {}: {}", event.applicationId(), ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            log.error("Exception occurred while sending CreditAssessmentCompletedEvent for applicationId {}: {}", event.applicationId(), e.getMessage(), e);
        }
    }
}
