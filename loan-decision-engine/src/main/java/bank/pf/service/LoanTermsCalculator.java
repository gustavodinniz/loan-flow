package bank.pf.service;

import bank.pf.dto.LoanTerms;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
public class LoanTermsCalculator {

    private static final int DEFAULT_NUMBER_OF_INSTALLMENTS_SHORT_TERM = 12;
    private static final int DEFAULT_NUMBER_OF_INSTALLMENTS_MEDIUM_TERM = 24;
    private static final int DEFAULT_NUMBER_OF_INSTALLMENTS_LONG_TERM = 36;
    private static final BigDecimal MEDIUM_TERM_THRESHOLD_AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal LONG_TERM_THRESHOLD_AMOUNT = new BigDecimal("25000.00");

    public LoanTerms calculateDefaultLoanTerms(BigDecimal approvedAmount, BigDecimal annualInterestRate) {
        int numberOfInstallments = determineNumberOfInstallments(approvedAmount);
        BigDecimal installmentAmount;

        if (annualInterestRate.compareTo(BigDecimal.ZERO) == 0) {
            installmentAmount = calculateZeroInterestInstallmentAmount(approvedAmount, numberOfInstallments);
        } else {
            installmentAmount = calculateInterestBasedInstallmentAmount(approvedAmount, annualInterestRate, numberOfInstallments);
        }

        return createLoanTerms(approvedAmount, annualInterestRate, numberOfInstallments, installmentAmount);
    }

    private int determineNumberOfInstallments(BigDecimal approvedAmount) {
        if (approvedAmount.compareTo(LONG_TERM_THRESHOLD_AMOUNT) >= 0) {
            return DEFAULT_NUMBER_OF_INSTALLMENTS_LONG_TERM;
        } else if (approvedAmount.compareTo(MEDIUM_TERM_THRESHOLD_AMOUNT) >= 0) {
            return DEFAULT_NUMBER_OF_INSTALLMENTS_MEDIUM_TERM;
        } else {
            return DEFAULT_NUMBER_OF_INSTALLMENTS_SHORT_TERM;
        }
    }

    private BigDecimal calculateZeroInterestInstallmentAmount(BigDecimal approvedAmount, int numberOfInstallments) {
        return approvedAmount.divide(new BigDecimal(numberOfInstallments), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateInterestBasedInstallmentAmount(BigDecimal approvedAmount, BigDecimal annualInterestRate, int numberOfInstallments) {
        BigDecimal monthlyInterestRate = convertToMonthlyRate(annualInterestRate);
        BigDecimal onePlusIMonthly = BigDecimal.ONE.add(monthlyInterestRate);
        BigDecimal onePlusIMonthlyToN = onePlusIMonthly.pow(numberOfInstallments);

        BigDecimal numerator = monthlyInterestRate.multiply(onePlusIMonthlyToN);
        BigDecimal denominator = onePlusIMonthlyToN.subtract(BigDecimal.ONE);

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Denominator in installment calculation is zero for amount {}, rate {}, installments {}. Defaulting installment amount.",
                    approvedAmount, annualInterestRate, numberOfInstallments);

            return calculateZeroInterestInstallmentAmount(approvedAmount, numberOfInstallments);
        }

        return approvedAmount.multiply(numerator).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal convertToMonthlyRate(BigDecimal annualInterestRate) {
        return annualInterestRate.divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
    }

    private LoanTerms createLoanTerms(BigDecimal approvedAmount, BigDecimal interestRate, int numberOfInstallments, BigDecimal installmentAmount) {
        return LoanTerms.builder()
                .approvedAmount(approvedAmount)
                .interestRate(interestRate)
                .numberOfInstallments(numberOfInstallments)
                .installmentAmount(installmentAmount)
                .build();
    }
}