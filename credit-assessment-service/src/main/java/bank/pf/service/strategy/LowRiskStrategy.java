package bank.pf.service.strategy;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component("lowRiskStrategy")
public class LowRiskStrategy implements CreditRiskStrategy {

    private static final BigDecimal MAX_LOAN_AMOUNT_CAP = new BigDecimal("5000000.00");
    private static final BigDecimal INCOME_MULTIPLIER = new BigDecimal("4.5");
    private static final BigDecimal INTEREST_RATE = new BigDecimal("0.08");

    @Override
    public void assessRisk(LoanApplicationReceivedEvent applicationData, BureauScore bureauScore, CreditAssessmentResult ongoingAssessment) {
        ongoingAssessment.setJustification((ongoingAssessment.getJustification() == null ? "" : ongoingAssessment.getJustification()) + "Low risk profile identified. ");

        var incomeBasedLimit = applicationData.monthlyIncome().multiply(INCOME_MULTIPLIER);
        var recommendedLimit = incomeBasedLimit.min(MAX_LOAN_AMOUNT_CAP);

        // Ajusta para o valor solicitado se for menor que o limite calculado
        recommendedLimit = recommendedLimit.min(applicationData.amountRequested());

        ongoingAssessment.setRecommendedLimit(recommendedLimit.setScale(2, RoundingMode.HALF_EVEN));
        ongoingAssessment.setRecommendedInterestRate(INTEREST_RATE.setScale(4, RoundingMode.HALF_EVEN)); // Taxa com 4 casas decimais

        if (recommendedLimit.compareTo(applicationData.amountRequested()) < 0) {
            ongoingAssessment.setJustification(ongoingAssessment.getJustification() + "Recommended limit adjusted due to income/cap. ");
        }
    }

    @Override
    public boolean appliesTo(int score) {
        return score >= 700;
    }
}
