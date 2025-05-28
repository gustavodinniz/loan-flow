package bank.pf.dto.response;

public record InternalRestrictResponse(
        boolean hasRestriction,
        String message
) {
}
