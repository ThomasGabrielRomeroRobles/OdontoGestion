package ar.edu.odontogestion;

import ar.edu.odontogestion.support.TurnosFicticios;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** V7 (C-03): token_acceso. Ids de referencia ficticios >= 900000 y limpieza al final (sin FK hasta C-07/C-10). */
@TurnosFicticios
class FlywayV7MigrationIT extends AbstractIntegrationTest {

    private static final String INSERT = "INSERT INTO token_acceso (consultorio_id, token_hash, proposito, "
            + "turno_id, aviso_id, entrada_lista_espera_id, vence_en) "
            + "VALUES (?, ?, ?, ?, ?, ?, now() + interval '1 day')";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConsultorioActual consultorioActual;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM token_acceso WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    private static byte[] hash(String semilla) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(semilla.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int insertar(byte[] hash, String proposito, Long turno, Long aviso, Long entrada) {
        return jdbc.update(INSERT, consultorioActual.id(), hash, proposito, turno, aviso, entrada);
    }

    @Test
    void versionSieteAplicadaYColumnasConTiposEsperados() {
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '7'", Boolean.class)).isTrue();
        Map<String, Map<String, Object>> columnas = new java.util.HashMap<>();
        jdbc.queryForList("SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'token_acceso'").forEach(f -> columnas.put((String) f.get("column_name"), f));
        assertThat(columnas.get("consultorio_id")).containsEntry("is_nullable", "NO");
        assertThat(columnas.get("token_hash")).containsEntry("data_type", "bytea").containsEntry("is_nullable", "NO");
        for (String c : List.of("vence_en", "creado_en", "actualizado_en")) {
            assertThat(columnas.get(c)).containsEntry("data_type", "timestamp with time zone")
                    .containsEntry("is_nullable", "NO");
        }
        assertThat(columnas.get("usado_en")).containsEntry("data_type", "timestamp with time zone")
                .containsEntry("is_nullable", "YES");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints "
                + "WHERE table_name = 'token_acceso' AND constraint_type = 'FOREIGN KEY'", Integer.class))
                .as("FK a consultorio y a turno (V9, C-07); las de aviso/entrada llegan con C-10").isEqualTo(2);
    }

    @Test
    void insercionesValidasConCadaPropositoYSuReferenciaSonAceptadas() {
        assertThat(insertar(hash("a1"), "CONFIRMAR_SOLICITUD", 900001L, null, null)).isEqualTo(1);
        assertThat(insertar(hash("a2"), "CONFIRMAR_TURNO", 900002L, null, null)).isEqualTo(1);
        assertThat(insertar(hash("a3"), "CANCELAR_TURNO", 900003L, null, null)).isEqualTo(1);
        assertThat(insertar(hash("a4"), "ACEPTAR_AVISO", null, 900004L, null)).isEqualTo(1);
        assertThat(insertar(hash("a5"), "CONFIRMAR_LISTA_ESPERA", null, null, 900005L)).isEqualTo(1);
    }

    @Test
    void ningunaOVariasReferenciasSonRechazadasPorLaBase() {
        assertThatThrownBy(() -> insertar(hash("b1"), "CONFIRMAR_TURNO", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(hash("b2"), "CONFIRMAR_TURNO", 900001L, 900001L, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(hash("b3"), "ACEPTAR_AVISO", 900001L, 900002L, 900003L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void referenciaIncoherenteConElPropositoEsRechazadaPorLaBase() {
        assertThatThrownBy(() -> insertar(hash("c1"), "ACEPTAR_AVISO", 900001L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(hash("c2"), "CANCELAR_TURNO", null, null, 900001L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(hash("c3"), "CONFIRMAR_LISTA_ESPERA", 900001L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void propositoDesconocidoYHashDeLongitudIncorrectaSonRechazados() {
        assertThatThrownBy(() -> insertar(hash("d1"), "OTRO", 900001L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(new byte[16], "CONFIRMAR_TURNO", 900001L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void hashDuplicadoEsRechazadoPorUnicidad() {
        insertar(hash("e1"), "CONFIRMAR_TURNO", 900001L, null, null);
        assertThatThrownBy(() -> insertar(hash("e1"), "CANCELAR_TURNO", 900002L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existenIndicesParcialesPorCadaReferencia() {
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'token_acceso'", String.class);
        assertThat(indices).contains("idx_token_acceso_turno", "idx_token_acceso_aviso", "idx_token_acceso_entrada");
    }
}
