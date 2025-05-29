package bank.pf.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class WireMockConfig {

    @Value("${wiremock.server.port:8092}")
    private int port;

    @Getter
    private WireMockServer wireMockServer;

    @PostConstruct
    public void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options()
                .port(port)
                .extensions(new ResponseTemplateTransformer(true)));
        wireMockServer.start();
        log.info("WireMock server started on port {}", port);
    }

    @PreDestroy
    public void stopServer() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            log.info("WireMock server stopped");
        }
    }

    @Bean
    public String wireMockBaseUrl() {
        return "http://localhost:" + port;
    }
}