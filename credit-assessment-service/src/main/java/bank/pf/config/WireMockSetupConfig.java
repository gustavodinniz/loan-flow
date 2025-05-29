package bank.pf.config;

import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.enums.AssessmentType;
import bank.pf.enums.PaymentHistoryType;
import bank.pf.enums.RecommendationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WireMockSetupConfig {

    private final WireMockConfig wireMockConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Getter
    private String baseUrl;

    @Getter
    private RestClient restClient;

    @PostConstruct
    public void setup() {
        WireMockServer wireMockServer = wireMockConfig.getWireMockServer();
        baseUrl = "http://localhost:" + wireMockServer.port();
        restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();

        setupBureauScore(wireMockServer);
        setupAntiFraudScore(wireMockServer);
    }

    private void setupBureauScore(WireMockServer wireMockServer) {
        try {
            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/bureau/score/.*00"))
                    .atPriority(1)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(new BureauScore("", 100, AssessmentType.HIGH_RISK, true, PaymentHistoryType.POOR_OVERDUE_60_DAYS, BigDecimal.valueOf(1000))))));

            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/bureau/score/.*01"))
                    .atPriority(2)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(new BureauScore("", 400, AssessmentType.MEDIUM_RISK, false, PaymentHistoryType.GOOD, BigDecimal.valueOf(500))))));

            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/bureau/score/.*"))
                    .atPriority(3)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(new BureauScore("", 900, AssessmentType.LOW_RISK, false, PaymentHistoryType.EXCELLENT, BigDecimal.ZERO)))));

        } catch (JsonProcessingException e) {
            log.error("Error setting up BureauScoreStub", e);
        }
    }

    private void setupAntiFraudScore(WireMockServer wireMockServer) {
        try {
            wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/api/antifraud/check"))
                    .withRequestBody(WireMock.matchingJsonPath("$.cpf", WireMock.matching(".*03$")))
                    .atPriority(1)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(new AntiFraudScore(UUID.randomUUID().toString(), 100, RecommendationType.REJECT)))));

            wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/api/antifraud/check"))
                    .withRequestBody(WireMock.matchingJsonPath("$.cpf", WireMock.matching(".*04$")))
                    .atPriority(2)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(new AntiFraudScore(UUID.randomUUID().toString(), 400, RecommendationType.MANUAL_REVIEW)))));

            wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/api/antifraud/check"))
                    .withRequestBody(WireMock.matchingJsonPath("$.cpf"))
                    .atPriority(3)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(new AntiFraudScore(UUID.randomUUID().toString(), 900, RecommendationType.ACCEPT)))));
        } catch (JsonProcessingException e) {
            log.error("Error setting up AntiFraudStub", e);
        }

    }
}
