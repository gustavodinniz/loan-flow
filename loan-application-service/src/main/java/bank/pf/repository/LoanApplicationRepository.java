package bank.pf.repository;

import bank.pf.entity.LoanApplication;
import bank.pf.enums.LoanStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LoanApplicationRepository extends MongoRepository<LoanApplication, String> {

    Optional<LoanApplication> findByCpfAndStatusIn(String cpf, List<LoanStatus> statuses);
}
