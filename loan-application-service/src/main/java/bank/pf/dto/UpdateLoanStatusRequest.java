package bank.pf.dto;

import bank.pf.enums.LoanStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateLoanStatusRequest(
        @NotBlank String applicationId,
        @NotNull LoanStatus status,
        @NotBlank String reason,
        @NotNull BigDecimal amountApproved,
        @NotNull BigDecimal interestRate,
        @NotNull Integer installments,
        @NotNull BigDecimal installmentValue
) {
}
