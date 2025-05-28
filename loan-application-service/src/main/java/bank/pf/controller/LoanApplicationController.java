package bank.pf.controller;

import bank.pf.dto.LoanApplicationRequest;
import bank.pf.dto.UpdateLoanStatusRequest;
import bank.pf.exception.ApplicationNotFoundException;
import bank.pf.exception.ValidationException;
import bank.pf.service.LoanApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    @PostMapping
    public ResponseEntity<?> submitLoanApplication(
            @Valid @RequestBody LoanApplicationRequest request, @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Recebida solicitação de empréstimo para CPF: {}", request.cpf());
        log.info("Thread (controller for CPF {}): {}", request.cpf(), Thread.currentThread());

        try {
            String applicationId = loanApplicationService.submitApplication(request);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("applicationId", applicationId, "message", "Solicitação recebida e em processamento."));
        } catch (ValidationException e) {
            log.warn("Falha na validação da solicitação para CPF {}: {}", request.cpf(), e.getErrors());
            return ResponseEntity.badRequest().body(Map.of("errors", e.getErrors()));
        } catch (ExecutionException | InterruptedException e) {
            log.error("Erro de execução concorrente ou interrupção ao processar solicitação para CPF {}: {}", request.cpf(), e.getMessage());
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erro interno ao processar a solicitação."));
        } catch (Exception e) {
            log.error("Erro inesperado ao processar solicitação para CPF {}: {}", request.cpf(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erro inesperado."));
        }
    }

    // Endpoint interno para ser chamado pelo loan-decision-engine
    @PutMapping("/internal/{applicationId}/status")
    public ResponseEntity<?> updateLoanStatus(@PathVariable String applicationId, @RequestBody UpdateLoanStatusRequest statusRequest) {
        log.info("Recebida atualização de status para applicationId {}: {}", applicationId, statusRequest.status());
        try {
            loanApplicationService.updateLoanStatus(statusRequest);
            return ResponseEntity.ok(Map.of("message", "Status da solicitação " + applicationId + " atualizado para " + statusRequest.status()));
        } catch (ApplicationNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erro ao atualizar status da solicitação {}: {}", applicationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erro interno ao atualizar status."));
        }
    }
}
