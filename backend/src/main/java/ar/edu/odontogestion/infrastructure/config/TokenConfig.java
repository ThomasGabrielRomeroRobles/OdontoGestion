package ar.edu.odontogestion.infrastructure.config;

import ar.edu.odontogestion.application.token.GeneradorTokens;
import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TokenConfig {

    @Bean
    public GeneradorTokens generadorTokens() {
        return new GeneradorTokens(new SecureRandom());
    }
}
