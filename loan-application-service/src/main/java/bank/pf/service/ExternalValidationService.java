package bank.pf.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

record CpfValidationResult(boolean isValid, boolean isRegular, String message) {
}

record AccountValidationResult(boolean isActive, String message) {
}

record InternalRestrictionResult(boolean hasRestriction, String message) {
}

@Slf4j
@Service
public class ExternalValidationService {

    private final Random random = new Random();

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public CompletableFuture<CpfValidationResult> validateCpfStatus(String cpf) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread (validateCpfStatus for {}): {}", cpf, Thread.currentThread());
            try {
                Thread.sleep(random.nextInt(200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new CpfValidationResult(false, false, "Validação de CPF interrompida");
            }

            boolean isRegular = !cpf.endsWith("00");
            boolean isValid = true;
            String message = isRegular ? "CPF regular" : "CPF com pendências na Receita Federal";
            log.debug("CPF {} validation: isValid={}, isRegular={}", cpf, isValid, isRegular);
            return new CpfValidationResult(isValid, isRegular, message);
        }, virtualThreadExecutor);
    }

    public CompletableFuture<AccountValidationResult> checkAccountActive(String cpf) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread (checkAccountActive for {}): {}", cpf, Thread.currentThread());
            try {
                Thread.sleep(random.nextInt(150));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new AccountValidationResult(false, "Validação de conta interrompida");
            }

            // Simulação: CPFs terminados em 9 não têm conta ativa
            boolean isActive = !cpf.endsWith("9");
            log.debug("CPF {} account active check: isActive={}", cpf, isActive);
            return new AccountValidationResult(isActive, isActive ? "Conta ativa" : "Cliente não possui conta ativa");
        }, virtualThreadExecutor);
    }

    public CompletableFuture<InternalRestrictionResult> checkInternalRestrictions(String cpf) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Thread (checkInternalRestrictions for {}): {}", cpf, Thread.currentThread());
            try {
                Thread.sleep(random.nextInt(100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new InternalRestrictionResult(true, "Validação de restrições interrompida");
            }

            // Simulação: CPFs terminados em 88 têm restrições
            boolean hasRestriction = cpf.endsWith("88");
            log.debug("CPF {} internal restriction check: hasRestriction={}", cpf, hasRestriction);
            return new InternalRestrictionResult(hasRestriction, hasRestriction ? "Cliente possui restrições internas graves" : "Sem restrições internas graves");
        }, virtualThreadExecutor);
    }
}
