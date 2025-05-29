package bank.pf.service.chain;

import bank.pf.dto.event.LoanApplicationReceivedEvent;
import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import bank.pf.enums.AssessmentStatus;
import bank.pf.enums.PaymentHistoryType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class PaymentHistoryRule extends AbstractAssessmentRule {

    @Override
    public void evaluate(LoanApplicationReceivedEvent application, BureauScore bureauScore, AntiFraudScore antiFraudScore, CreditAssessmentResult currentAssessment) {
        var paymentHistory = bureauScore.paymentHistory();
        if (PaymentHistoryType.POOR_OVERDUE_60_DAYS.equals(paymentHistory)) {
            currentAssessment.setStatus(AssessmentStatus.REJECTED);
            currentAssessment.setJustification("Histórico de pagamento com atrasos significativos. ");
        }

        evaluateNext(application, bureauScore, antiFraudScore, currentAssessment);
    }
}
