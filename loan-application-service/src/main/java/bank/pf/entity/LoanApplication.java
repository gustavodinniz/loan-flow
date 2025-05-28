package bank.pf.entity;

import bank.pf.dto.request.LoanApplicationRequest;
import bank.pf.enums.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "loan_applications")
public class LoanApplication {

    @Id
    private String id;

    @Indexed
    private String cpf;
    private LocalDate dateOfBirth;
    private BigDecimal amountRequested;
    private Integer numberOfInstallments;
    private BigDecimal monthlyIncome;

    @Indexed
    private LoanStatus status;

    private String rejectionReason;

    private BigDecimal amountApproved;
    private BigDecimal interestRate;
    private Integer approvedInstallments;
    private BigDecimal installmentValue;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public static LoanApplication valueOf(LoanApplicationRequest request) {
        return LoanApplication.builder()
                .id(UUID.randomUUID().toString())
                .cpf(request.cpf())
                .dateOfBirth(request.dateOfBirth())
                .amountRequested(request.amountRequested())
                .numberOfInstallments(request.numberOfInstallments())
                .monthlyIncome(request.monthlyIncome())
                .build();
    }
}
