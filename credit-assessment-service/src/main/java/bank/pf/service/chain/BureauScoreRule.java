package bank.pf.service.chain;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import bank.pf.enums.AssessmentStatus;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class BureauScoreRule extends AbstractAssessmentRule {

    @Override
    public void evaluate(LoanApplicationReceivedEvent application, BureauScore bureauScore, AntiFraudScore antiFraudScore, CreditAssessmentResult currentAssessment) {
        if (bureauScore.score() < 300) {
            currentAssessment.setStatus(AssessmentStatus.REJECTED);
            currentAssessment.setJustification("Score de crédito abaixo do mínimo (Score: " + bureauScore.score() + "). ");
        }

        evaluateNext(application, bureauScore, antiFraudScore, currentAssessment);
    }
}
