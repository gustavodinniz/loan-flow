package bank.pf.service;

import bank.pf.dto.LoanTerms;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Locale LOCALE_BR = new Locale("pt", "BR");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(LOCALE_BR);
    private static final NumberFormat PERCENT_FORMAT = NumberFormat.getPercentInstance(LOCALE_BR);

    static {
        PERCENT_FORMAT.setMinimumFractionDigits(2);
        PERCENT_FORMAT.setMaximumFractionDigits(4);
    }

    private final JavaMailSender mailSender;
    private final ResourceLoader resourceLoader;

    @Value("${spring.mail.username:noreply@credfacil.com}")
    private String fromEmail;

    private String loadEmailTemplate(String templatePath, Map<String, String> placeholders) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:templates/" + templatePath);
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            String templateContent = FileCopyUtils.copyToString(reader);
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                templateContent = templateContent.replace("${" + entry.getKey() + "}", entry.getValue());
            }
            // Extrair o assunto da primeira linha e remover
            String[] lines = templateContent.split("\\R", 2); // \\R para quebra de linha universal
            if (lines.length > 0 && lines[0].toLowerCase().startsWith("assunto:")) {
                // O corpo do templateContent será o restante
                return templateContent; // O chamador pegará o assunto e o corpo
            }
            return templateContent; // Se não houver linha de assunto
        }
    }

    private String extractSubjectFromTemplate(String fullTemplateContent) {
        String[] lines = fullTemplateContent.split("\\R", 2);
        if (lines.length > 0 && lines[0].toLowerCase().startsWith("assunto:")) {
            return lines[0].substring("assunto:".length()).trim();
        }
        return "Notificação CredFácil"; // Assunto padrão
    }

    private String extractBodyFromTemplate(String fullTemplateContent) {
        String[] lines = fullTemplateContent.split("\\R", 2);
        if (lines.length > 1 && lines[0].toLowerCase().startsWith("assunto:")) {
            return lines[1];
        }
        return fullTemplateContent;
    }


    public void sendApprovalEmail(String to, String cpf, String applicationId, LoanTerms terms, byte[] pdfAttachment) {
        try {
            Map<String, String> placeholders = Map.of(
                    "cpf", cpf,
                    "applicationId", applicationId,
                    "approvedAmount", CURRENCY_FORMAT.format(terms.getApprovedAmount()),
                    "interestRate", PERCENT_FORMAT.format(terms.getInterestRate()),
                    "numberOfInstallments", String.valueOf(terms.getNumberOfInstallments()),
                    "installmentAmount", CURRENCY_FORMAT.format(terms.getInstallmentAmount())
            );
            String fullTemplateContent = loadEmailTemplate("approval_template.txt", placeholders);
            String subject = extractSubjectFromTemplate(fullTemplateContent);
            String body = extractBodyFromTemplate(fullTemplateContent);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name()); // true para multipart

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false); // false indica que não é HTML

            if (pdfAttachment != null && pdfAttachment.length > 0) {
                DataSource attachmentDataSource = new ByteArrayDataSource(pdfAttachment, "application/pdf");
                helper.addAttachment("ContratoEmprestimo_" + applicationId + ".pdf", attachmentDataSource);
            }

            mailSender.send(message);
            log.info("Email de aprovação enviado para {} referente à aplicação {}", to, applicationId);

        } catch (MessagingException | IOException e) {
            log.error("Falha ao enviar email de aprovação para {} (Aplicação {}): {}", to, applicationId, e.getMessage(), e);
            // Tratar erro (ex: DLQ para reenvio)
        }
    }

    public void sendRejectionEmail(String to, String cpf, String applicationId, String reason) {
        try {
            Map<String, String> placeholders = Map.of(
                    "cpf", cpf,
                    "applicationId", applicationId,
                    "reason", reason != null ? reason : "Não especificado"
            );
            String fullTemplateContent = loadEmailTemplate("rejection_template.txt", placeholders);
            String subject = extractSubjectFromTemplate(fullTemplateContent);
            String body = extractBodyFromTemplate(fullTemplateContent);


            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name()); // false para email simples

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);

            mailSender.send(message);
            log.info("Email de rejeição enviado para {} referente à aplicação {}", to, applicationId);

        } catch (MessagingException | IOException e) {
            log.error("Falha ao enviar email de rejeição para {} (Aplicação {}): {}", to, applicationId, e.getMessage(), e);
        }
    }

}
