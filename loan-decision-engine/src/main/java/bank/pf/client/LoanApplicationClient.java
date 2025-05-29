package bank.pf.client;

import bank.pf.dto.request.LoanApplicationUpdateStatusRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanApplicationClient {

    private final RestClient restClient;

    @Value("${app.loan-application-service.update-status-uri}")
    private String updateStatusUri;

    public void updateLoanApplicationStatus(String applicationId, LoanApplicationUpdateStatusRequest updateStatusRequest) {
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
