package ar.edu.odontogestion.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Datos del odontólogo inicial (prefijo {@code app.bootstrap}). La contraseña viene solo del entorno
 * ({@code BOOTSTRAP_ODONTOLOGO_PASSWORD}); {@code toString()} la enmascara para que no llegue a logs.
 */
@ConfigurationProperties("app.bootstrap")
public record BootstrapProperties(
        @DefaultValue("odontologo@consultorio.example") String odontologoEmail,
        @DefaultValue("") String odontologoPassword) {

    @Override
    public String toString() {
        return "BootstrapProperties[odontologoEmail=" + odontologoEmail + ", odontologoPassword=****]";
    }
}
