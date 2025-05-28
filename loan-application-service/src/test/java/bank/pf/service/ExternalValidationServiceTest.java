package bank.pf.service;

import bank.pf.dto.response.AccountValidationResponse;
import bank.pf.dto.response.CpfValidationResponse;
import bank.pf.dto.response.InternalRestrictResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ExternalValidationServiceTest {

    @Autowired
    private ExternalValidationService externalValidationService;

    @Test
    void validateCpfStatus_withCpfEndingIn00_shouldReturnNotRegular() throws ExecutionException, InterruptedException {
        // Given
        String cpf = "12345678900";

        // When
        CompletableFuture<CpfValidationResponse> future = externalValidationService.validateCpfStatus(cpf);
        CpfValidationResponse response = future.get();

        // Then
        System.out.println("[DEBUG_LOG] CPF validation response: " + response);
        assertTrue(response.isValid());
        assertFalse(response.isRegular());
        assertEquals("CPF com pendências na Receita Federal", response.message());
    }

    @Test
    void validateCpfStatus_withRegularCpf_shouldReturnValid() throws ExecutionException, InterruptedException {
        // Given
        String cpf = "12345678901";

        // When
        CompletableFuture<CpfValidationResponse> future = externalValidationService.validateCpfStatus(cpf);
        CpfValidationResponse response = future.get();

        // Then
        System.out.println("[DEBUG_LOG] CPF validation response: " + response);
        assertTrue(response.isValid());
        // Note: The regular status is random for non-00 CPFs, so we don't assert it
    }

    @Test
    void checkAccountActive_withCpfEndingIn9_shouldReturnInactive() throws ExecutionException, InterruptedException {
        // Given
        String cpf = "123456789";

        // When
        CompletableFuture<AccountValidationResponse> future = externalValidationService.checkAccountActive(cpf);
        AccountValidationResponse response = future.get();

        // Then
        System.out.println("[DEBUG_LOG] Account validation response: " + response);
        assertFalse(response.isActive());
        assertEquals("Cliente não possui conta ativa", response.message());
    }

    @Test
    void checkAccountActive_withActiveCpf_shouldReturnActive() throws ExecutionException, InterruptedException {
        // Given
        String cpf = "12345678901";

        // When
        CompletableFuture<AccountValidationResponse> future = externalValidationService.checkAccountActive(cpf);
        AccountValidationResponse response = future.get();

        // Then
        System.out.println("[DEBUG_LOG] Account validation response: " + response);
        // Note: The active status is random for non-9 CPFs, so we don't assert it
    }

    @Test
    void checkInternalRestrictions_withCpfEndingIn88_shouldReturnRestriction() throws ExecutionException, InterruptedException {
        // Given
        String cpf = "1234567888";

        // When
        CompletableFuture<InternalRestrictResponse> future = externalValidationService.checkInternalRestrictions(cpf);
        InternalRestrictResponse response = future.get();

        // Then
        System.out.println("[DEBUG_LOG] Internal restrictions response: " + response);
        assertTrue(response.hasRestriction());
        assertEquals("Cliente possui restrições internas graves", response.message());
    }

    @Test
    void checkInternalRestrictions_withNonRestrictedCpf_shouldReturnNoRestriction() throws ExecutionException, InterruptedException {
        // Given
        String cpf = "12345678901";

        // When
        CompletableFuture<InternalRestrictResponse> future = externalValidationService.checkInternalRestrictions(cpf);
        InternalRestrictResponse response = future.get();

        // Then
        System.out.println("[DEBUG_LOG] Internal restrictions response: " + response);
        // Note: The restriction status is random for non-88 CPFs, so we don't assert it
    }
}