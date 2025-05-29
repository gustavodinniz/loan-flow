package bank.pf.entity;

import bank.pf.enums.RecommendationType;

public record AntiFraudScore(
        String applicationId,
        Integer fraudScore,
        RecommendationType recommendation
) {
}
