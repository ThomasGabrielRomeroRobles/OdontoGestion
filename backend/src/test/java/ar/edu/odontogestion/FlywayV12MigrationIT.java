package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractHistoriaIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V12 (C-05): tablas de historia clínica. La base garantiza una historia por paciente, una fila por pieza, el
 * conjunto FDI permanente, el enum de estados, el largo de la nota y la FK a turno (design D1). Datos ficticios.
 */
class FlywayV12MigrationIT extends AbstractHistoriaIT {

    private static final String INSERT_PIEZA = "INSERT INTO pieza_dental_estado (historia_id, pieza_fdi, estado, "
            + "actualizado_por_id) VALUES (?, ?, ?, ?)";
    private static final String INSERT_EVOLUCION = "INSERT INTO evolucion_clinica (historia_id, usuario_id, "
            + "turno_id, fecha, nota) VALUES (?, ?, ?, DATE '2026-11-20', ?)";

    private Long paciente;
    private Long historia;
    private Long usuario;

    @BeforeEach
    void datos() {
        paciente = crearPaciente();
        historia = insertarHistoria(paciente);
        usuario = usuarioSeedId();
    }

    @Test
    void versionDoceAplicada() {
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '12'", Boolean.class)).isTrue();
    }

    @Test
    void unaSolaHistoriaPorPaciente() {
        assertThatThrownBy(() -> insertarHistoria(paciente)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaSolaAnamnesisPorHistoria() {
        String sql = "INSERT INTO anamnesis (historia_id, actualizado_por_id) VALUES (?, ?)";
        jdbc.update(sql, historia, usuario);
        assertThatThrownBy(() -> jdbc.update(sql, historia, usuario))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaSolaFilaPorPieza() {
        jdbc.update(INSERT_PIEZA, historia, 16, "CARIES", usuario);
        assertThatThrownBy(() -> jdbc.update(INSERT_PIEZA, historia, 16, "SANA", usuario))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void soloPiezasPermanentesFdi() {
        for (int valida : new int[] {11, 18, 21, 28, 31, 38, 41, 48}) {
            assertThat(jdbc.update(INSERT_PIEZA, historia, valida, "SANA", usuario)).isEqualTo(1);
        }
        for (int invalida : new int[] {10, 19, 20, 49, 51, 85, 0}) {
            assertThatThrownBy(() -> jdbc.update(INSERT_PIEZA, historia, invalida, "SANA", usuario))
                    .as("pieza %d", invalida).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void estadoDesconocidoRechazado() {
        assertThatThrownBy(() -> jdbc.update(INSERT_PIEZA, historia, 16, "ROTA", usuario))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notaVaciaODemasiadoLargaRechazada() {
        assertThatThrownBy(() -> jdbc.update(INSERT_EVOLUCION, historia, usuario, null, ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(INSERT_EVOLUCION, historia, usuario, null, "x".repeat(4001)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(INSERT_EVOLUCION, historia, usuario, null, "x".repeat(4000))).isEqualTo(1);
    }

    @Test
    void turnoAsociadoDebeExistir() {
        assertThatThrownBy(() -> jdbc.update(INSERT_EVOLUCION, historia, usuario, 999_999_999L, "Nota ficticia"))
                .isInstanceOf(DataIntegrityViolationException.class);
        Long prestacion = crearPrestacion(30);
        Long turno = insertarTurno(paciente, prestacion, EstadoTurno.ATENDIDO, lunes("09:00"), lunes("09:30"));
        assertThat(jdbc.update(INSERT_EVOLUCION, historia, usuario, turno, "Nota ficticia")).isEqualTo(1);
    }

    @Test
    void historiaSinConsultorioRechazada() {
        Long otro = crearPaciente();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO historia_clinica (consultorio_id, paciente_id) VALUES (NULL, ?)", otro))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
