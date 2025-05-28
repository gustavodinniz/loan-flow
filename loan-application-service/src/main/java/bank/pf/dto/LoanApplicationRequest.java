package bank.pf.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanApplicationRequest (
        @NotBlank(message = "CPF é obrigatório")
        @Pattern(regexp = "^\\d{11}$", message = "CPF deve conter 11 dígitos numéricos")
        String cpf,

        @NotNull(message = "Data de nascimento é obrigatória")
        @Past(message = "Data de nascimento deve ser no passado")
        LocalDate dateOfBirth,

        @NotNull(message = "Valor do empréstimo é obrigatório")
        @DecimalMin(value = "500.00", message = "Valor mínimo do empréstimo é R$ 500,00")
        @DecimalMax(value = "100000.00", message = "Valor máximo do empréstimo é R$ 100.000,00")
        BigDecimal amountRequested,

        @NotNull(message = "Número de parcelas é obrigatório")
        @Min(value = 3, message = "Número mínimo de parcelas é 3")
        @Max(value = 48, message = "Número máximo de parcelas é 48")
        Integer numberOfInstallments,

        @NotNull(message = "Renda mensal é obrigatória")
        @DecimalMin(value = "0.01", message = "Renda mensal deve ser positiva")
        BigDecimal monthlyIncome
) {
}
