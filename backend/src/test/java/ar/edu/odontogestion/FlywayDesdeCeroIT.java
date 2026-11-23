package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Flyway aplica V1..V9 en orden sobre una base realmente vacía (contenedor propio, no el compartido ni reutilizado):
 * V9 (C-07) no depende de datos residuales de los ITs. V5 no existe (C-04 se renumeró a V8).
 */
class FlywayDesdeCeroIT {

    @Test
    void aplicaTodasLasMigracionesEnOrdenSobreUnaBaseVacia() throws Exception {
        try (PostgreSQLContainer<?> vacia = new PostgreSQLContainer<>("postgres:16-alpine")) {
            vacia.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(vacia.getJdbcUrl(), vacia.getUsername(), vacia.getPassword())
                    .locations("classpath:db/migration").load();
            flyway.migrate();

            try (Connection c = DriverManager.getConnection(vacia.getJdbcUrl(), vacia.getUsername(),
                    vacia.getPassword()); Statement s = c.createStatement()) {
                List<String> versiones = new ArrayList<>();
                try (ResultSet rs = s.executeQuery("SELECT version FROM flyway_schema_history WHERE success "
                        + "AND version IS NOT NULL ORDER BY installed_rank")) {
                    while (rs.next()) {
                        versiones.add(rs.getString(1));
                    }
                }
                assertThat(versiones).containsExactly("1", "2", "3", "4", "6", "7", "8", "9");
                try (ResultSet rs = s.executeQuery("SELECT count(*) FROM pg_constraint WHERE conname IN "
                        + "('ex_turno_sin_superposicion', 'fk_notificacion_turno', 'fk_token_acceso_turno')")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(3);
                }
            }
        }
    }
}
