package bank.pf.entity;

import bank.pf.enums.AssessmentType;
import bank.pf.enums.PaymentHistoryType;

import java.math.BigDecimal;

public record BureauScore(
        String cpf,
        Integer score,
        AssessmentType assessment,
        boolean hasRestrictions,
        PaymentHistoryType paymentHistory,
        BigDecimal monthlyDebts
) {
}
