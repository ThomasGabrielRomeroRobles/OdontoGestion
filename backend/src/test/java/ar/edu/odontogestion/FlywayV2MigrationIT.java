package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class FlywayV2MigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    /** Ejecuta el bloque en una transacción que siempre se revierte (la base es compartida). */
    private void revertida(Runnable bloque) {
        tx.executeWithoutResult(status -> {
            bloque.run();
            status.setRollbackOnly();
        });
    }

    private long insertarConsultorio(String nombre) {
        return jdbc.queryForObject("INSERT INTO consultorio (nombre) VALUES (?) RETURNING id", Long.class, nombre);
    }

    private void insertarUsuario(Object consultorioId, String email) {
        jdbc.update("INSERT INTO usuario (consultorio_id, email, password_hash, nombre, apellido) "
                + "VALUES (?, ?, 'hash', 'N', 'A')", consultorioId, email);
    }

    @Test
    void existenLasTablasConsultorioYUsuario() {
        List<String> tablas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'", String.class);
        assertThat(tablas).contains("consultorio", "usuario");
    }

    @Test
    void emailEsCitextUnico() {
        String tipo = jdbc.queryForObject("SELECT udt_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = 'usuario' AND column_name = 'email'", String.class);
        assertThat(tipo).isEqualTo("citext");

        revertida(() -> {
            long id = insertarConsultorio("C");
            insertarUsuario(id, "odontologo@ejemplo.test");
            assertThatThrownBy(() -> insertarUsuario(id, "Odontologo@Ejemplo.TEST"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        });
    }

    @Test
    void emailsDistintosConviven() {
        revertida(() -> {
            long id = insertarConsultorio("C");
            insertarUsuario(id, "uno@ejemplo.test");
            insertarUsuario(id, "dos@ejemplo.test");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE consultorio_id = ?",
                    Integer.class, id)).isEqualTo(2);
        });
    }

    @Test
    void consultorioInexistenteONuloEsRechazado() {
        revertida(() -> assertThatThrownBy(() -> insertarUsuario(987654321L, "a@ejemplo.test"))
                .isInstanceOf(DataIntegrityViolationException.class));
        revertida(() -> assertThatThrownBy(() -> insertarUsuario(null, "b@ejemplo.test"))
                .isInstanceOf(DataIntegrityViolationException.class));
    }

    @Test
    void borrarConsultorioConUsuarioFallaPorRestrict() {
        revertida(() -> {
            long id = insertarConsultorio("C");
            insertarUsuario(id, "c@ejemplo.test");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM consultorio WHERE id = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        });
    }

    @Test
    void borrarConsultorioSinUsuariosFunciona() {
        revertida(() -> {
            long id = insertarConsultorio("C");
            assertThat(jdbc.update("DELETE FROM consultorio WHERE id = ?", id)).isEqualTo(1);
        });
    }

    @Test
    void existeElIndiceDeLaClaveForanea() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_usuario_consultorio_id'", Integer.class);
        assertThat(n).isEqualTo(1);
    }

    @Test
    void lasColumnasDeFechaSonTimestamptz() {
        List<String> noTz = jdbc.queryForList(
                "SELECT table_name || '.' || column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name IN ('consultorio', 'usuario') "
                        + "AND column_name IN ('creado_en', 'actualizado_en', 'ultimo_acceso') "
                        + "AND data_type <> 'timestamp with time zone'", String.class);
        assertThat(noTz).isEmpty();
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name IN ('consultorio', 'usuario') "
                        + "AND column_name IN ('creado_en', 'actualizado_en', 'ultimo_acceso')", Integer.class);
        assertThat(total).isEqualTo(5);
    }

    /** Aplica solo hasta V2 en un esquema aislado: lo que vea después es obra de la migración, no del seed Java. */
    private String migrarHastaV2EnEsquemaAislado() {
        String schema = "chk_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema, "public")
                .createSchemas(true)
                .target("2")
                .load()
                .migrate();
        return schema;
    }

    @Test
    void laMigracionV2InsertaElConsultorioDemoConZonaHorariaPorDefecto() {
        String schema = migrarHastaV2EnEsquemaAislado();
        try {
            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT nombre, zona_horaria FROM " + schema + ".consultorio");
            assertThat(filas).hasSize(1);
            assertThat(filas.get(0)).containsEntry("nombre", "Consultorio Demo")
                    .containsEntry("zona_horaria", "America/Argentina/Buenos_Aires");
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void laMigracionV2NoCreaNingunUsuario() {
        String schema = migrarHastaV2EnEsquemaAislado();
        try {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".usuario", Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void nombreDeConsultorioVacioViolaElCheck() {
        revertida(() -> assertThatThrownBy(() -> insertarConsultorio(""))
                .isInstanceOf(DataIntegrityViolationException.class));
    }
}
