package ar.edu.odontogestion.application.notificacion;

import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.application.turno.TurnoComandos;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Invitación a reprogramar al marcar ausente y su descarte al revertir (C-08 design D4), con PostgreSQL y el canal
 * falso en memoria de C-03.
 */
class NotificacionesAusenciaIT extends AbstractTurnoIT {

    @Autowired
    private TurnoComandos comandos;

    @Autowired
    private NotificationService notificaciones;

    @Autowired
    private CanalEnMemoria canal;

    @Autowired
    private AppProperties appProperties;

    private Long paciente;
    private Long turno;

    @BeforeEach
    void datos() {
        canal.reiniciar();
        paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Instant inicio = AHORA.minus(Duration.ofMinutes(20));
        turno = insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, inicio, inicio.plus(Duration.ofMinutes(30)));
    }

    @AfterEach
    void reiniciarCanal() {
        canal.reiniciar();
    }

    private Map<String, Object> aviso() {
        return jdbc.queryForMap("SELECT estado, error, destinatario::text AS destinatario, datos::text AS datos, "
                + "jsonb_array_length(enlaces) AS enlaces FROM notificacion "
                + "WHERE turno_id = ? AND tipo = 'AUSENTE_REPROGRAMAR'", turno);
    }

    // ---- 5.1 ----

    @Test
    void marcarAusenteEncolaLaInvitacionSinEnlacesConLasVariablesDeSiempre() {
        comandos.marcarAusente(turno);

        Map<String, Object> aviso = aviso();
        String email = jdbc.queryForObject("SELECT email::text FROM paciente WHERE id = ?", String.class, paciente);
        assertThat(aviso).containsEntry("estado", "PENDIENTE").containsEntry("destinatario", email)
                .containsEntry("enlaces", 0);
        assertThat((String) aviso.get("datos")).contains("\"consultorio\"", "\"inicio\"", "\"prestacion\"");
        assertThat(canal.enviados()).isEmpty();
    }

    @Test
    void alEnviarseElCuerpoInvitaAReservarEnLaPaginaPublicaSinElDni() {
        comandos.marcarAusente(turno);

        notificaciones.procesarPendientes();

        String dni = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, paciente);
        assertThat(canal.enviados()).singleElement().satisfies(n -> {
            assertThat(n.cuerpo()).contains(appProperties.baseUrl()).doesNotContain(dni);
            assertThat(n.asunto()).isNotBlank();
        });
        assertThat(aviso()).containsEntry("estado", "ENVIADA");
    }

    @Test
    void conElCanalCaidoElTurnoSigueAusenteYElAvisoQuedaParaReintento() {
        canal.fallarSiempre();
        comandos.marcarAusente(turno);

        notificaciones.procesarPendientes();

        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno))
                .isEqualTo("AUSENTE");
        assertThat(aviso()).containsEntry("estado", "PENDIENTE");
        assertThat((String) aviso().get("error")).isNotBlank();
    }

    // ---- 5.2 ----

    @Test
    void revertirConElAvisoPendienteLoDescartaYNoSeEnvia() {
        comandos.marcarAusente(turno);

        comandos.revertirAusencia(turno);
        notificaciones.procesarPendientes();

        assertThat(aviso()).containsEntry("estado", "FALLIDA").containsEntry("error", "AUSENCIA_REVERTIDA");
        assertThat(canal.enviados()).isEmpty();
    }

    @Test
    void revertirConElAvisoYaEnviadoLoConserva() {
        comandos.marcarAusente(turno);
        notificaciones.procesarPendientes();

        comandos.revertirAusencia(turno);

        assertThat(aviso()).containsEntry("estado", "ENVIADA");
        assertThat(aviso().get("error")).isNull();
    }

    @Test
    void elDescarteSoloTocaLaInvitacionDeEseTurno() {
        jdbc.update("INSERT INTO notificacion (consultorio_id, tipo, destinatario, turno_id, datos, programada_para) "
                + "SELECT min(id), 'CONFIRMACION_TURNO', 'paciente@ejemplo.test', ?, ?::jsonb, ? FROM consultorio",
                turno, "{\"consultorio\": \"C\", \"inicio\": \"2026-11-20T11:40:00Z\", \"prestacion\": \"P\"}",
                java.sql.Timestamp.from(AHORA));
        comandos.marcarAusente(turno);

        comandos.revertirAusencia(turno);

        assertThat(jdbc.queryForObject("SELECT estado FROM notificacion WHERE turno_id = ? AND tipo = "
                + "'CONFIRMACION_TURNO'", String.class, turno)).isEqualTo("PENDIENTE");
    }
}
