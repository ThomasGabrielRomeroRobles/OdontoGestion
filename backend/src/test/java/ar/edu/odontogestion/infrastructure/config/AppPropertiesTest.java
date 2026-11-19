package ar.edu.odontogestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void sinConfiguracionUsaValoresPorDefectoNoSensibles() {
        runner.run(ctx -> {
            AppProperties props = ctx.getBean(AppProperties.class);
            assertThat(props.timezone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
            assertThat(props.baseUrl()).isEqualTo("http://localhost:8080");
            assertThat(props.reminderHoursBefore()).isEqualTo(24);
            assertThat(props.pendingRequestTtlMin()).isEqualTo(30);
            assertThat(props.publicRateLimitPerHour()).isEqualTo(5);
            assertThat(props.notificationChannels()).containsExactly("EMAIL");
            assertThat(props.mailFrom()).isEqualTo("no-reply@consultorio.example");
        });
    }

    @Test
    void valoresDelEntornoSobrescribenLosPorDefecto() {
        runner.withPropertyValues(
                        "app.base-url=http://localhost:9999",
                        "app.timezone=UTC",
                        "app.reminder-hours-before=48",
                        "app.notification-channels=EMAIL,WHATSAPP")
                .run(ctx -> {
                    AppProperties props = ctx.getBean(AppProperties.class);
                    assertThat(props.baseUrl()).isEqualTo("http://localhost:9999");
                    assertThat(props.timezone()).isEqualTo(ZoneId.of("UTC"));
                    assertThat(props.reminderHoursBefore()).isEqualTo(48);
                    assertThat(props.notificationChannels()).isEqualTo(List.of("EMAIL", "WHATSAPP"));
                });
    }

    @Test
    void horasNegativasSonRechazadasPorValidacion() {
        runner.withPropertyValues("app.reminder-hours-before=-1")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void ttlCeroEsRechazadoPorValidacion() {
        runner.withPropertyValues("app.pending-request-ttl-min=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void urlBaseVaciaEsRechazada() {
        runner.withPropertyValues("app.base-url=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
