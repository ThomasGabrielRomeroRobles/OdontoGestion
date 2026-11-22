package ar.edu.odontogestion.application.notificacion;

import static ar.edu.odontogestion.application.notificacion.NotificationServiceEncolarIT.DATOS_TURNO;
import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import ar.edu.odontogestion.support.MutableClock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** RN-NO-06: el fallo del canal no revierte ni impide la operación de negocio que encoló la notificación. */
@Import(FixedClockTestConfig.class)
class NotificationServiceFalloSinRollbackIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private ConsultorioActual consultorioActual;

    @Autowired
    private CanalEnMemoria canal;

    @Autowired
    private MutableClock reloj;

    private String telefonoOriginal;

    @BeforeEach
    void guardarTelefono() {
        telefonoOriginal = jdbc.queryForObject("SELECT telefono FROM consultorio WHERE id = ?", String.class,
                consultorioActual.id());
    }

    @AfterEach
    void limpiar() {
        jdbc.update("UPDATE consultorio SET telefono = ? WHERE id = ?", telefonoOriginal, consultorioActual.id());
        reloj.set(AHORA);
        canal.reiniciar();
        jdbc.update("DELETE FROM notificacion WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    @Test
    void conElCanalCaidoLosDatosDeNegocioPersistenYLaNotificacionRegistraElError() {
        canal.fallarSiempre();

        tx.executeWithoutResult(s -> {
            jdbc.update("UPDATE consultorio SET telefono = '0000-ficticio' WHERE id = ?", consultorioActual.id());
            service.encolar(TipoNotificacion.CONFIRMACION_TURNO, "paciente@ejemplo.test",
                    new ReferenciaToken.Turno(900301), DATOS_TURNO, List.of());
        });
        assertThatCode(() -> service.procesarPendientes()).doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("SELECT telefono FROM consultorio WHERE id = ?", String.class,
                consultorioActual.id())).isEqualTo("0000-ficticio");
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT estado, intentos, error FROM notificacion WHERE turno_id = 900301");
        assertThat(fila).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 1)
                .containsEntry("error", "Canal en memoria configurado para fallar");
    }

    @Test
    void unaExcepcionAlPrepararElEnvioSeRegistraComoFalloSinPropagarse() {
        // Datos corruptos insertados a mano (variable no declarada): la plantilla rechaza el render.
        jdbc.update("INSERT INTO notificacion (consultorio_id, tipo, destinatario, turno_id, datos, programada_para) "
                + "VALUES (?, 'CONFIRMACION_TURNO', 'paciente@ejemplo.test', 900302, "
                + "'{\"diagnostico\": \"dato clinico ficticio\"}'::jsonb, ?)",
                consultorioActual.id(), java.sql.Timestamp.from(AHORA));

        assertThatCode(() -> service.procesarPendientes()).doesNotThrowAnyException();

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT estado, intentos, error FROM notificacion WHERE turno_id = 900302");
        assertThat(fila).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 1);
        assertThat((String) fila.get("error")).startsWith("Error al preparar el envío")
                .doesNotContain("dato clinico ficticio");
        assertThat(canal.enviados()).isEmpty();
    }
}
