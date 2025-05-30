package bank.pf.dto.event;

import bank.pf.entity.LoanApplication;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Builder
public record LoanApplicationReceivedEvent(
        String applicationId,
        String cpf,
        String email,
        LocalDate dateOfBirth,
        BigDecimal amountRequested,
        Integer numberOfInstallments,
        BigDecimal monthlyIncome,
        Instant eventTimestamp) {


    public static LoanApplicationReceivedEvent valueOf(LoanApplication savedLoan) {
        return LoanApplicationReceivedEvent.builder()
                .applicationId(savedLoan.getId())
                .cpf(savedLoan.getCpf())
                .email(savedLoan.getEmail())
                .dateOfBirth(savedLoan.getDateOfBirth())
                .amountRequested(savedLoan.getAmountRequested())
                .numberOfInstallments(savedLoan.getNumberOfInstallments())
                .monthlyIncome(savedLoan.getMonthlyIncome())
                .eventTimestamp(Instant.now())
                .build();
    }
}
