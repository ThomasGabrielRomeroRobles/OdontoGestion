package ar.edu.odontogestion.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Limitación de intentos de login (RN-AU-09): umbral, espera inicial y máxima, y tope de claves en memoria. */
@Validated
@ConfigurationProperties("app.security.login")
public record LoginProperties(
        @Min(1) @DefaultValue("5") int maxIntentos,
        @NotNull @DefaultValue("60s") Duration esperaInicial,
        @NotNull @DefaultValue("900s") Duration esperaMaxima,
        @Min(1) @DefaultValue("10000") int maxClaves) {
}
