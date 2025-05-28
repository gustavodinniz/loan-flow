package bank.pf.service;

import bank.pf.dto.response.AccountValidationResponse;
import bank.pf.dto.response.CpfValidationResponse;
import bank.pf.dto.response.InternalRestrictResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



@Slf4j
@Service
public class ExternalValidationService {

    private final Random random = new Random();

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public CompletableFuture<CpfValidationResponse> validateCpfStatus(String cpf) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread (validateCpfStatus for {}): {}", cpf, Thread.currentThread());
            try {
                Thread.sleep(random.nextInt(200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new CpfValidationResponse(false, false, "Validação de CPF interrompida");
            }

            boolean isRegular = !cpf.endsWith("00");
            boolean isValid = true;
            String message = isRegular ? "CPF regular" : "CPF com pendências na Receita Federal";
            log.debug("CPF {} validation: isValid={}, isRegular={}", cpf, isValid, isRegular);
            return new CpfValidationResponse(isValid, isRegular, message);
        }, virtualThreadExecutor);
    }

    public CompletableFuture<AccountValidationResponse> checkAccountActive(String cpf) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread (checkAccountActive for {}): {}", cpf, Thread.currentThread());
            try {
                Thread.sleep(random.nextInt(150));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new AccountValidationResponse(false, "Validação de conta interrompida");
            }

            // Simulação: CPFs terminados em 9 não têm conta ativa
            boolean isActive = !cpf.endsWith("9");
            log.debug("CPF {} account active check: isActive={}", cpf, isActive);
            return new AccountValidationResponse(isActive, isActive ? "Conta ativa" : "Cliente não possui conta ativa");
        }, virtualThreadExecutor);
    }

    public CompletableFuture<InternalRestrictResponse> checkInternalRestrictions(String cpf) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread (checkInternalRestrictions for {}): {}", cpf, Thread.currentThread());
            try {
                Thread.sleep(random.nextInt(100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new InternalRestrictResponse(true, "Validação de restrições interrompida");
            }

            // Simulação: CPFs terminados em 88 têm restrições
            boolean hasRestriction = cpf.endsWith("88");
            log.debug("CPF {} internal restriction check: hasRestriction={}", cpf, hasRestriction);
            return new InternalRestrictResponse(hasRestriction, hasRestriction ? "Cliente possui restrições internas graves" : "Sem restrições internas graves");
        }, virtualThreadExecutor);
    }
}
