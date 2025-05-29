package bank.pf.service.strategy;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import bank.pf.enums.AssessmentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component("veryRiskStrategy")
public class VeryRiskStrategy implements CreditRiskStrategy {

    @Override
    public void assessRisk(LoanApplicationReceivedEvent applicationData, BureauScore bureauScore, CreditAssessmentResult ongoingAssessment) {
        ongoingAssessment.setStatus(AssessmentStatus.REJECTED);
        ongoingAssessment.setJustification((ongoingAssessment.getJustification() == null ? "" : ongoingAssessment.getJustification()) + "Credit score too low (" + bureauScore.score() + "). Automatic rejection.");
        ongoingAssessment.setRecommendedLimit(BigDecimal.ZERO);
        ongoingAssessment.setRecommendedInterestRate(BigDecimal.ZERO);
    }

    @Override
    public boolean appliesTo(int score) {
        return score < 300;
    }
}
