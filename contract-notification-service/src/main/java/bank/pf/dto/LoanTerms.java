package bank.pf.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanTerms {

    private BigDecimal approvedAmount;
    private BigDecimal interestRate;
    private Integer numberOfInstallments;
    private BigDecimal installmentAmount;
}
