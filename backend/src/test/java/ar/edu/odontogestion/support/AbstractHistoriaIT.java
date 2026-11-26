package ar.edu.odontogestion.support;

/**
 * Base de los ITs de historia clínica (C-05). Hereda los fixtures de {@link AbstractTurnoIT} (pacientes de prueba
 * con DNI {@code 38xxxxxx}, reloj fijo) y, con el hook de limpieza de la base, borra antes y después las
 * historias de esos pacientes, porque sus FK ({@code ON DELETE RESTRICT}) impedirían la limpieza de pacientes y turnos de la clase base. Los usuarios extra
 * se crean con el prefijo {@code it-hc} y se borran acá.
 */
public abstract class AbstractHistoriaIT extends AbstractTurnoIT {

    @Override
    protected void limpiarDependientesDePrueba() {
        String historias = "SELECT h.id FROM historia_clinica h JOIN paciente p ON p.id = h.paciente_id "
                + "WHERE p.dni LIKE '38%'";
        jdbc.update("DELETE FROM evolucion_clinica WHERE historia_id IN (" + historias + ")");
        jdbc.update("DELETE FROM pieza_dental_estado WHERE historia_id IN (" + historias + ")");
        jdbc.update("DELETE FROM anamnesis WHERE historia_id IN (" + historias + ")");
        jdbc.update("DELETE FROM historia_clinica WHERE id IN (" + historias + ")");
        jdbc.update("DELETE FROM usuario WHERE email LIKE 'it-hc-%'");
    }

    protected Long usuarioSeedId() {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?::citext", Long.class, EMAIL_SEED);
    }

    protected Long insertarHistoria(Long pacienteId) {
        return jdbc.queryForObject("INSERT INTO historia_clinica (consultorio_id, paciente_id) "
                + "VALUES (?, ?) RETURNING id", Long.class, consultorioId(), pacienteId);
    }
}
