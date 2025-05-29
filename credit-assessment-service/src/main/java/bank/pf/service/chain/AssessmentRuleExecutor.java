package bank.pf.service.chain;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AssessmentRuleExecutor {

    private final List<AssessmentRule> assessmentRules;
    private AssessmentRule firstRule;

    private void buildRuleChain() {
        if (this.assessmentRules == null || this.assessmentRules.isEmpty()) {
            this.firstRule = null;
            return;
        }
        this.firstRule = this.assessmentRules.getFirst();
        for (int i = 0; i < this.assessmentRules.size() - 1; i++) {
            this.assessmentRules.get(i).setNextRule(this.assessmentRules.get(i + 1));
        }

        if (!this.assessmentRules.isEmpty()) {
            this.assessmentRules.getLast().setNextRule(null);
        }
    }

    public void executeChain(LoanApplicationReceivedEvent application,
                             BureauScore bureauScore,
                             AntiFraudScore antiFraudScore,
                             CreditAssessmentResult currentAssessment) {
        if (firstRule != null) {
            firstRule.evaluate(application, bureauScore, antiFraudScore, currentAssessment);
        }
    }
}
