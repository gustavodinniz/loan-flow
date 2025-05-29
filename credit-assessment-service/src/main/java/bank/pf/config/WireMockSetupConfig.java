package bank.pf.config;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class WireMockSetupConfig {

    private final WireMockConfig wireMockConfig;

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
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/bureau/score/.*00"))
                .atPriority(1)
                .willReturn(WireMock.aResponse()
                        .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "cpf": "{{jsonPath request.body '$.cpf'}}",
                                  "score": 900,
                                  "assessment": "HIGH_RISK",
                                  "hasRestrictions": true,
                                  "paymentHistory": "POOR_OVERDUE_60_DAYS",
                                  "monthlyDebts": 1000.00
                                }
                                """)
                        .withTransformers("response-template")));

        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/bureau/score/.*01"))
                .atPriority(2)
                .willReturn(WireMock.aResponse()
                        .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "cpf": "{{jsonPath request.body '$.cpf'}}",
                                  "score": 400,
                                  "assessment": "MEDIUM_RISK",
                                  "hasRestrictions": false,
                                  "paymentHistory": "GOOD",
                                  "monthlyDebts": 500.00
                                }
                                """)
                        .withTransformers("response-template")));

        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/bureau/score/.*"))
                .atPriority(3)
                .willReturn(WireMock.aResponse()
                        .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "cpf": "{{jsonPath request.body '$.cpf'}}",
                                  "score": 900,
                                  "assessment": "LOW_RISK",
                                  "hasRestrictions": false,
                                  "paymentHistory": "EXCELLENT",
                                  "monthlyDebts": 0
                                }
                                """)
                        .withTransformers("response-template")));

    }

    private void setupAntiFraudScore(WireMockServer wireMockServer) {
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/api/antifraud/check"))
                .withRequestBody(WireMock.matchingJsonPath("$.cpf", WireMock.matching(".*03$")))
                .atPriority(1)
                .willReturn(WireMock.aResponse()
                        .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "applicationId": "{{jsonPath request.body '$.applicationId'}}",
                                  "fraudScore": 900,
                                  "recommendation": "REJECT"
                                }
                                """)
                        .withTransformers("response-template")));

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/api/antifraud/check"))
                .withRequestBody(WireMock.matchingJsonPath("$.cpf", WireMock.matching(".*04$")))
                .atPriority(2)
                .willReturn(WireMock.aResponse()
                        .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "applicationId": "{{jsonPath request.body '$.applicationId'}}",
                                  "fraudScore": 400,
                                  "recommendation": "MANUAL_REVIEW"
                                }
                                """)
                        .withTransformers("response-template")));

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/api/antifraud/check"))
                .withRequestBody(WireMock.matchingJsonPath("$.cpf"))
                .atPriority(3)
                .willReturn(WireMock.aResponse()
                        .withStatus(HttpStatus.OK.value()).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "applicationId": "{{jsonPath request.body '$.applicationId'}}",
                                  "fraudScore": 100,
                                  "recommendation": "ACCEPT"
                                }
                                """)
                        .withTransformers("response-template")));

    }
}
