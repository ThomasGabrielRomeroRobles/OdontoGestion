package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

class MigrationBehaviorIT extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void segundoArranqueNoReaplicaMigraciones() {
        var resultado = flyway.migrate();

        assertThat(resultado.migrationsExecuted).isZero();
        assertThat(resultado.success).isTrue();
    }

    @Test
    void migracionAplicadaModificadaFallaPorChecksum(@TempDir Path dir) throws Exception {
        Path script = dir.resolve("V1__extensiones.sql");
        Files.writeString(script, "SELECT 1;");
        Flyway aislado = flywayAislado(dir);
        aislado.migrate();

        Files.writeString(script, "SELECT 2;");

        assertThatThrownBy(aislado::validate)
                .isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void migracionSinCambiosPasaLaValidacion(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("V1__extensiones.sql"), "SELECT 1;");
        Flyway aislado = flywayAislado(dir);
        aislado.migrate();

        assertThat(aislado.validateWithResult().validationSuccessful).isTrue();
    }

    private static Flyway flywayAislado(Path dir) {
        String schema = "chk_" + UUID.randomUUID().toString().replace("-", "");
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .createSchemas(true)
                .locations("filesystem:" + dir.toAbsolutePath())
                .load();
    }
}
