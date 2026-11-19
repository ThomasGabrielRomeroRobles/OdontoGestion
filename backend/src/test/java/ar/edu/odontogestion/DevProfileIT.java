package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.infrastructure.config.AppProperties;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("dev")
class DevProfileIT extends AbstractIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private AppProperties appProperties;

    @Test
    void perfilDevActivoYArrancaConExito() {
        assertThat(environment.getActiveProfiles()).containsExactly("dev");
    }

    @Test
    void hibernateSoloValidaEnDev() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void devAumentaElDetalleDeLogDeLaAplicacion() {
        assertThat(environment.getProperty("logging.level.ar.edu.odontogestion")).isEqualTo("DEBUG");
    }

    @Test
    void parametrosNoSensiblesTienenValorPorDefectoSinVariablesDeEntorno() {
        assertThat(appProperties.timezone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
        assertThat(appProperties.baseUrl()).isEqualTo("http://localhost:8080");
        assertThat(appProperties.reminderHoursBefore()).isEqualTo(24);
    }
}
