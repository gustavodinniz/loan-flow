package bank.pf.dto.event;

import bank.pf.entity.AntiFraudScore;
import bank.pf.entity.BureauScore;
import bank.pf.entity.CreditAssessmentResult;
import bank.pf.enums.AssessmentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record CreditAssessmentCompletedEvent(
        String eventId,
        LocalDateTime eventTimestamp,
        String applicationId,
        String cpf,
        AssessmentStatus finalAssessmentStatus,
        String justification,
        Integer creditScoreUsed,
        Integer antiFraudScoreUsed,
        BigDecimal approvedLimit,
        BigDecimal interestRateApplied
) {

    public static CreditAssessmentCompletedEvent valueOf(CreditAssessmentResult creditAssessmentResult) {
        return CreditAssessmentCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventTimestamp(LocalDateTime.now())
                .applicationId(creditAssessmentResult.getApplicationId())
                .cpf(creditAssessmentResult.getCpf())
                .finalAssessmentStatus(creditAssessmentResult.getStatus())
                .justification(creditAssessmentResult.getJustification())
                .approvedLimit(creditAssessmentResult.getRecommendedLimit())
                .interestRateApplied(creditAssessmentResult.getRecommendedInterestRate())
                .build();
    }

    public static CreditAssessmentCompletedEvent valueOf(CreditAssessmentResult result, BureauScore bureauScore, AntiFraudScore antiFraudScore) {
        return CreditAssessmentCompletedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventTimestamp(java.time.LocalDateTime.now())
                .applicationId(result.getApplicationId())
                .cpf(result.getCpf())
                .finalAssessmentStatus(result.getStatus())
                .justification(result.getJustification())
                .creditScoreUsed(bureauScore.score())
                .antiFraudScoreUsed(antiFraudScore.fraudScore())
                .approvedLimit(result.getRecommendedLimit())
                .interestRateApplied(result.getRecommendedInterestRate())
                .build();
    }
}
