package bank.pf.service;

import bank.pf.config.WireMockConfig;
import bank.pf.dto.response.AccountValidationResponse;
import bank.pf.dto.response.CpfValidationResponse;
import bank.pf.dto.response.InternalRestrictResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalValidationService {

    private final Random random = new Random();
    private final WireMockConfig wireMockConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private RestClient restClient;
    private String baseUrl;

    @PostConstruct
    public void setup() {
        WireMockServer wireMockServer = wireMockConfig.getWireMockServer();
        baseUrl = "http://localhost:" + wireMockServer.port();
        restClient = RestClient.builder().baseUrl(baseUrl).build();

        setupCpfValidationStubs(wireMockServer);
        setupAccountValidationStubs(wireMockServer);
        setupInternalRestrictionsStubs(wireMockServer);
    }

    private void setupCpfValidationStubs(WireMockServer wireMockServer) {
        try {
            // CPFs ending with 00 are not regular - higher priority
            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/cpf-validation/.*00"))
                    .atPriority(1)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(
                                    new CpfValidationResponse(true, false, "CPF com pendências na Receita Federal")))));

            // Random regular/irregular CPF response for other CPFs - lower priority
            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/cpf-validation/.*"))
                    .atPriority(2)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(
                                    new CpfValidationResponse(true, true, "CPF regular")))));

        } catch (JsonProcessingException e) {
            log.error("Error setting up CPF validation stubs", e);
        }
    }

    private void setupAccountValidationStubs(WireMockServer wireMockServer) {
        try {
            // CPFs ending with 9 don't have active accounts - higher priority
            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/account-validation/.*9"))
                    .atPriority(1)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(
                                    new AccountValidationResponse(false, "Cliente não possui conta ativa")))));

            // Active account response for other CPFs - lower priority
            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/account-validation/.*"))
                    .atPriority(2)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(
                                    new AccountValidationResponse(true, "Conta ativa")))));

        } catch (JsonProcessingException e) {
            log.error("Error setting up account validation stubs", e);
        }
    }

    private void setupInternalRestrictionsStubs(WireMockServer wireMockServer) {
        try {
            // CPFs ending with 88 have restrictions - higher priority
            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/internal-restrictions/.*88"))
                    .atPriority(1)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(
                                    new InternalRestrictResponse(true, "Cliente possui restrições internas graves")))));

            // No restrictions response for other CPFs - lower priority
            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/api/internal-restrictions/.*"))
                    .atPriority(2)
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(
                                    new InternalRestrictResponse(false, "Sem restrições internas graves")))));

        } catch (JsonProcessingException e) {
            log.error("Error setting up internal restrictions stubs", e);
        }
    }

    public CompletableFuture<CpfValidationResponse> validateCpfStatus(String cpf) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread (validateCpfStatus for {}): {}", cpf, Thread.currentThread());
            try {
                Thread.sleep(random.nextInt(200)); // Simulate network delay
                return restClient.get()
                        .uri("/api/cpf-validation/{cpf}", cpf)
                        .retrieve()
                        .body(CpfValidationResponse.class);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new CpfValidationResponse(false, false, "Validação de CPF interrompida");
            } catch (Exception e) {
                log.error("Error calling CPF validation service", e);
                return new CpfValidationResponse(false, false, "Erro ao validar CPF: " + e.getMessage());
            }
        }, virtualThreadExecutor);
    }

    public CompletableFuture<AccountValidationResponse> checkAccountActive(String cpf) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread (checkAccountActive for {}): {}", cpf, Thread.currentThread());
            try {
                Thread.sleep(random.nextInt(150)); // Simulate network delay
                return restClient.get()
                        .uri("/api/account-validation/{cpf}", cpf)
                        .retrieve()
                        .body(AccountValidationResponse.class);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new AccountValidationResponse(false, "Validação de conta interrompida");
            } catch (Exception e) {
                log.error("Error calling account validation service", e);
                return new AccountValidationResponse(false, "Erro ao validar conta: " + e.getMessage());
            }
        }, virtualThreadExecutor);
    }

    public CompletableFuture<InternalRestrictResponse> checkInternalRestrictions(String cpf) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread (checkInternalRestrictions for {}): {}", cpf, Thread.currentThread());
            try {
                Thread.sleep(random.nextInt(100)); // Simulate network delay
                return restClient.get()
                        .uri("/api/internal-restrictions/{cpf}", cpf)
                        .retrieve()
                        .body(InternalRestrictResponse.class);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new InternalRestrictResponse(true, "Validação de restrições interrompida");
            } catch (Exception e) {
                log.error("Error calling internal restrictions service", e);
                return new InternalRestrictResponse(true, "Erro ao validar restrições: " + e.getMessage());
            }
        }, virtualThreadExecutor);
    }
}
