package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("demo")
// Base compartida con el resto de los IT: el seed de demo (C-12) se prueba aparte, en DemoSeedIT, con base propia.
@TestPropertySource(properties = "app.seed-demo.enabled=false")
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
