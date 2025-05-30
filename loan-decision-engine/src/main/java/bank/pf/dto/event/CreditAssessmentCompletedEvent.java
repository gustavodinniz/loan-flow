package bank.pf.dto.event;

import bank.pf.enums.AssessmentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record CreditAssessmentCompletedEvent(
        String eventId,
        LocalDateTime eventTimestamp,
        String applicationId,
        String cpf,
        String email,
        AssessmentStatus finalAssessmentStatus,
        String justification,
        Integer creditScoreUsed,
        Integer antiFraudScoreUsed,
        BigDecimal approvedLimit,
        BigDecimal interestRateApplied
) {

}
