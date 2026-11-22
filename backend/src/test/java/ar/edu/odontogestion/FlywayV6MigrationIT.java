package ar.edu.odontogestion;

import ar.edu.odontogestion.support.TurnosFicticios;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V6 (C-03): outbox {@code notificacion}. Ids de referencia ficticios >= 900000 y limpieza al final
 * (FK a turno desde V9 de C-07; las de aviso/entrada llegan con C-10).
 */
@TurnosFicticios
class FlywayV6MigrationIT extends AbstractIntegrationTest {

    private static final String INSERT = "INSERT INTO notificacion (consultorio_id, tipo, destinatario, "
            + "turno_id, aviso_id, entrada_lista_espera_id, programada_para) "
            + "VALUES (?, ?, 'paciente@ejemplo.test', ?, ?, ?, now())";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConsultorioActual consultorioActual;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM notificacion "
                + "WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000 "
                + "OR destinatario = 'sin-referencia@ejemplo.test'");
    }

    private int insertar(String tipo, Long turno, Long aviso, Long entrada) {
        return jdbc.update(INSERT, consultorioActual.id(), tipo, turno, aviso, entrada);
    }

    @Test
    void versionSeisAplicadaYColumnasConTiposEsperados() {
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '6'", Boolean.class)).isTrue();
        Map<String, Map<String, Object>> columnas = new HashMap<>();
        jdbc.queryForList("SELECT column_name, data_type, udt_name, is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'notificacion'").forEach(f -> columnas.put((String) f.get("column_name"), f));

        assertThat(columnas.get("consultorio_id")).containsEntry("is_nullable", "NO");
        assertThat(columnas.get("destinatario")).containsEntry("udt_name", "citext").containsEntry("is_nullable", "NO");
        assertThat(columnas.get("datos")).containsEntry("data_type", "jsonb").containsEntry("is_nullable", "NO");
        assertThat(columnas.get("enlaces")).containsEntry("data_type", "jsonb").containsEntry("is_nullable", "NO");
        for (String c : List.of("programada_para", "creado_en", "actualizado_en")) {
            assertThat(columnas.get(c)).containsEntry("data_type", "timestamp with time zone")
                    .containsEntry("is_nullable", "NO");
        }
        assertThat(columnas.get("enviada_en")).containsEntry("data_type", "timestamp with time zone")
                .containsEntry("is_nullable", "YES");
        assertThat(columnas.get("error")).containsEntry("is_nullable", "YES");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints "
                + "WHERE table_name = 'notificacion' AND constraint_type = 'FOREIGN KEY'", Integer.class))
                .as("FK a consultorio y a turno (V9, C-07); las de aviso/entrada llegan con C-10").isEqualTo(2);
    }

    @Test
    void valoresPorDefectoDeEstadoContadoresCanalYJson() {
        insertar("CONFIRMACION_TURNO", 900001L, null, null);
        Map<String, Object> fila = jdbc.queryForMap("SELECT estado, canal, intentos, reintentos_manuales, "
                + "datos::text AS datos, enlaces::text AS enlaces FROM notificacion WHERE turno_id = 900001");
        assertThat(fila).containsEntry("estado", "PENDIENTE")
                .containsEntry("canal", "EMAIL")
                .containsEntry("intentos", 0)
                .containsEntry("reintentos_manuales", 0)
                .containsEntry("datos", "{}")
                .containsEntry("enlaces", "[]");
    }

    @Test
    void tipoOEstadoDesconocidoEIntentosNegativosSonRechazados() {
        assertThatThrownBy(() -> insertar("OTRO", 900001L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO notificacion (consultorio_id, tipo, destinatario, turno_id, "
                + "estado, programada_para) VALUES (?, 'RECORDATORIO', 'p@ejemplo.test', 900002, 'PERDIDA', now())",
                consultorioActual.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO notificacion (consultorio_id, tipo, destinatario, turno_id, "
                + "intentos, programada_para) VALUES (?, 'RECORDATORIO', 'p@ejemplo.test', 900003, -1, now())",
                consultorioActual.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void referenciaAusenteOIncoherenteConElTipoEsRechazada() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO notificacion (consultorio_id, tipo, destinatario, "
                + "programada_para) VALUES (?, 'RECORDATORIO', 'sin-referencia@ejemplo.test', now())",
                consultorioActual.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("AVISO_HUECO", 900001L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("RECORDATORIO", null, 900001L, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("CONFIRMAR_LISTA_ESPERA", 900001L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("RECORDATORIO", 900001L, 900002L, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insercionesValidasConCadaTipoYSuReferenciaSonAceptadas() {
        for (String tipo : List.of("CONFIRMAR_SOLICITUD", "CONFIRMACION_TURNO", "RECORDATORIO", "CANCELACION",
                "AUSENTE_REPROGRAMAR")) {
            assertThat(insertar(tipo, 900010L, null, null)).as(tipo).isEqualTo(1);
        }
        assertThat(insertar("AVISO_HUECO", null, 900011L, null)).isEqualTo(1);
        assertThat(insertar("CONFIRMAR_LISTA_ESPERA", null, null, 900012L)).isEqualTo(1);
    }

    @Test
    void segundoRecordatorioDelMismoTurnoEsRechazadoPorIndiceUnico() {
        insertar("RECORDATORIO", 900020L, null, null);
        assertThatThrownBy(() -> insertar("RECORDATORIO", 900020L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void segundoAvisoHuecoDelMismoAvisoEsRechazado() {
        insertar("AVISO_HUECO", null, 900021L, null);
        assertThatThrownBy(() -> insertar("AVISO_HUECO", null, 900021L, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void segundaConfirmarListaEsperaDeLaMismaEntradaEsRechazada() {
        insertar("CONFIRMAR_LISTA_ESPERA", null, null, 900022L);
        assertThatThrownBy(() -> insertar("CONFIRMAR_LISTA_ESPERA", null, null, 900022L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dosConfirmacionesDelMismoTurnoSonAceptadas() {
        assertThat(insertar("CONFIRMACION_TURNO", 900023L, null, null)).isEqualTo(1);
        assertThat(insertar("CONFIRMACION_TURNO", 900023L, null, null)).isEqualTo(1);
    }

    @Test
    void existenIndicesDeProcesamientoListadoEIdempotencia() {
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'notificacion'", String.class);
        assertThat(indices).contains("idx_notificacion_estado_programada", "idx_notificacion_creado_en",
                "uq_notificacion_turno_tipo", "uq_notificacion_aviso", "uq_notificacion_entrada_tipo");
    }
}
