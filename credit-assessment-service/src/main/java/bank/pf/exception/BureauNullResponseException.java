package bank.pf.exception;

public class BureauNullResponseException extends ServiceException {
    
    public BureauNullResponseException(String cpf) {
        super("Bureau service returned null response for CPF: " + cpf);
    }
}