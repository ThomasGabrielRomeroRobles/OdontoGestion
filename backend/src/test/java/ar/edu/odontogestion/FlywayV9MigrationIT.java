package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V9 (C-07): tabla turno, exclusión parcial de superposición (PA-06), CHECKs y FKs diferidas de C-03.
 * Cada caso corre en una transacción revertida con fechas de 2040 para no dejar ni chocar con datos.
 */
class FlywayV9MigrationIT extends AbstractIntegrationTest {

    private static final String INSERT_TURNO = "INSERT INTO turno (consultorio_id, paciente_id, prestacion_id, "
            + "inicio, fin, estado, origen, pendiente_hasta) "
            + "VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, ?, ?, ?::timestamptz)";

    /** V11 (C-08): un turno AUSENTE lleva siempre la marca y el estado previo. */
    private static final String INSERT_TURNO_AUSENTE = "INSERT INTO turno (consultorio_id, paciente_id, "
            + "prestacion_id, inicio, fin, estado, origen, ausente_marcado_en, estado_previo_ausencia) "
            + "VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, 'AUSENTE', ?, ?::timestamptz, 'RESERVADO')";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    private record Base(Long consultorioId, Long pacienteId, Long prestacionId) {
    }

    private void revertida(java.util.function.Consumer<Base> bloque) {
        tx.executeWithoutResult(status -> {
            Long consultorio = jdbc.queryForObject("SELECT min(id) FROM consultorio", Long.class);
            Long paciente = jdbc.queryForObject("INSERT INTO paciente (consultorio_id, nombre, apellido, dni, "
                    + "telefono, email) VALUES (?, 'Ana', 'Ficticia', '39000001', '+54 11 5555-0000', "
                    + "'ana-v9@ejemplo.test') RETURNING id", Long.class, consultorio);
            Long prestacion = jdbc.queryForObject("SELECT min(id) FROM prestacion", Long.class);
            bloque.accept(new Base(consultorio, paciente, prestacion));
            status.setRollbackOnly();
        });
    }

    private int turno(Base b, String estado, String inicio, String fin) {
        String pendienteHasta = "PENDIENTE_CONFIRMACION".equals(estado) ? "2040-03-04T00:00:00Z" : null;
        if ("AUSENTE".equals(estado)) {
            return jdbc.update(INSERT_TURNO_AUSENTE, b.consultorioId(), b.pacienteId(), b.prestacionId(), inicio, fin,
                    "ODONTOLOGO", fin);
        }
        return jdbc.update(INSERT_TURNO, b.consultorioId(), b.pacienteId(), b.prestacionId(), inicio, fin, estado,
                "ODONTOLOGO", pendienteHasta);
    }

    private static final String T0900 = "2040-03-05T12:00:00Z";
    private static final String T0930 = "2040-03-05T12:30:00Z";
    private static final String T0915 = "2040-03-05T12:15:00Z";
    private static final String T0945 = "2040-03-05T12:45:00Z";
    private static final String T1000 = "2040-03-05T13:00:00Z";

