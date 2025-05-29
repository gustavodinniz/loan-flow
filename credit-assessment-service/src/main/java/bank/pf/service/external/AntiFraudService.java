package bank.pf.service.external;

import bank.pf.config.WireMockSetupConfig;
import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.dto.request.AntiFraudScoreRequest;
import bank.pf.entity.AntiFraudScore;
import bank.pf.exception.AntiFraudApiException;
import bank.pf.exception.AntiFraudNullResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AntiFraudService {

    private final WireMockSetupConfig wireMockSetupConfig;

    public AntiFraudScore checkFraud(LoanApplicationReceivedEvent applicationData) {
        try {
            log.info("Checking fraud for application ID: {}", applicationData.applicationId());
            var antiFraudScoreRequest = AntiFraudScoreRequest.valueOf(applicationData);
            var antiFraudScore = wireMockSetupConfig.getRestClient().post()
                    .uri("/api/antifraud/check")
                    .body(antiFraudScoreRequest)
                    .retrieve()
                    .body(AntiFraudScore.class);

            if (antiFraudScore != null) {
                log.info("Successfully received anti-fraud antiFraudScore for application ID {}: {}", applicationData.applicationId(), antiFraudScore);
                return antiFraudScore;
            }

            throw new AntiFraudNullResponseException(applicationData.applicationId());
        } catch (AntiFraudNullResponseException e) {
            log.error("Received null response from anti-fraud service for application ID {}: {}", applicationData.applicationId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error during anti-fraud check for application ID {}: {}", applicationData.applicationId(), e.getMessage(), e);
            throw new AntiFraudApiException(applicationData.applicationId(), e);
        }
    }


}
