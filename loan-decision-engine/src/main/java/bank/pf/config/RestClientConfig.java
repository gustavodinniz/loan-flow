package bank.pf.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${app.loan-application-service.url}")
    private String loanApplicationServiceBaseUrl;

    @Bean
    public RestClient loanApplicationRestClient() {
        return RestClient.builder()
                .baseUrl(loanApplicationServiceBaseUrl)
                .build();
    }
}
