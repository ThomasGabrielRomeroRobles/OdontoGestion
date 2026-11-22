package ar.edu.odontogestion.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Procesamiento del outbox de notificaciones (C-03, design D10/D12).
 *
 * @param pollInterval cada cuánto corre el procesamiento periódico ({@code NOTIFICATION_POLL_INTERVAL})
 * @param maxIntentos intentos totales antes de dejar la notificación {@code FALLIDA}
 * @param esperas espera tras cada intento fallido, en orden; si hay menos que intentos se repite la última
 * @param lote máximo de notificaciones tomadas por ejecución
 */
@Validated
@ConfigurationProperties("app.notifications")
public record NotificationProperties(
        @NotNull @DefaultValue("PT5M") Duration pollInterval,
        @Min(1) @DefaultValue("3") int maxIntentos,
        @NotEmpty @DefaultValue({"PT1M", "PT5M"}) List<@NotNull Duration> esperas,
        @Min(1) @DefaultValue("50") int lote) {

    public NotificationProperties {
        esperas = esperas == null ? List.of() : List.copyOf(esperas);
    }
}
