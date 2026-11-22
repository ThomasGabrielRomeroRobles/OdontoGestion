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

/** V8 (C-04): tabla paciente. Cada caso corre en una transacción revertida para no dejar datos. */
class FlywayV8MigrationIT extends AbstractIntegrationTest {

    private static final String INSERT_MINIMO = "INSERT INTO paciente (consultorio_id, nombre, apellido, dni, "
            + "telefono, email) VALUES (?, 'Ana', 'Ficticia', ?, '+54 11 5555-0000', 'ana@ejemplo.test')";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    private void revertida(Runnable bloque) {
        tx.executeWithoutResult(status -> {
            bloque.run();
            status.setRollbackOnly();
        });
    }

    private Long consultorioDemo() {
        return jdbc.queryForObject("SELECT min(id) FROM consultorio", Long.class);
    }

    private Map<String, Object> columna(String nombre) {
        return jdbc.queryForMap("SELECT data_type, udt_name, is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'paciente' AND column_name = ?", nombre);
    }

    @Test
    void elHistorialRegistraLaVersionOcho() {
        Boolean ok = jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '8'",
                Boolean.class);
        assertThat(ok).isTrue();
    }

    @Test
    void consultorioIdEsNoNuloYTieneClaveForaneaAConsultorio() {
        assertThat(columna("consultorio_id")).containsEntry("is_nullable", "NO");
        Integer fks = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_name = tc.constraint_name
                WHERE tc.table_name = 'paciente' AND tc.constraint_type = 'FOREIGN KEY'
                  AND kcu.column_name = 'consultorio_id' AND ccu.table_name = 'consultorio'
                """, Integer.class);
        assertThat(fks).isEqualTo(1);
    }

    @Test
    void tiposDeColumnas() {
        for (String c : List.of("creado_en", "actualizado_en")) {
            assertThat(columna(c)).as(c).containsEntry("data_type", "timestamp with time zone")
                    .containsEntry("is_nullable", "NO");
        }
        assertThat(columna("fecha_nacimiento")).containsEntry("data_type", "date")
                .containsEntry("is_nullable", "YES");
        assertThat(columna("email")).containsEntry("udt_name", "citext");
    }

    @Test
    void insercionMinimaTomaLosDefaults() {
        revertida(() -> {
            Map<String, Object> fila = jdbc.queryForMap(INSERT_MINIMO
                    + " RETURNING cantidad_ausencias, activo, direccion, fecha_nacimiento", consultorioDemo(),
                    "30111222");
            assertThat(fila).containsEntry("cantidad_ausencias", 0).containsEntry("activo", true)
                    .containsEntry("direccion", "");
            assertThat(fila.get("fecha_nacimiento")).isNull();
        });
    }

    @Test
    void pacienteSinConsultorioEsRechazado() {
        revertida(() -> assertThatThrownBy(() -> jdbc.update(INSERT_MINIMO, null, "30111223"))
                .isInstanceOf(DataIntegrityViolationException.class));
    }

    @Test
    void dniDuplicadoEnElMismoConsultorioEsRechazado() {
        revertida(() -> {
            jdbc.update(INSERT_MINIMO, consultorioDemo(), "30111224");
            assertThatThrownBy(() -> jdbc.update(INSERT_MINIMO, consultorioDemo(), "30111224"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uq_paciente_consultorio_dni");
        });
    }

    @Test
    void mismoDniEnOtroConsultorioEsAceptado() {
        revertida(() -> {
            Long otro = jdbc.queryForObject("INSERT INTO consultorio (nombre) VALUES ('Otro C-04') RETURNING id",
                    Long.class);
            jdbc.update(INSERT_MINIMO, consultorioDemo(), "30111225");
            assertThat(jdbc.update(INSERT_MINIMO, otro, "30111225")).isEqualTo(1);
        });
    }

    @Test
    void dniConFormatoInvalidoEsRechazado() {
        for (String dni : List.of("123456", "123456789", "12A45678", "30.111.226")) {
            revertida(() -> assertThatThrownBy(() -> jdbc.update(INSERT_MINIMO, consultorioDemo(), dni))
                    .as(dni).isInstanceOf(DataIntegrityViolationException.class));
        }
    }

    @Test
    void dniDeSieteYOchoDigitosEsAceptado() {
        revertida(() -> {
            assertThat(jdbc.update(INSERT_MINIMO, consultorioDemo(), "7111227")).isEqualTo(1);
            assertThat(jdbc.update(INSERT_MINIMO, consultorioDemo(), "30111227")).isEqualTo(1);
        });
    }

    @Test
    void cantidadDeAusenciasNegativaEsRechazada() {
        revertida(() -> assertThatThrownBy(() -> jdbc.update("INSERT INTO paciente (consultorio_id, nombre, "
                + "apellido, dni, telefono, email, cantidad_ausencias) VALUES (?, 'A', 'B', '30111228', '1', "
                + "'a@ejemplo.test', -1)", consultorioDemo()))
                .isInstanceOf(DataIntegrityViolationException.class));
    }
}