    private static String sqlState(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private Map<String, Object> columna(String nombre) {
        return jdbc.queryForMap("SELECT data_type, udt_name, is_nullable, column_default FROM "
                + "information_schema.columns WHERE table_name = 'turno' AND column_name = ?", nombre);
    }

    @Test
    void elHistorialRegistraLaVersionNueve() {
        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '9'",
                Boolean.class)).isTrue();
    }

    @Test
    void columnasConLosTiposYNulabilidadDeD2() {
        for (String c : List.of("inicio", "fin", "creado_en", "actualizado_en")) {
            assertThat(columna(c)).as(c).containsEntry("data_type", "timestamp with time zone")
                    .containsEntry("is_nullable", "NO");
        }
        assertThat(columna("pendiente_hasta")).containsEntry("data_type", "timestamp with time zone")
                .containsEntry("is_nullable", "YES");
        for (String c : List.of("consultorio_id", "paciente_id", "prestacion_id", "estado", "origen", "version")) {
            assertThat(columna(c)).as(c).containsEntry("is_nullable", "NO");
        }
        assertThat(columna("motivo_cancelacion")).containsEntry("is_nullable", "YES");
        assertThat(columna("cancelado_por")).containsEntry("is_nullable", "YES");
        assertThat(columna("version")).containsEntry("data_type", "bigint");
    }

    @Test
    void indicesDeD2() {
        List<String> indices = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'turno'",
                String.class);
        assertThat(indices).contains("ix_turno_consultorio_inicio", "ix_turno_paciente_inicio",
                "ix_turno_estado_inicio", "ix_turno_prestacion_id", "ex_turno_sin_superposicion");
    }

    @Test
    void clavesForaneasDeTurnoSonRestrict() {
        List<String> reglas = jdbc.queryForList("""
                SELECT rc.delete_rule FROM information_schema.referential_constraints rc
                JOIN information_schema.table_constraints tc ON tc.constraint_name = rc.constraint_name
                WHERE tc.table_name = 'turno'
                """, String.class);
        assertThat(reglas).hasSize(3).containsOnly("RESTRICT");
    }

    @Test
    void turnosContiguosSonAceptados() {
        revertida(b -> {
            assertThat(turno(b, "RESERVADO", T0900, T0930)).isEqualTo(1);
            assertThat(turno(b, "RESERVADO", T0930, T1000)).isEqualTo(1);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVADO", "CONFIRMADO", "PENDIENTE_CONFIRMACION", "ATENDIDO", "AUSENTE"})
    void solapeConUnTurnoQueOcupaAgendaEsRechazadoConExclusion23P01(String estadoExistente) {
        revertida(b -> {
            turno(b, estadoExistente, T0900, T0930);

            assertThatThrownBy(() -> turno(b, "RESERVADO", T0915, T0945))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23P01"))
                    .hasMessageContaining("ex_turno_sin_superposicion");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELADO", "EXPIRADO"})
    void turnosCanceladosOExpiradosLiberanElHueco(String estadoExistente) {
        revertida(b -> {
            turno(b, estadoExistente, T0900, T0930);

            assertThat(turno(b, "RESERVADO", T0900, T0930)).isEqualTo(1);
        });
    }

    @Test
    void cancelarUnTurnoSolapadoEsAceptadoPorqueNoOcupaAgenda() {
        revertida(b -> {
            turno(b, "RESERVADO", T0900, T0930);

            assertThat(turno(b, "CANCELADO", T0900, T0930)).isEqualTo(1);
        });
    }

    @ParameterizedTest
    @EnumSource(EstadoTurno.class)
    void laBaseRechazaElSolapeExactamenteCuandoOcupaAgendaDelEstadoJava(EstadoTurno existente) {
        revertida(b -> {
            turno(b, existente.name(), T0900, T0930);
            boolean rechazado;
            try {
                turno(b, "RESERVADO", T0900, T0930);
                rechazado = false;
            } catch (DataIntegrityViolationException e) {
                rechazado = "23P01".equals(sqlState(e));
            }
            assertThat(rechazado).as("solape contra " + existente).isEqualTo(existente.ocupaAgenda());
        });
    }

    @Test
    void finIgualOAnteriorAInicioEsRechazado() {
        revertida(b -> assertThatThrownBy(() -> turno(b, "RESERVADO", T0930, T0930))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_rango"));
        revertida(b -> assertThatThrownBy(() -> turno(b, "RESERVADO", T0930, T0900))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_rango"));
    }

    @Test
    void pendienteSinVencimientoEsRechazadoYReservadoSinVencimientoEsValido() {
        revertida(b -> assertThatThrownBy(() -> jdbc.update(INSERT_TURNO, b.consultorioId(), b.pacienteId(),
                b.prestacionId(), T0900, T0930, "PENDIENTE_CONFIRMACION", "PUBLICA", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_pendiente_con_vencimiento"));
        revertida(b -> assertThat(turno(b, "RESERVADO", T0900, T0930)).isEqualTo(1));
    }

    @Test
    void estadoOrigenYCanceladoPorFueraDelEnumSonRechazados() {
        revertida(b -> assertThatThrownBy(() -> turno(b, "PERDIDO", T0900, T0930))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_estado"));
        revertida(b -> assertThatThrownBy(() -> jdbc.update(INSERT_TURNO, b.consultorioId(), b.pacienteId(),
                b.prestacionId(), T0900, T0930, "RESERVADO", "TELEFONO", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_origen"));
        revertida(b -> assertThatThrownBy(() -> jdbc.update("INSERT INTO turno (consultorio_id, paciente_id, "
                + "prestacion_id, inicio, fin, estado, origen, cancelado_por) VALUES (?, ?, ?, ?::timestamptz, "
                + "?::timestamptz, 'CANCELADO', 'ODONTOLOGO', 'VECINO')", b.consultorioId(), b.pacienteId(),
                b.prestacionId(), T0900, T0930))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_cancelado_por"));
    }

    @Test
    void motivoDeCancelacionDeMasDe200CaracteresEsRechazadoYUnoDe200EsValido() {
        String sql = "INSERT INTO turno (consultorio_id, paciente_id, prestacion_id, inicio, fin, estado, origen, "
                + "cancelado_por, motivo_cancelacion) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, 'CANCELADO', "
                + "'ODONTOLOGO', 'ODONTOLOGO', ?)";
        revertida(b -> assertThat(jdbc.update(sql, b.consultorioId(), b.pacienteId(), b.prestacionId(), T0900, T0930,
                "x".repeat(200))).isEqualTo(1));
        revertida(b -> assertThatThrownBy(() -> jdbc.update(sql, b.consultorioId(), b.pacienteId(),
                b.prestacionId(), T0900, T0930, "x".repeat(201)))
                .isInstanceOf(DataIntegrityViolationException.class));
    }

    @Test
    void turnoSinConsultorioPacienteOPrestacionExistentesEsRechazadoPorFk() {
        revertida(b -> assertThatThrownBy(() -> jdbc.update(INSERT_TURNO, b.consultorioId(), -1L,
                b.prestacionId(), T0900, T0930, "RESERVADO", "ODONTOLOGO", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23503")));
    }

    @Test
    void valoresPorDefectoDeVersionYTimestamps() {
        revertida(b -> {
            Map<String, Object> fila = jdbc.queryForMap(INSERT_TURNO.replace("VALUES", "VALUES")
                    + " RETURNING version, creado_en, actualizado_en", b.consultorioId(), b.pacienteId(),
                    b.prestacionId(), T0900, T0930, "RESERVADO", "ODONTOLOGO", null);
            assertThat(fila).containsEntry("version", 0L);
            assertThat(fila.get("creado_en")).isNotNull();
            assertThat(fila.get("actualizado_en")).isNotNull();
        });
    }

    // ---- 3.4: FKs diferidas de C-03 ----

    @Test
    void fkNotificacionTurnoEsRestrictYFkTokenAccesoTurnoEsCascade() {
        assertThat(jdbc.queryForObject("SELECT delete_rule FROM information_schema.referential_constraints "
                + "WHERE constraint_name = 'fk_notificacion_turno'", String.class)).isEqualTo("RESTRICT");
        assertThat(jdbc.queryForObject("SELECT delete_rule FROM information_schema.referential_constraints "
                + "WHERE constraint_name = 'fk_token_acceso_turno'", String.class)).isEqualTo("CASCADE");
    }

    @Test
    void notificacionYTokenConTurnoInexistenteSonRechazadosPorFk() {
        revertida(b -> assertThatThrownBy(() -> jdbc.update("INSERT INTO notificacion (consultorio_id, tipo, "
                + "destinatario, turno_id, programada_para) VALUES (?, 'RECORDATORIO', 'p@ejemplo.test', "
                + "999999999, now())", b.consultorioId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23503"))
                .hasMessageContaining("fk_notificacion_turno"));
        revertida(b -> assertThatThrownBy(() -> jdbc.update("INSERT INTO token_acceso (consultorio_id, token_hash, "
                + "proposito, turno_id, vence_en) VALUES (?, decode(repeat('ab', 32), 'hex'), 'CANCELAR_TURNO', "
                + "999999999, now() + interval '1 day')", b.consultorioId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23503"))
                .hasMessageContaining("fk_token_acceso_turno"));
    }

    @Test
    void borrarUnTurnoConNotificacionesEsRechazadoYConTokensLosBorraEnCascada() {
        revertida(b -> {
            turno(b, "RESERVADO", T0900, T0930);
            Long turnoId = jdbc.queryForObject("SELECT max(id) FROM turno", Long.class);
            jdbc.update("INSERT INTO token_acceso (consultorio_id, token_hash, proposito, turno_id, vence_en) "
                    + "VALUES (?, decode(repeat('cd', 32), 'hex'), 'CANCELAR_TURNO', ?, now() + interval '1 day')",
                    b.consultorioId(), turnoId);

            jdbc.update("DELETE FROM turno WHERE id = ?", turnoId);

            assertThat(jdbc.queryForObject("SELECT count(*) FROM token_acceso WHERE turno_id = ?", Integer.class,
                    turnoId)).isZero();
        });
        revertida(b -> {
            turno(b, "RESERVADO", T0900, T0930);
            Long turnoId = jdbc.queryForObject("SELECT max(id) FROM turno", Long.class);
            jdbc.update("INSERT INTO notificacion (consultorio_id, tipo, destinatario, turno_id, programada_para) "
                    + "VALUES (?, 'RECORDATORIO', 'p@ejemplo.test', ?, now())", b.consultorioId(), turnoId);

            assertThatThrownBy(() -> jdbc.update("DELETE FROM turno WHERE id = ?", turnoId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23503"));
        });
    }

    @Test
    void v9NoAgregaIndicesNuevosAlasTablasDeC03() {
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'notificacion'",
                String.class)).containsExactlyInAnyOrder("notificacion_pkey", "idx_notificacion_estado_programada",
                "idx_notificacion_creado_en", "uq_notificacion_turno_tipo", "uq_notificacion_aviso",
                "uq_notificacion_entrada_tipo", "idx_notificacion_turno");
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'token_acceso'",
                String.class)).containsExactlyInAnyOrder("token_acceso_pkey", "uq_token_acceso_hash",
                "idx_token_acceso_turno", "idx_token_acceso_aviso", "idx_token_acceso_entrada");
    }

    // ---- 3.5: evolucion_clinica la agrega C-05 ----

    @Test
    void v9NoReferenciaEvolucionClinicaMasQueEnComentarios() throws IOException {
        String sql = new String(new ClassPathResource("db/migration/V9__turno.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String sinComentarios = sql.lines().filter(l -> !l.stripLeading().startsWith("--"))
                .reduce("", (a, l) -> a + l + "\n");

        assertThat(sinComentarios).doesNotContainIgnoringCase("evolucion_clinica");
        assertThat(sql).as("el comentario deja dicho que la FK la agrega C-05").contains("evolucion_clinica")
                .contains("C-05");
    }
}
