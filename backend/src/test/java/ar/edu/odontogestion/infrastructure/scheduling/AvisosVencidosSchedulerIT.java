package ar.edu.odontogestion.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.application.listaespera.AvisoHuecoService;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

/** Barrido de avisos vencidos (C-10 D6). Se invoca directo (los ITs corren con {@code app.scheduling.enabled=false}). */
class AvisosVencidosSchedulerIT extends AbstractListaEsperaIT {

    private static final LocalDate LUN = LocalDate.of(2026, 11, 23);

    @Autowired
    private AvisosVencidosScheduler scheduler;
    @Autowired
    private Environment entorno;

    private Long aviso(Long turnoOrigen, Long prestacion, String estado, java.time.Instant venceEn) {
        Long entrada = insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN);
        return insertarAviso(entrada, turnoOrigen, prestacion, lunes("10:00"), lunes("10:30"), estado, venceEn);
    }

    @Test
    void expiraSoloLosEnviadosVencidosYLaSegundaEjecucionNoCambiaNada() {
        Long prestacion = crearPrestacion(30);
        Long origen = insertarTurno(crearPaciente(), prestacion, EstadoTurno.CANCELADO, lunes("10:00"),
                lunes("10:30"));
        Long vencido1 = aviso(origen, prestacion, "ENVIADO", reloj.instant().minusSeconds(5));
        Long vencido2 = aviso(origen, prestacion, "ENVIADO", reloj.instant());
        Long vigente = aviso(origen, prestacion, "ENVIADO", reloj.instant().plusSeconds(600));
        Long cerradoVencido = aviso(origen, prestacion, "CERRADO", reloj.instant().minusSeconds(5));

        scheduler.ejecutar();
        scheduler.ejecutar();

        assertThat(estadoAviso(vencido1)).isEqualTo("EXPIRADO");
        assertThat(estadoAviso(vencido2)).isEqualTo("EXPIRADO");
        assertThat(estadoAviso(vigente)).isEqualTo("ENVIADO");
        assertThat(estadoAviso(cerradoVencido)).isEqualTo("CERRADO");
        assertThat(jdbc.queryForObject("SELECT version FROM aviso_hueco WHERE id = ?", Long.class, vencido1))
                .isEqualTo(1L);
    }

    @Test
    void usaElRelojInyectado() {
        Long prestacion = crearPrestacion(30);
        Long origen = insertarTurno(crearPaciente(), prestacion, EstadoTurno.CANCELADO, lunes("10:00"),
                lunes("10:30"));
        Long vigente = aviso(origen, prestacion, "ENVIADO", reloj.instant().plusSeconds(600));

        scheduler.ejecutar();
        assertThat(estadoAviso(vigente)).isEqualTo("ENVIADO");

        reloj.advance(Duration.ofMinutes(11));
        scheduler.ejecutar();
        assertThat(estadoAviso(vigente)).isEqualTo("EXPIRADO");
    }

    @Test
    void nuncaPropagaUnaExcepcion() {
        AvisoHuecoService roto = mock(AvisoHuecoService.class);
        when(roto.expirarVencidos()).thenThrow(new IllegalStateException("base caída"));

        assertThatCode(() -> new AvisosVencidosScheduler(roto).ejecutar()).doesNotThrowAnyException();
    }

    @Test
    void elIntervaloEsConfigurableConUnValorPorDefectoDeUnMinuto() throws Exception {
        Scheduled anotacion = AvisosVencidosScheduler.class.getMethod("ejecutar").getAnnotation(Scheduled.class);

        assertThat(anotacion.fixedDelayString()).contains("app.lista-espera.expiracion-intervalo");
        assertThat(anotacion.fixedDelayString()).endsWith(":PT1M}");
        assertThat(entorno.getProperty("app.lista-espera.expiracion-intervalo")).isEqualTo("PT1M");
    }
}
