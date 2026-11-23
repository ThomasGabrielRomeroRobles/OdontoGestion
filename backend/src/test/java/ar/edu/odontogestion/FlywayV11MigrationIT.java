package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V11 (C-08): datos de la marca de ausente en {@code turno}. Turnos ficticios sobre pacientes y prestaciones de
 * {@link AbstractTurnoIT}, que los borra antes y después de cada caso.
 */
class FlywayV11MigrationIT extends AbstractTurnoIT {

    private static final String INSERT = "INSERT INTO turno (consultorio_id, paciente_id, prestacion_id, inicio, fin, "
            + "estado, origen, ausente_marcado_en, estado_previo_ausencia) "
            + "VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, ?, 'ODONTOLOGO', ?::timestamptz, ?)";

    private Long paciente;
    private Long prestacion;
    private int hora;

    @BeforeEach
    void datos() {
        paciente = crearPaciente();
        prestacion = crearPrestacion(30);
        hora = 8;
    }

    /** Cada inserción usa un horario distinto para no chocar con la restricción de no superposición. */
    private int insertar(String estado, String marcadoEn, String estadoPrevio) {
        Instant inicio = lunes(String.format("%02d:00", hora++));
        return jdbc.update(INSERT, consultorioId(), paciente, prestacion, inicio.toString(),
                inicio.plusSeconds(1800).toString(), estado, marcadoEn, estadoPrevio);
    }

    @Test
    void versionOnceAplicadaConLasColumnasNullable() {
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '11'", Boolean.class)).isTrue();
        Map<String, Map<String, Object>> columnas = new HashMap<>();
        jdbc.queryForList("SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'turno'").forEach(f -> columnas.put((String) f.get("column_name"), f));
        assertThat(columnas.get("ausente_marcado_en")).containsEntry("data_type", "timestamp with time zone")
                .containsEntry("is_nullable", "YES");
        assertThat(columnas.get("estado_previo_ausencia")).containsEntry("data_type", "text")
                .containsEntry("is_nullable", "YES");
    }

    @Test
    void ausenteSinMarcaOSinEstadoPrevioEsRechazado() {
        assertThatThrownBy(() -> insertar("AUSENTE", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("AUSENTE", "2026-11-23T13:20:00Z", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("AUSENTE", null, "RESERVADO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void datosDeAusenciaEnOtroEstadoSonRechazados() {
        assertThatThrownBy(() -> insertar("RESERVADO", "2026-11-23T13:20:00Z", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("CONFIRMADO", null, "RESERVADO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("ATENDIDO", "2026-11-23T13:20:00Z", "RESERVADO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void estadoPrevioQueNoEsReservadoNiConfirmadoEsRechazado() {
        assertThatThrownBy(() -> insertar("AUSENTE", "2026-11-23T13:20:00Z", "CANCELADO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("AUSENTE", "2026-11-23T13:20:00Z", "OTRO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ausenteConMarcaYEstadoPrevioValidoEsAceptado() {
        assertThat(insertar("AUSENTE", "2026-11-23T13:20:00Z", "RESERVADO")).isEqualTo(1);
        assertThat(insertar("AUSENTE", "2026-11-23T14:20:00Z", "CONFIRMADO")).isEqualTo(1);
        assertThat(insertar("RESERVADO", null, null)).isEqualTo(1);
    }
}
