package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Import(FixedClockTestConfig.class)
class ClockIT extends AbstractIntegrationTest {

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void relojFijoReemplazaAlRelojDeLaAplicacion() {
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-11-20T12:00:00Z"));
        assertThat(clock.instant()).isEqualTo(clock.instant());
    }

    @Test
    void hibernateUsaUtcParaLosInstantes() {
        assertThat(entityManagerFactory.getProperties()).containsEntry("hibernate.jdbc.time_zone", "UTC");
    }

    @Test
    void instanteEscritoEnTimestamptzSeLeeSinDesplazamientoConJvmFueraDeUtc() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        try (var ds = new SingleConnectionDataSource(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                        .getConnection(), true)) {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TEMP TABLE instante_prueba (id int, momento timestamptz)");
            Instant escrito = Instant.parse("2026-11-20T23:30:00Z");
            Instant otro = Instant.parse("2027-01-01T00:00:00Z");
            jdbc.update("INSERT INTO instante_prueba VALUES (1, ?), (2, ?)",
                    OffsetDateTime.ofInstant(escrito, ZoneOffset.UTC), OffsetDateTime.ofInstant(otro, ZoneOffset.UTC));

            Instant leido1 = jdbc.queryForObject("SELECT momento FROM instante_prueba WHERE id = 1",
                    OffsetDateTime.class).toInstant();
            Instant leido2 = jdbc.queryForObject("SELECT momento FROM instante_prueba WHERE id = 2",
                    OffsetDateTime.class).toInstant();

            assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Tokyo");
            assertThat(leido1).isEqualTo(escrito);
            assertThat(leido2).isEqualTo(otro);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
