package ar.edu.odontogestion.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.application.notificacion.CanalEnMemoria;
import ar.edu.odontogestion.application.notificacion.NotificacionSaliente;
import ar.edu.odontogestion.application.notificacion.NotificationService;
import ar.edu.odontogestion.application.recordatorio.RecordatorioService;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.support.AbstractRecordatorioIT;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

/** El job se invoca directamente (los ITs corren con {@code app.scheduling.enabled=false}) con el reloj fijo. */
class RecordatoriosSchedulerIT extends AbstractRecordatorioIT {

    @Autowired
    private RecordatoriosScheduler scheduler;
    @Autowired
    private NotificationService notificaciones;
    @Autowired
    private CanalEnMemoria canal;
    @Autowired
    private Environment entorno;

    @BeforeEach
    @AfterEach
    void reiniciarCanal() {
        canal.reiniciar();
    }

    private List<NotificacionSaliente> recordatoriosEnviados() {
        return canal.enviados().stream().filter(n -> n.tipo() == TipoNotificacion.RECORDATORIO).toList();
    }

    @Test
    void ejecutarEncolaLosCandidatos() {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Long id = reservado(p, pr, reloj.instant().plus(Duration.ofHours(6)));

        scheduler.ejecutar();

        assertThat(recordatoriosDe(id)).isEqualTo(1);
    }

    @Test
    void unaExcepcionDelServicioNoSePropaga() {
        RecordatorioService roto = mock(RecordatorioService.class);
        when(roto.encolarRecordatorios()).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> new RecordatoriosScheduler(roto).ejecutar()).doesNotThrowAnyException();
    }

    @Test
    void elIntervaloEsConfigurableConUnValorPorDefectoDeCincoMinutos() throws Exception {
        Scheduled anotacion = RecordatoriosScheduler.class.getMethod("ejecutar").getAnnotation(Scheduled.class);

        assertThat(anotacion.fixedDelayString()).isEqualTo("${app.recordatorios.intervalo:PT5M}");
        assertThat(entorno.getProperty("app.recordatorios.intervalo")).isEqualTo("PT5M");
    }

    @Test
    void retomaTrasUnReinicioEnviaElPendienteYElNuevoUnaSolaVezYNoRecuerdaUnTurnoYaIniciado() {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Instant ahora = reloj.instant();
        Long previo = reservado(p, pr, ahora.plus(Duration.ofHours(2)));
        Long nuevo = reservado(p, pr, ahora.plus(Duration.ofHours(3)));
        Long yaIniciado = reservado(p, pr, ahora.plus(Duration.ofHours(1)));
        // "Proceso caído": el previo quedó encolado y el job no corrió para los demás.
        jdbc.update("INSERT INTO notificacion (consultorio_id, tipo, canal, destinatario, turno_id, datos, enlaces, "
                + "programada_para) SELECT ?, 'RECORDATORIO', 'EMAIL', pa.email, t.id, jsonb_build_object("
                + "'consultorio', 'Consultorio ficticio', 'inicio', t.inicio, 'prestacion', 'IT-Turno-x'), "
                + "jsonb_build_array(jsonb_build_object('proposito','CONFIRMAR_TURNO','venceEn',t.inicio), "
                + "jsonb_build_object('proposito','CANCELAR_TURNO','venceEn',t.inicio)), ?::timestamptz "
                + "FROM turno t JOIN paciente pa ON pa.id = t.paciente_id WHERE t.id = ?",
                consultorioId(), ahora.toString(), previo);
        reloj.advance(Duration.ofHours(1).plusSeconds(1)); // el turno "yaIniciado" ya empezó

        scheduler.ejecutar();
        scheduler.ejecutar();
        notificaciones.procesarPendientes();
        notificaciones.procesarPendientes();

        assertThat(recordatoriosDe(previo)).isEqualTo(1);
        assertThat(recordatoriosDe(nuevo)).isEqualTo(1);
        assertThat(recordatoriosDe(yaIniciado)).isZero();
        assertThat(recordatoriosEnviados()).hasSize(2);
    }
}
