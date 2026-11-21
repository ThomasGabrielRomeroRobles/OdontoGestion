package ar.edu.odontogestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class BootstrapPropertiesTest {

    @Test
    void elEmailPorDefectoEsElDelOdontologoDemo() {
        BootstrapProperties props = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("app.bootstrap", BootstrapProperties.class);

        assertThat(props.odontologoEmail()).isEqualTo("odontologo@consultorio.example");
        assertThat(props.odontologoPassword()).isEmpty();
    }

    @Test
    void losValoresConfiguradosSeEnlazan() {
        BootstrapProperties props = new Binder(new MapConfigurationPropertySource(Map.of(
                "app.bootstrap.odontologo-email", "otra@ejemplo.test",
                "app.bootstrap.odontologo-password", "Ficticia-Otra-1")))
                .bindOrCreate("app.bootstrap", BootstrapProperties.class);

        assertThat(props.odontologoEmail()).isEqualTo("otra@ejemplo.test");
        assertThat(props.odontologoPassword()).isEqualTo("Ficticia-Otra-1");
    }

    @Test
    void toStringNoContieneLaContrasena() {
        BootstrapProperties props = new BootstrapProperties("a@ejemplo.test", "Ficticia-Secreta-9");

        assertThat(props.toString()).doesNotContain("Ficticia-Secreta-9").contains("a@ejemplo.test");
    }

    @Test
    void toStringTampocoLaImprimeSiEstaVacia() {
        assertThat(new BootstrapProperties("a@ejemplo.test", "").toString()).contains("a@ejemplo.test");
    }
}
