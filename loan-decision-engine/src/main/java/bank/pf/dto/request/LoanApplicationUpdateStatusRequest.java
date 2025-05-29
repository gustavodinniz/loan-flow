package bank.pf.dto.request;

import bank.pf.enums.LoanDecision;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record LoanApplicationUpdateStatusRequest(
        String applicationId,
        LoanDecision status,
        String reason,
        BigDecimal amountApproved,
        BigDecimal interestRate,
        Integer installments,
        BigDecimal installmentValue
) {
}
