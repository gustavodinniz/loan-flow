package bank.pf.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;


public record LoanApplicationReceivedEvent(
        String applicationId,
        String cpf,
        LocalDate dateOfBirth,
        BigDecimal amountRequested,
        Integer numberOfInstallments,
        BigDecimal monthlyIncome,
        Instant eventTimestamp) {
}
