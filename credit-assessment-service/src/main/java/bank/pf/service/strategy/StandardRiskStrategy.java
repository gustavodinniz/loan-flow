package bank.pf.service.strategy;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component("standardRiskStrategy")
public class StandardRiskStrategy implements CreditRiskStrategy {

    private static final BigDecimal MAX_LOAN_AMOUNT_CAP = new BigDecimal("5000000.00");
    private static final BigDecimal INCOME_MULTIPLIER = new BigDecimal("2.5"); // Multiplicador menor para risco padrão
    private static final BigDecimal INTEREST_RATE = new BigDecimal("0.12"); // Taxa anual para risco padrão (ex: 12%)

    @Override
    public void assessRisk(LoanApplicationReceivedEvent applicationData, BureauScore bureauScore, CreditAssessmentResult ongoingAssessment) {
        ongoingAssessment.setJustification((ongoingAssessment.getJustification() == null ? "" : ongoingAssessment.getJustification()) + "Standard risk profile identified. ");

        var incomeBasedLimit = applicationData.monthlyIncome().multiply(INCOME_MULTIPLIER);
        var recommendedLimit = incomeBasedLimit.min(MAX_LOAN_AMOUNT_CAP);
        recommendedLimit = recommendedLimit.min(applicationData.amountRequested());

        ongoingAssessment.setRecommendedLimit(recommendedLimit.setScale(2, RoundingMode.HALF_EVEN));
        ongoingAssessment.setRecommendedInterestRate(INTEREST_RATE.setScale(4, RoundingMode.HALF_EVEN));

        if (recommendedLimit.compareTo(applicationData.amountRequested()) < 0) {
            ongoingAssessment.setJustification(ongoingAssessment.getJustification() + "Recommended limit adjusted due to income/cap/risk profile. ");
        }
    }

    @Override
    public boolean appliesTo(int score) {
        return score >= 501 && score < 700;
    }
}
