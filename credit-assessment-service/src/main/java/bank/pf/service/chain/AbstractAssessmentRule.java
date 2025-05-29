package bank.pf.service.chain;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import bank.pf.enums.AssessmentStatus;

public abstract class AbstractAssessmentRule implements AssessmentRule {

    protected AssessmentRule nextRule;

    @Override
    public void setNextRule(AssessmentRule nextRule) {
        this.nextRule = nextRule;
    }

    protected void evaluateNext(LoanApplicationReceivedEvent application, BureauScore bureauScore, AntiFraudScore antiFraudScore, CreditAssessmentResult currentAssessment) {
        if (nextRule != null && currentAssessment.getStatus() != AssessmentStatus.REJECTED) {
            nextRule.evaluate(application, bureauScore, antiFraudScore, currentAssessment);
        }
    }
}
