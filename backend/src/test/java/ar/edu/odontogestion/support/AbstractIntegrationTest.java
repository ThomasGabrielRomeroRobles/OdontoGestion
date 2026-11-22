package ar.edu.odontogestion.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base única de pruebas de integración: PostgreSQL real (nunca H2) con un contenedor singleton
 * compartido por todo el JVM de tests para maximizar el cache de contextos de Spring.
 * Prohibido @DirtiesContext y variantes de @MockBean aquí.
 * {@code app.scheduling.enabled=false}: los jobs {@code @Scheduled} (C-03 en adelante) no corren en segundo
 * plano; los tests invocan el procesamiento directamente.
 */
@SpringBootTest(properties = {
        "app.bootstrap.odontologo-password=Ficticia-Test-123",
        "app.scheduling.enabled=false"})
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }
}
