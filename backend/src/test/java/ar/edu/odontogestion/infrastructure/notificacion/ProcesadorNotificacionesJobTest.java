package ar.edu.odontogestion.infrastructure.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import ar.edu.odontogestion.application.notificacion.NotificationService;
import ar.edu.odontogestion.infrastructure.config.SchedulingConfig;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/** Procesamiento periódico del outbox (design D12), en un contexto liviano sin base. */
class ProcesadorNotificacionesJobTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(NotificationService.class, () -> mock(NotificationService.class))
            .withUserConfiguration(SchedulingConfig.class, ProcesadorNotificacionesJob.class);

    private static Set<ScheduledTask> tareas(org.springframework.context.ApplicationContext ctx) {
        return ctx.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .flatMap(h -> h.getScheduledTasks().stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void conElSchedulingDeshabilitadoNoHayTareasProgramadas() {
        runner.withPropertyValues("app.scheduling.enabled=false").run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(ProcesadorNotificacionesJob.class);
            assertThat(tareas(ctx)).isEmpty();
        });
    }

    @Test
    void habilitadoHayUnaTareaDeRetrasoFijoDeCincoMinutosQueProcesaLosPendientes() {
        runner.withPropertyValues("app.scheduling.enabled=true").run(ctx -> {
            assertThat(tareas(ctx)).singleElement().satisfies(tarea -> {
                assertThat(tarea.getTask()).isInstanceOf(FixedDelayTask.class);
                assertThat(((FixedDelayTask) tarea.getTask()).getIntervalDuration()).isEqualTo(Duration.ofMinutes(5));
                // Spring 6.2 envuelve el ScheduledMethodRunnable; su toString nombra el método programado.
                assertThat(tarea.getTask().getRunnable().toString())
                        .contains(ProcesadorNotificacionesJob.class.getName() + ".ejecutar");
            });

            // Sin demora inicial, el scheduler ya ejecutó una vez al arrancar el contexto.
            NotificationService service = ctx.getBean(NotificationService.class);
            verify(service, timeout(2000).atLeastOnce()).procesarPendientes();
            clearInvocations(service);
            ctx.getBean(ProcesadorNotificacionesJob.class).ejecutar();
            verify(service).procesarPendientes();
        });
    }

    @Test
    void elIntervaloSeConfiguraPorPropiedad() {
        runner.withPropertyValues("app.scheduling.enabled=true", "app.notifications.poll-interval=PT1M").run(ctx ->
                assertThat(tareas(ctx)).singleElement().satisfies(tarea ->
                        assertThat(((FixedDelayTask) tarea.getTask()).getIntervalDuration())
                                .isEqualTo(Duration.ofMinutes(1))));
    }

    @Test
    void sinLaPropiedadElSchedulingQuedaHabilitado() {
        runner.run(ctx -> assertThat(tareas(ctx)).hasSize(1));
    }
}
