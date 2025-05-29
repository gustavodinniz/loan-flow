package bank.pf.service.strategy;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;

public interface CreditRiskStrategy {
    void assessRisk(LoanApplicationReceivedEvent applicationData, BureauScore bureauScore, CreditAssessmentResult ongoingAssessment);

    boolean appliesTo(int score);
}
