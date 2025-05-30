package bank.pf.messaging;

import bank.pf.dto.event.LoanDecisionMadeEvent;
import bank.pf.enums.LoanDecision;
import bank.pf.service.EmailService;
import bank.pf.service.PDFGenerationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanDecisionEventConsumer {

    private final EmailService emailService;
    private final MeterRegistry meterRegistry;
    private final PDFGenerationService pdfGenerationService;

    private Counter approvedNotificationCounter;
    private Counter rejectedNotificationCounter;
    private Counter notificationErrorCounter;

    @PostConstruct
    public void init() {
        approvedNotificationCounter = Counter.builder("loan.notifications.sent")
                .tag("status", "approved").register(meterRegistry);

        rejectedNotificationCounter = Counter.builder("loan.notifications.sent")
                .tag("status", "rejected").register(meterRegistry);

        notificationErrorCounter = Counter.builder("loan.notifications.errors")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topic.loan-decision-made}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void handleLoanDecisionMadeEvent(LoanDecisionMadeEvent event) {
        log.info("Received LoanDecisionMadeEvent on topic. AppId {}, Decision: {}", event.getApplicationId(), event.getDecision());

        if (event.getEmail() == null || event.getEmail().isBlank()) {
            log.error("Email não encontrado no evento para a aplicação {}. Não é possível notificar.", event.getApplicationId());
            notificationErrorCounter.increment();
            return;
        }

        try {
            if (event.getDecision() == LoanDecision.APPROVED) {
                if (event.getTerms() == null) {
                    log.error("Termos do empréstimo não encontrados para aplicação APROVADA {}. Não é possível gerar contrato/notificar.", event.getApplicationId());
                    notificationErrorCounter.increment();
                    return;
                }
                // Gerar PDF
                byte[] pdfContract = pdfGenerationService.generateLoanContractPdf(
                        event.getApplicationId(),
                        event.getCpf(),
                        event.getTerms()
                );
                // Enviar email de aprovação com PDF
                emailService.sendApprovalEmail(
                        event.getEmail(),
                        event.getCpf(),
                        event.getApplicationId(),
                        event.getTerms(),
                        pdfContract
                );
                approvedNotificationCounter.increment();

            } else if (event.getDecision() == LoanDecision.REJECTED) {
                // Enviar email de rejeição
                emailService.sendRejectionEmail(
                        event.getEmail(),
                        event.getCpf(),
                        event.getApplicationId(),
                        event.getReason()
                );
                rejectedNotificationCounter.increment();
            } else {
                log.info("Decisão {} para aplicação {} não requer notificação por este serviço.", event.getDecision(), event.getApplicationId());
            }
        } catch (Exception e) {
            log.error("Erro ao processar notificação para aplicação {}: {}", event.getApplicationId(), e.getMessage(), e);
            notificationErrorCounter.increment();
            // Considerar DLQ para o evento Kafka ou um mecanismo de retentativa mais robusto para o processamento da notificação.
        }
    }
}
