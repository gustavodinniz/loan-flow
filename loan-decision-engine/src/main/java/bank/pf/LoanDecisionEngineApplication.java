package bank.pf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class LoanDecisionEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoanDecisionEngineApplication.class, args);
	}

}
