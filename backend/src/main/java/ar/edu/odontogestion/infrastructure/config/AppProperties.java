package ar.edu.odontogestion.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros operativos de la aplicación (prefijo {@code app}). Los demás componentes consumen este
 * record en lugar de {@code @Value} sueltos.
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank @DefaultValue("http://localhost:8080") String baseUrl,
        @NotNull @DefaultValue("America/Argentina/Buenos_Aires") ZoneId timezone,
        @Min(1) @DefaultValue("24") int reminderHoursBefore,
        @Min(1) @DefaultValue("30") int pendingRequestTtlMin,
        @Min(1) @DefaultValue("5") int publicRateLimitPerHour,
        @NotEmpty @DefaultValue("EMAIL") List<String> notificationChannels,
        @NotBlank @DefaultValue("no-reply@consultorio.example") String mailFrom,
        @NotNull @DefaultValue SeedDemo seedDemo) {

    @ConstructorBinding
    public AppProperties {
    }

    /** Sin {@code app.seed-demo.*}: seed de demo apagado (solo el perfil {@code demo} lo activa, C-12 D2). */
    public AppProperties(String baseUrl, ZoneId timezone, int reminderHoursBefore, int pendingRequestTtlMin,
            int publicRateLimitPerHour, List<String> notificationChannels, String mailFrom) {
        this(baseUrl, timezone, reminderHoursBefore, pendingRequestTtlMin, publicRateLimitPerHour,
                notificationChannels, mailFrom, new SeedDemo(false));
    }

    /**
     * Seed de datos ficticios de la demo ({@code app.seed-demo.enabled}, C-12 D2). El runner se registra por
     * {@code @ConditionalOnProperty} sobre la misma propiedad.
     */
    public record SeedDemo(@DefaultValue("false") boolean enabled) {
    }
}
