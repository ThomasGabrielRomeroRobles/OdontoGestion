package ar.edu.odontogestion.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AppProperties.class, BootstrapProperties.class, LoginProperties.class,
        NotificationProperties.class})
public class AppConfig {
}
