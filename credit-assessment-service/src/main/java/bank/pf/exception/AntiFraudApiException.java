package bank.pf.exception;

public class AntiFraudApiException extends ServiceException {
    
    public AntiFraudApiException(String applicationId, Throwable cause) {
        super("Error during anti-fraud check for application ID: " + applicationId, cause);
    }
}