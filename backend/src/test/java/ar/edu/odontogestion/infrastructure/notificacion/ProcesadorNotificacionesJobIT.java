package ar.edu.odontogestion.infrastructure.notificacion;

import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.notificacion.CanalEnMemoria;
import ar.edu.odontogestion.application.notificacion.NotificacionSaliente;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import java.sql.Timestamp;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Pendientes persistidos antes de un reinicio se envían cuando corre el procesamiento periódico. */
@Import(FixedClockTestConfig.class)
class ProcesadorNotificacionesJobIT extends AbstractIntegrationTest {

    private static final String DATOS = "{\"consultorio\": \"Consultorio Ficticio Sonrisa\", "
            + "\"inicio\": \"2026-11-24T13:00:00Z\", \"prestacion\": \"Limpieza ficticia\"}";

    @Autowired
    private ProcesadorNotificacionesJob job;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConsultorioActual consultorioActual;

    @Autowired
    private CanalEnMemoria canal;

    @AfterEach
    void limpiar() {
        canal.reiniciar();
        jdbc.update("DELETE FROM notificacion WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    @Test
    void losPendientesPreviosAlReinicioSeEnvianAlCorrerElJob() {
        // Filas escritas "por la ejecución anterior" de la aplicación, programadas en el pasado.
        for (long turno : new long[] {900501, 900502}) {
            jdbc.update("INSERT INTO notificacion (consultorio_id, tipo, destinatario, turno_id, datos, "
                    + "programada_para, creado_en, actualizado_en) VALUES (?, 'CONFIRMACION_TURNO', "
                    + "'paciente@ejemplo.test', ?, ?::jsonb, ?, ?, ?)", consultorioActual.id(), turno, DATOS,
                    Timestamp.from(AHORA.minus(Duration.ofHours(2))), Timestamp.from(AHORA.minus(Duration.ofHours(2))),
                    Timestamp.from(AHORA.minus(Duration.ofHours(2))));
        }

        job.ejecutar();

        assertThat(canal.enviados()).hasSize(2).extracting(NotificacionSaliente::destinatario)
                .containsOnly("paciente@ejemplo.test");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE turno_id IN (900501, 900502) "
                + "AND estado = 'ENVIADA'", Integer.class)).isEqualTo(2);
    }
}
