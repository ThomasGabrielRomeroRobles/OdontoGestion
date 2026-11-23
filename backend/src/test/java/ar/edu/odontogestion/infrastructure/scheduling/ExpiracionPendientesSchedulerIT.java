package ar.edu.odontogestion.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

/** El job se invoca directamente (los ITs corren con {@code app.scheduling.enabled=false}) con el reloj avanzable. */
class ExpiracionPendientesSchedulerIT extends AbstractTurnoIT {

    @Autowired
    private ExpiracionPendientesScheduler scheduler;
    @Autowired
    private Environment entorno;

    private Long pendiente(Long paciente, Long prestacion, String inicio, Instant hasta) {
        return insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes(inicio),
                lunes(inicio).plus(Duration.ofMinutes(30)), hasta);
    }

    @Test
    void expiraSoloLaVencidaYLaSegundaEjecucionNoCambiaNada() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long vencida = pendiente(paciente, prestacion, "10:00", reloj.instant().minusSeconds(5));
        Long vigente = pendiente(paciente, prestacion, "11:00", reloj.instant().plusSeconds(600));

        scheduler.ejecutar();
        scheduler.ejecutar();

        assertThat(estadoDe(vencida)).isEqualTo("EXPIRADO");
        assertThat(estadoDe(vigente)).isEqualTo("PENDIENTE_CONFIRMACION");
        assertThat(jdbc.queryForObject("SELECT version FROM turno WHERE id = ?", Long.class, vencida)).isEqualTo(1L);
    }

    @Test
    void usaElRelojInyectadoParaDecidirQueEstaVencido() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long vigente = pendiente(paciente, prestacion, "11:00", reloj.instant().plusSeconds(600));

        scheduler.ejecutar();
        assertThat(estadoDe(vigente)).isEqualTo("PENDIENTE_CONFIRMACION");

        reloj.advance(Duration.ofMinutes(11));
        scheduler.ejecutar();
        assertThat(estadoDe(vigente)).isEqualTo("EXPIRADO");
    }

    @Test
    void elIntervaloEsConfigurableConUnValorPorDefectoDeUnMinuto() throws Exception {
        Scheduled anotacion = ExpiracionPendientesScheduler.class.getMethod("ejecutar").getAnnotation(Scheduled.class);

        assertThat(anotacion.fixedDelayString()).contains("app.turnos.expiracion-intervalo");
        assertThat(entorno.getProperty("app.turnos.expiracion-intervalo")).isEqualTo("PT1M");
    }
}
