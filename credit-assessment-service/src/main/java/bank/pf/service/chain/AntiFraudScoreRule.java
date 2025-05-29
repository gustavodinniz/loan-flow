package bank.pf.service.chain;


import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import bank.pf.enums.AssessmentStatus;
import bank.pf.enums.RecommendationType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class AntiFraudScoreRule extends AbstractAssessmentRule {

    private static final int HIGH_FRAUD_SCORE_THRESHOLD = 700;

    @Override
    public void evaluate(LoanApplicationReceivedEvent application, BureauScore bureauScore, AntiFraudScore antiFraudScore, CreditAssessmentResult currentAssessment) {
        if (antiFraudScore.fraudScore() >= HIGH_FRAUD_SCORE_THRESHOLD) {
            currentAssessment.setStatus(AssessmentStatus.REJECTED);
            currentAssessment.setJustification("Score antifraude (" + antiFraudScore.fraudScore() + ") indica alto risco de fraude. Recomendação: " + antiFraudScore.recommendation());
        } else if (RecommendationType.REJECT.equals(antiFraudScore.recommendation())) {
            currentAssessment.setStatus(AssessmentStatus.REJECTED);
            currentAssessment.setJustification("Recomendação antifraude é de rejeição. Score: " + antiFraudScore.fraudScore());
        }
        evaluateNext(application, bureauScore, antiFraudScore, currentAssessment);
    }
}
