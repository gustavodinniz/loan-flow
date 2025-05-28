package bank.pf.exception;

public class EventPublishingFailedException extends RuntimeException {
    public EventPublishingFailedException(String message, Exception e) {
        super(message, e);
    }
}
