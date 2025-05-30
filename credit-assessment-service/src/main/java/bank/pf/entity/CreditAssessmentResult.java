package bank.pf.entity;

import bank.pf.enums.AssessmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAssessmentResult {

    private String applicationId;
    private String cpf;
    private String email;
    private AssessmentStatus status;
    private String justification;
    private int finalScore;
    private BigDecimal recommendedLimit;
    private BigDecimal recommendedInterestRate;
}
