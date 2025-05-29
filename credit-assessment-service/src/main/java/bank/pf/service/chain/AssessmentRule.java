package bank.pf.service.chain;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;

public interface AssessmentRule {

    void setNextRule(AssessmentRule nextRule);


    void evaluate(LoanApplicationReceivedEvent application, BureauScore bureauScore, AntiFraudScore antiFraudScore, CreditAssessmentResult currentAssessment);
}
