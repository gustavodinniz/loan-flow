package bank.pf.client;

import bank.pf.dto.request.LoanApplicationUpdateStatusRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanApplicationClient {

    private final RestClient restClient;

    @Value("${app.loan-application-service.update-status-uri}")
    private String updateStatusUri;

    @Retryable(retryFor = RuntimeException.class, maxAttempts = 5)
    public void updateLoanApplicationStatus(String applicationId, LoanApplicationUpdateStatusRequest updateStatusRequest) {
        int retryNumber = 1 + Objects.requireNonNull(RetrySynchronizationManager.getContext()).getRetryCount();
        log.info("Attempting to update status for application ID {} to {} via API. Attempt: {}", applicationId, updateStatusRequest.status(), retryNumber);
        log.info("Updating status for application ID {} to {} via API", applicationId, updateStatusRequest.status());
        try {
            restClient.put()
                    .uri(updateStatusUri, applicationId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updateStatusRequest)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully updated status for application ID {}", applicationId);
        } catch (Exception e) {
            log.error("Error updating status for application ID {}: {}", applicationId, e.getMessage(), e);
            throw new RuntimeException("Failed to update loan application status for " + applicationId, e);
        }
    }
}
