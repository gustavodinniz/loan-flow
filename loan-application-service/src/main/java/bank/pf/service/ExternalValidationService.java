package bank.pf.service;

import bank.pf.dto.response.AccountValidationResponse;
import bank.pf.dto.response.CpfValidationResponse;
import bank.pf.dto.response.InternalRestrictResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final WireMockSetupService wireMockSetupService;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public CompletableFuture<CpfValidationResponse> validateCpfStatus(String cpf) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread (validateCpfStatus for {}): {}", cpf, Thread.currentThread());
            try {
                Thread.sleep(random.nextInt(200)); // Simulate network delay
                return wireMockSetupService.getRestClient().get()
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
                return wireMockSetupService.getRestClient().get()
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
                return wireMockSetupService.getRestClient().get()
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
