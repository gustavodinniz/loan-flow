package bank.pf.exception;

public class BureauApiException extends ServiceException {
    
    public BureauApiException(String cpf, Throwable cause) {
        super("Error fetching bureau score for CPF: " + cpf, cause);
    }
}