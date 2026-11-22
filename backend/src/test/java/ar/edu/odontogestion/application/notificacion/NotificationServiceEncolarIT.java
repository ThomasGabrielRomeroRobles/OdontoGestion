package ar.edu.odontogestion.application.notificacion;

import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Encolado dentro de la transacción de origen (design D6/D7/D8). Ids ficticios >= 900000 y datos ficticios. */
@Import(FixedClockTestConfig.class)
class NotificationServiceEncolarIT extends AbstractIntegrationTest {

    static final Map<String, String> DATOS_TURNO = Map.of(
            "consultorio", "Consultorio Ficticio Sonrisa",
            "inicio", "2026-11-24T13:00:00Z",
            "prestacion", "Limpieza ficticia");

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

    @AfterEach
    void limpiar() {
        canal.reiniciar();
        jdbc.update("DELETE FROM notificacion WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    private int filas(String where) {
        return jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE " + where, Integer.class);
    }

    @Test
    void notificacionEncoladaQuedaPendienteSinEnviarse() {
        Boolean insertada = tx.execute(s -> service.encolar(TipoNotificacion.CONFIRMACION_TURNO,
                "paciente@ejemplo.test", new ReferenciaToken.Turno(900101), DATOS_TURNO, List.of()));

        assertThat(insertada).isTrue();
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM notificacion WHERE turno_id = 900101");
        assertThat(fila).containsEntry("estado", "PENDIENTE")
                .containsEntry("tipo", "CONFIRMACION_TURNO")
                .containsEntry("canal", "EMAIL")
                .containsEntry("intentos", 0);
        assertThat(String.valueOf(fila.get("destinatario"))).isEqualTo("paciente@ejemplo.test");
        assertThat(((Number) fila.get("consultorio_id")).longValue()).isEqualTo(consultorioActual.id());
        assertThat(((Timestamp) fila.get("programada_para")).toInstant()).isEqualTo(AHORA);
        assertThat(((Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(AHORA);
        assertThat(fila.get("enviada_en")).isNull();
        assertThat(canal.enviados()).isEmpty();
    }

    @Test
    void enlacesProgramadosSeGuardanSinToken() {
        tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.RECORDATORIO, "paciente@ejemplo.test",
                new ReferenciaToken.Turno(900102), DATOS_TURNO, List.of(
                        new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, AHORA.plus(Duration.ofHours(24))),
                        new EnlaceProgramado(PropositoToken.CANCELAR_TURNO, AHORA.plus(Duration.ofHours(24))))));

        String enlaces = jdbc.queryForObject("SELECT enlaces::text FROM notificacion WHERE turno_id = 900102",
                String.class);
        assertThat(enlaces).contains("CONFIRMAR_TURNO", "CANCELAR_TURNO", "2026-11-21T12:00:00Z");
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(enlaces) FROM notificacion WHERE turno_id = 900102",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void origenRevertidoDescartaLaNotificacion() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            service.encolar(TipoNotificacion.CONFIRMACION_TURNO, "paciente@ejemplo.test",
                    new ReferenciaToken.Turno(900103), DATOS_TURNO, List.of());
            throw new IllegalStateException("la operación de origen falla");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(filas("turno_id = 900103")).isZero();
    }

    @Test
    void avisoHuecoReferenciaSoloAlAviso() {
        tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.AVISO_HUECO, "paciente@ejemplo.test",
                new ReferenciaToken.Aviso(900007), DATOS_TURNO,
                List.of(new EnlaceProgramado(PropositoToken.ACEPTAR_AVISO, AHORA.plus(Duration.ofHours(2))))));

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM notificacion WHERE aviso_id = 900007");
        assertThat(fila.get("turno_id")).isNull();
        assertThat(fila.get("entrada_lista_espera_id")).isNull();
    }

    @Test
    void referenciaIncoherenteConElTipoEsRechazadaSinPersistir() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.RECORDATORIO,
                "paciente@ejemplo.test", new ReferenciaToken.Aviso(900008), DATOS_TURNO, List.of(
                        new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, AHORA.plus(Duration.ofHours(24))),
                        new EnlaceProgramado(PropositoToken.CANCELAR_TURNO, AHORA.plus(Duration.ofHours(24)))))))
                .isInstanceOf(ValidacionDominioException.class);

        assertThat(filas("aviso_id = 900008")).isZero();
    }

    @Test
    void variableNoDeclaradaEsRechazadaSinPersistir() {
        Map<String, String> conDiagnostico = new java.util.HashMap<>(DATOS_TURNO);
        conDiagnostico.put("diagnostico", "dato clinico ficticio");

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.CONFIRMACION_TURNO,
                "paciente@ejemplo.test", new ReferenciaToken.Turno(900104), conDiagnostico, List.of())))
                .isInstanceOf(ValidacionDominioException.class);

        assertThat(filas("turno_id = 900104")).isZero();
    }

    @Test
    void enlacesQueNoCorrespondenAlTipoODestinatarioInvalidoSonRechazados() {
        assertThatThrownBy(() -> service.encolar(TipoNotificacion.RECORDATORIO, "paciente@ejemplo.test",
                new ReferenciaToken.Turno(900105), DATOS_TURNO,
                List.of(new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, AHORA.plus(Duration.ofHours(24))))))
                .isInstanceOf(ValidacionDominioException.class);
        assertThatThrownBy(() -> service.encolar(TipoNotificacion.CONFIRMACION_TURNO, "no-es-un-email",
                new ReferenciaToken.Turno(900105), DATOS_TURNO, List.of()))
                .isInstanceOf(ValidacionDominioException.class);

        assertThat(filas("turno_id = 900105")).isZero();
    }
}
