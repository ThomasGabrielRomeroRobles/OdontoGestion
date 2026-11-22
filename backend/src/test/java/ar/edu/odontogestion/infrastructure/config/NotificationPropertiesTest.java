package ar.edu.odontogestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class NotificationPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(SoloNotificaciones.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationProperties.class)
    static class SoloNotificaciones {
    }

    @Test
    void valoresPorDefectoSegunElDiseno() {
        NotificationProperties props = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("app.notifications", NotificationProperties.class);

        assertThat(props.pollInterval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(props.maxIntentos()).isEqualTo(3);
        assertThat(props.esperas()).containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(5));
        assertThat(props.lote()).isEqualTo(50);
    }

    @Test
    void losValoresConfiguradosSeEnlazan() {
        NotificationProperties props = new Binder(new MapConfigurationPropertySource(Map.of(
                "app.notifications.poll-interval", "PT1M",
                "app.notifications.max-intentos", "4",
                "app.notifications.esperas", "PT30S,PT2M,PT10M",
                "app.notifications.lote", "10")))
                .bindOrCreate("app.notifications", NotificationProperties.class);

        assertThat(props.pollInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(props.maxIntentos()).isEqualTo(4);
        assertThat(props.esperas()).isEqualTo(List.of(Duration.ofSeconds(30), Duration.ofMinutes(2),
                Duration.ofMinutes(10)));
        assertThat(props.lote()).isEqualTo(10);
    }

    @Test
    void elContextoArrancaConLosValoresPorDefecto() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(NotificationProperties.class).maxIntentos()).isEqualTo(3);
        });
    }

    @Test
    void maxIntentosMenorQueUnoEsInvalido() {
        runner.withPropertyValues("app.notifications.max-intentos=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void loteMenorQueUnoYEsperasVaciasSonInvalidos() {
        runner.withPropertyValues("app.notifications.lote=0").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("app.notifications.esperas=").run(ctx -> assertThat(ctx).hasFailed());
    }
}
