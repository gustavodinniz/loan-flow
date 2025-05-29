package bank.pf.service.chain;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import bank.pf.enums.AssessmentStatus;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Order(30)
public class DebtToIncomeRatioRule extends AbstractAssessmentRule {

    private static final BigDecimal MAX_DTI_RATIO_STRICT = new BigDecimal("0.30");
    private static final BigDecimal MAX_DTI_RATIO_FLEXIBLE = new BigDecimal("0.40");

    @Override
    public void evaluate(LoanApplicationReceivedEvent application, BureauScore bureauScore, AntiFraudScore antiFraudScore, CreditAssessmentResult currentAssessment) {

        var estimatedMonthlyPayment = application.amountRequested().divide(new BigDecimal(application.numberOfInstallments()), 2, RoundingMode.HALF_UP);
        var totalMonthlyDebt = estimatedMonthlyPayment.add(bureauScore.monthlyDebts());
        var dti = totalMonthlyDebt.divide(application.monthlyIncome(), 4, RoundingMode.HALF_UP);

        var debtRatio = dti.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        if (dti.compareTo(MAX_DTI_RATIO_FLEXIBLE) > 0) {
            currentAssessment.setStatus(AssessmentStatus.REJECTED);
            currentAssessment.setJustification("Índice de endividamento (" + debtRatio + "%) acima do permitido. ");
        } else if (dti.compareTo(MAX_DTI_RATIO_STRICT) > 0) {
            currentAssessment.setStatus(AssessmentStatus.REJECTED);
            currentAssessment.setJustification((currentAssessment.getJustification() == null ? "" : currentAssessment.getJustification()) + "Índice de endividamento (" + debtRatio + "%) requer atenção. ");
        }

        evaluateNext(application, bureauScore, antiFraudScore, currentAssessment);
    }
}
