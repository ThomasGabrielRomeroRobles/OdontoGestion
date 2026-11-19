package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("demo")
class DemoProfileIT extends AbstractIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    void perfilDemoActivoYArrancaConExito() {
        assertThat(environment.getActiveProfiles()).containsExactly("demo");
    }

    @Test
    void hibernateSoloValidaEnDemo() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void demoSirveElFrontendConCacheDeRecursosEstaticos() {
        assertThat(environment.getProperty("spring.web.resources.cache.period")).isEqualTo("1h");
    }
}
