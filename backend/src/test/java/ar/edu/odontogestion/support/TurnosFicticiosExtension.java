package ar.edu.odontogestion.support;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Siembra / limpia los turnos ficticios con ids {@code 900000..900999}. Corre antes de los {@code @BeforeEach} y
 * después de los {@code @AfterEach} de la clase de test, así que las propias limpiezas de notificaciones y tokens
 * de cada test siguen funcionando; esta clase además las repite por si el test las dejó.
 */
public class TurnosFicticiosExtension implements BeforeEachCallback, AfterEachCallback {

    static final long PRIMER_ID = 900_000L;
    static final long ULTIMO_ID = 900_999L;
    private static final String DNI_FICTICIO = "39999999";

    @Override
    public void beforeEach(ExtensionContext context) {
        JdbcTemplate jdbc = jdbc(context);
        limpiar(jdbc);
        jdbc.update("INSERT INTO paciente (consultorio_id, nombre, apellido, dni, telefono, email) "
                + "SELECT min(id), 'Fixture', 'Ficticia', '" + DNI_FICTICIO + "', '+54 11 5555-9999', "
                + "'fixture-turnos@ejemplo.test' FROM consultorio");
        jdbc.update("INSERT INTO turno (id, consultorio_id, paciente_id, prestacion_id, inicio, fin, estado, origen) "
                + "OVERRIDING SYSTEM VALUE "
                + "SELECT g, c.id, p.id, pr.id, "
                + "timestamptz '2045-01-01 00:00:00+00' + (g - " + PRIMER_ID + ") * interval '1 hour', "
                + "timestamptz '2045-01-01 00:30:00+00' + (g - " + PRIMER_ID + ") * interval '1 hour', "
                + "'RESERVADO', 'ODONTOLOGO' "
                + "FROM generate_series(" + PRIMER_ID + ", " + ULTIMO_ID + ") g "
                + "CROSS JOIN (SELECT min(id) AS id FROM consultorio) c "
                + "CROSS JOIN (SELECT id FROM paciente WHERE dni = '" + DNI_FICTICIO + "') p "
                + "CROSS JOIN (SELECT min(id) AS id FROM prestacion) pr");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        limpiar(jdbc(context));
    }

    private static void limpiar(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM notificacion WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= "
                + PRIMER_ID);
        jdbc.update("DELETE FROM token_acceso WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= "
                + PRIMER_ID);
        jdbc.update("DELETE FROM turno WHERE id BETWEEN " + PRIMER_ID + " AND " + ULTIMO_ID);
        jdbc.update("DELETE FROM paciente WHERE dni = '" + DNI_FICTICIO + "'");
    }

    private static JdbcTemplate jdbc(ExtensionContext context) {
        return SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
    }
}
