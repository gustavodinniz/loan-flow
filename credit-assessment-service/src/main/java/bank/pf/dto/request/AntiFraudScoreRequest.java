package bank.pf.dto.request;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record AntiFraudScoreRequest(
        String applicationId,
        String cpf,
        LocalDate dateOfBirth,
        BigDecimal amountRequested,
        Integer numberOfInstallments,
        BigDecimal monthlyIncome
) {

    public static AntiFraudScoreRequest valueOf(LoanApplicationReceivedEvent applicationData) {
        return AntiFraudScoreRequest.builder()
                .applicationId(applicationData.applicationId())
                .cpf(applicationData.cpf())
                .amountRequested(applicationData.amountRequested())
                .dateOfBirth(applicationData.dateOfBirth())
                .numberOfInstallments(applicationData.numberOfInstallments())
                .monthlyIncome(applicationData.monthlyIncome())
                .build();
    }
}
