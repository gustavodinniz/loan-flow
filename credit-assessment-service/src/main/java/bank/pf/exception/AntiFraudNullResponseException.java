package bank.pf.exception;

public class AntiFraudNullResponseException extends ServiceException {
    
    public AntiFraudNullResponseException(String applicationId) {
        super("Anti-fraud service returned null response for application ID: " + applicationId);
    }
}