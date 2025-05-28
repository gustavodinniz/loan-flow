package bank.pf.dto.response;

public record CpfValidationResponse(
        boolean isValid,
        boolean isRegular,
        String message
) {
}
