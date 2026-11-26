package ar.edu.odontogestion.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Historia clínica (C-05 design D5): ventana en la que el autor puede editar una evolución desde su alta. */
@Validated
@ConfigurationProperties("app.historia")
public record HistoriaProperties(@NotNull @DefaultValue("24h") Duration ventanaEdicionEvolucion) {
}
