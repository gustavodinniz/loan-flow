package bank.pf.dto.response;

public record AccountValidationResponse(
        boolean isActive,
        String message
) {
}
