package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** V4 (C-06): prestaciones, horarios y bloqueos con restricciones con nombre, y su seed ficticio. */
class FlywayV4MigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    /** Ejecuta el bloque en una transacción que siempre se revierte: no deja datos para otros tests. */
    private void revertida(Runnable bloque) {
        tx.executeWithoutResult(status -> {
            bloque.run();
            status.setRollbackOnly();
        });
    }

    private Long consultorioId() {
        return jdbc.queryForObject("SELECT min(id) FROM consultorio", Long.class);
    }

    /** Afirma que la sentencia viola exactamente la restricción con ese nombre. */
    private void violaRestriccion(String nombre, String sql, Object... args) {
        revertida(() -> assertThatThrownBy(() -> jdbc.update(sql, args))
                .as(nombre)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(nombre));
    }

    private String tipo(String tabla, String columna) {
        return jdbc.queryForObject("SELECT udt_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?", String.class, tabla, columna);
    }

    @Test
    void elHistorialRegistraLaVersionCuatro() {
        Boolean ok = jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '4'",
                Boolean.class);
        assertThat(ok).isTrue();
    }

    @Test
    void lasTresTablasTienenConsultorioYFechasDeAuditoriaObligatorias() {
        for (String tabla : List.of("prestacion", "horario_trabajo", "bloqueo_agenda")) {
            List<String> obligatorias = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = ? AND is_nullable = 'NO'", String.class, tabla);
            assertThat(obligatorias).as(tabla).contains("id", "consultorio_id", "creado_en", "actualizado_en");
            assertThat(tipo(tabla, "creado_en")).as(tabla).isEqualTo("timestamptz");
            assertThat(tipo(tabla, "actualizado_en")).as(tabla).isEqualTo("timestamptz");
        }
    }

    @Test
    void tiposDeColumnaQueMapeaHibernate() {
        assertThat(tipo("prestacion", "nombre")).isEqualTo("citext");
        assertThat(tipo("prestacion", "duracion_min")).isEqualTo("int4");
        assertThat(tipo("horario_trabajo", "dia_semana")).isEqualTo("int2");
        assertThat(tipo("horario_trabajo", "hora_desde")).isEqualTo("time");
        assertThat(tipo("bloqueo_agenda", "desde")).isEqualTo("timestamptz");
        assertThat(tipo("bloqueo_agenda", "hasta")).isEqualTo("timestamptz");
        assertThat(tipo("bloqueo_agenda", "motivo")).isEqualTo("text");
    }

    @Test
    void nombreDePrestacionUnicoSinDistinguirMayusculas() {
        violaRestriccion("uq_prestacion_consultorio_nombre",
                "INSERT INTO prestacion (consultorio_id, nombre, duracion_min) VALUES (?, 'consulta', 30)",
                consultorioId());
    }

    @Test
    void duracionDePrestacionPositiva() {
        violaRestriccion("ck_prestacion_duracion_positiva",
                "INSERT INTO prestacion (consultorio_id, nombre, duracion_min) VALUES (?, 'Nueva', 0)",
                consultorioId());
    }

    @Test
    void nombreDePrestacionNoVacio() {
        violaRestriccion("ck_prestacion_nombre_no_vacio",
                "INSERT INTO prestacion (consultorio_id, nombre, duracion_min) VALUES (?, '', 30)", consultorioId());
    }

    @Test
    void diaDeLaSemanaEntreUnoYSiete() {
        violaRestriccion("ck_horario_dia_semana", "INSERT INTO horario_trabajo "
                + "(consultorio_id, dia_semana, hora_desde, hora_hasta) VALUES (?, 8, '09:00', '10:00')",
                consultorioId());
    }

    @Test
    void horarioConInicioAnteriorAlFin() {
        violaRestriccion("ck_horario_rango", "INSERT INTO horario_trabajo "
                + "(consultorio_id, dia_semana, hora_desde, hora_hasta) VALUES (?, 6, '14:00', '13:00')",
                consultorioId());
    }

    @Test
    void horariosSolapadosEnElMismoDiaRechazadosPorLaBase() {
        violaRestriccion("ex_horario_trabajo_sin_solapamiento", "INSERT INTO horario_trabajo "
                + "(consultorio_id, dia_semana, hora_desde, hora_hasta) VALUES (?, 1, '12:00', '15:00')",
                consultorioId());
    }

    @Test
    void horarioContiguoYMismoRangoEnOtroDiaSonAceptados() {
        revertida(() -> {
            jdbc.update("INSERT INTO horario_trabajo (consultorio_id, dia_semana, hora_desde, hora_hasta) "
                    + "VALUES (?, 1, '13:00', '14:00')", consultorioId());
            jdbc.update("INSERT INTO horario_trabajo (consultorio_id, dia_semana, hora_desde, hora_hasta) "
                    + "VALUES (?, 6, '09:00', '13:00')", consultorioId());
        });
    }

    @Test
    void bloqueoConDesdeAnteriorAHasta() {
        violaRestriccion("ck_bloqueo_rango", "INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta) "
                + "VALUES (?, '2026-12-26T03:00:00Z', '2026-12-24T03:00:00Z')", consultorioId());
    }

    @Test
    void motivoDeBloqueoVacioPorDefectoYCortoAcotado() {
        revertida(() -> {
            String motivo = jdbc.queryForObject("INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta) "
                    + "VALUES (?, '2026-12-24T03:00:00Z', '2026-12-26T03:00:00Z') RETURNING motivo",
                    String.class, consultorioId());
            assertThat(motivo).isEmpty();
        });
        violaRestriccion("ck_bloqueo_motivo_largo", "INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta, motivo) "
                + "VALUES (?, '2026-12-24T03:00:00Z', '2026-12-26T03:00:00Z', repeat('x', 201))", consultorioId());
    }

    @Test
    void elSeedCargaLasSeisPrestacionesFicticiasActivas() {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT nombre::text AS nombre, duracion_min, activa "
                + "FROM prestacion WHERE consultorio_id = ? AND nombre IN ('Consulta', 'Limpieza', 'Obturación', "
                + "'Endodoncia', 'Extracción', 'Control') ORDER BY nombre", consultorioId());
        assertThat(filas).extracting(f -> f.get("nombre") + "=" + f.get("duracion_min")).containsExactly(
                "Consulta=30", "Control=15", "Endodoncia=90", "Extracción=45", "Limpieza=45", "Obturación=60");
    }

    @Test
    void elSeedCargaDosFranjasDeLunesAViernes() {
        List<String> franjas = jdbc.queryForList("SELECT dia_semana || ' ' || to_char(hora_desde, 'HH24:MI') "
                + "|| '-' || to_char(hora_hasta, 'HH24:MI') FROM horario_trabajo WHERE consultorio_id = ? "
                + "AND dia_semana BETWEEN 1 AND 5 ORDER BY dia_semana, hora_desde", String.class, consultorioId());
        assertThat(franjas).containsExactly(
                "1 09:00-13:00", "1 14:00-18:00", "2 09:00-13:00", "2 14:00-18:00", "3 09:00-13:00",
                "3 14:00-18:00", "4 09:00-13:00", "4 14:00-18:00", "5 09:00-13:00", "5 14:00-18:00");
    }
}
