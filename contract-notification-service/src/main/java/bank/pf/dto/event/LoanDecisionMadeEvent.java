package bank.pf.dto.event;


import bank.pf.dto.LoanTerms;
import bank.pf.enums.LoanDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDecisionMadeEvent {
    private String eventId;
    private LocalDateTime eventTimestamp;
    private String applicationId;
    private String cpf;
    private String email;
    private LoanDecision decision;
    private String reason;
    private LoanTerms terms;

}
