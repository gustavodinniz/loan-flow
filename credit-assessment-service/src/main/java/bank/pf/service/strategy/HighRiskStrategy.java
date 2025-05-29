package bank.pf.service.strategy;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import bank.pf.enums.AssessmentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component("highRiskStrategy")
public class HighRiskStrategy implements CreditRiskStrategy {

    private static final BigDecimal MAX_LOAN_AMOUNT_CAP = new BigDecimal("1000000.00");
    private static final BigDecimal INCOME_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal INTEREST_RATE = new BigDecimal("0.18");
    private static final BigDecimal MIN_ACCEPTABLE_LOAN_AMOUNT_RATIO = new BigDecimal("0.5");

    @Override
    public void assessRisk(LoanApplicationReceivedEvent applicationData, BureauScore bureauScore, CreditAssessmentResult ongoingAssessment) {
        ongoingAssessment.setJustification((ongoingAssessment.getJustification() == null ? "" : ongoingAssessment.getJustification()) + "High risk profile identified, conditions adjusted.");
        ongoingAssessment.setStatus(AssessmentStatus.ADJUSTED_CONDITIONS);

        var incomeBasedLimit = applicationData.monthlyIncome().multiply(INCOME_MULTIPLIER);
        var recommendedLimit = incomeBasedLimit.min(MAX_LOAN_AMOUNT_CAP);
        recommendedLimit = recommendedLimit.min(applicationData.amountRequested());

        BigDecimal minimumOffer = applicationData.amountRequested().multiply(MIN_ACCEPTABLE_LOAN_AMOUNT_RATIO);
        if (recommendedLimit.compareTo(minimumOffer) < 0) {
            ongoingAssessment.setStatus(AssessmentStatus.REJECTED);
            ongoingAssessment.setJustification(ongoingAssessment.getJustification() + "Calculated limit too low for high-risk profile.");
            ongoingAssessment.setRecommendedLimit(BigDecimal.ZERO); // Sem recomendação de limite neste caso
            ongoingAssessment.setRecommendedInterestRate(BigDecimal.ZERO);
        } else {
            ongoingAssessment.setRecommendedLimit(recommendedLimit.setScale(2, RoundingMode.HALF_EVEN));
            ongoingAssessment.setRecommendedInterestRate(INTEREST_RATE.setScale(4, RoundingMode.HALF_EVEN));
            if (recommendedLimit.compareTo(applicationData.amountRequested()) < 0) {
                ongoingAssessment.setJustification(ongoingAssessment.getJustification() + "Recommended limit significantly adjusted. ");
            }
        }
    }

    @Override
    public boolean appliesTo(int score) {
        return score >= 300 && score < 501;
    }
}
