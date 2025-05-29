package bank.pf.exception;


public class BureauNotFoundException extends ServiceException {
    
    public BureauNotFoundException(String cpf) {
        super("Bureau score not found for CPF: " + cpf);
    }
    
    public BureauNotFoundException(String cpf, Throwable cause) {
        super("Bureau score not found for CPF: " + cpf, cause);
    }
}