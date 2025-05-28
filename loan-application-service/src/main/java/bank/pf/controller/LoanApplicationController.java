package bank.pf.controller;

import bank.pf.dto.request.LoanApplicationRequest;
import bank.pf.dto.request.UpdateLoanStatusRequest;
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
            @Valid @RequestBody LoanApplicationRequest request, @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        log.info("Recebida solicitação de empréstimo para CPF: {}", request.cpf());
        log.info("Thread (controller for CPF {}): {}", request.cpf(), Thread.currentThread());

        String applicationId = loanApplicationService.submitApplication(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("applicationId", applicationId, "message", "Solicitação recebida e em processamento."));
    }

    // Endpoint interno para ser chamado pelo loan-decision-engine
    @PutMapping("/internal/{applicationId}/status")
    public ResponseEntity<?> updateLoanStatus(@PathVariable String applicationId, @RequestBody UpdateLoanStatusRequest statusRequest) throws ApplicationNotFoundException {
        log.info("Recebida atualização de status para applicationId {}: {}", applicationId, statusRequest.status());
        loanApplicationService.updateLoanStatus(statusRequest);
        return ResponseEntity.ok(Map.of("message", "Status da solicitação " + applicationId + " atualizado para " + statusRequest.status()));
    }
}
