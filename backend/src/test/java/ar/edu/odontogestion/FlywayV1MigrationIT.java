package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class FlywayV1MigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void versionUnoAplicadaConExito() {
        Boolean success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);
        assertThat(success).isTrue();
    }

    @Test
    void btreeGistInstalada() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void citextCompararSinDistinguirMayusculas() {
        Boolean igual = jdbc.queryForObject(
                "SELECT 'Ana@Example.com'::citext = 'ana@example.com'::citext", Boolean.class);
        assertThat(igual).isTrue();
        Boolean distintos = jdbc.queryForObject(
                "SELECT 'ana@example.com'::citext = 'otra@example.com'::citext", Boolean.class);
        assertThat(distintos).isFalse();
    }

    @Test
    void restriccionDeExclusionConIgualdadRechazaSolapamiento() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.execute("CREATE TEMP TABLE reserva_prueba (grupo int, rango tstzrange, "
                    + "EXCLUDE USING gist (grupo WITH =, rango WITH &&)) ON COMMIT DROP");
            jdbc.execute("INSERT INTO reserva_prueba VALUES "
                    + "(1, tstzrange('2026-11-20 10:00+00', '2026-11-20 11:00+00'))");
            jdbc.execute("INSERT INTO reserva_prueba VALUES "
                    + "(1, tstzrange('2026-11-20 10:30+00', '2026-11-20 11:30+00'))");
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void gruposDistintosNoSeExcluyenEntreSi() {
        Integer filas = tx.execute(status -> {
            jdbc.execute("CREATE TEMP TABLE reserva_prueba (grupo int, rango tstzrange, "
                    + "EXCLUDE USING gist (grupo WITH =, rango WITH &&)) ON COMMIT DROP");
            jdbc.execute("INSERT INTO reserva_prueba VALUES "
                    + "(1, tstzrange('2026-11-20 10:00+00', '2026-11-20 11:00+00'))");
            jdbc.execute("INSERT INTO reserva_prueba VALUES "
                    + "(2, tstzrange('2026-11-20 10:30+00', '2026-11-20 11:30+00'))");
            return jdbc.queryForObject("SELECT count(*) FROM reserva_prueba", Integer.class);
        });
        assertThat(filas).isEqualTo(2);
    }

    @Test
    void soloExisteLaTablaDeHistorialDeMigraciones() {
        List<String> tablas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);
        assertThat(tablas).containsExactly("flyway_schema_history");
    }
}
