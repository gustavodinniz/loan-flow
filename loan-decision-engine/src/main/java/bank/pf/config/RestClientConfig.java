package bank.pf.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Configuration
public class RestClientConfig {

    @Value("${app.loan-application-service.url}")
    private String loanApplicationServiceBaseUrl;

    @Value("${app.loan-application-service.username}")
    private String serviceUsername;

    @Value("${app.loan-application-service.password}")
    private String servicePassword;

    @Bean
    public RestClient loanApplicationRestClient() {
        String authCredentials = serviceUsername + ":" + servicePassword;
        String encodedAuthCredentials = Base64.getEncoder().encodeToString(authCredentials.getBytes());

        return RestClient.builder()
                .baseUrl(loanApplicationServiceBaseUrl)
                .defaultHeader("Authorization", "Basic " + encodedAuthCredentials)
                .build();
    }
}
