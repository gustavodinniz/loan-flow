package bank.pf.service.external;

import bank.pf.config.WireMockSetupConfig;
import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.dto.request.AntiFraudScoreRequest;
import bank.pf.entity.AntiFraudScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AntiFraudService {

    private final WireMockSetupConfig wireMockSetupConfig;

    public Optional<AntiFraudScore> checkFraud(LoanApplicationReceivedEvent applicationData) {
        log.info("Checking fraud for application ID: {}", applicationData.applicationId());
        var antiFraudScoreRequest = AntiFraudScoreRequest.valueOf(applicationData);

        try {
            var antiFraudScore = wireMockSetupConfig.getRestClient().post()
                    .uri("/api/antifraud/check")
                    .body(antiFraudScoreRequest)
                    .retrieve()
                    .body(AntiFraudScore.class);

            if (antiFraudScore != null) {
                log.info("Successfully received anti-fraud antiFraudScore for application ID {}: {}", applicationData.applicationId(), antiFraudScore);
                return Optional.of(antiFraudScore);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error during anti-fraud check for application ID {}: {}", applicationData.applicationId(), e.getMessage(), e);
            return Optional.empty();
        }
    }


}
