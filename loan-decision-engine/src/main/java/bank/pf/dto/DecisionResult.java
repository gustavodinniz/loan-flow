package bank.pf.dto;

import bank.pf.enums.LoanDecision;

public record DecisionResult(LoanDecision decision, LoanTerms terms, String reason) {
}
