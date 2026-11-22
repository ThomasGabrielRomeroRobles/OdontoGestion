package ar.edu.odontogestion.application.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** buscarOCrearPorDni contra PostgreSQL real. Transacción revertida por test. */
@Transactional
@Import(FixedClockTestConfig.class)
class PacienteServiceIT extends AbstractIntegrationTest {

    @Autowired
    private PacienteService servicio;

    @Autowired
    private PacienteRepository pacientes;

    @Autowired
    private ConsultorioActual consultorioActual;

    @Autowired
    private JdbcTemplate jdbc;

    private static DatosPaciente datos(String dni, String email) {
        return new DatosPaciente("Ana", "Pérez", dni, null, "+54 11 5555-0001", email, null);
    }

    @Test
    void dniNuevoCreaAlPacienteConLosTimestampsDelReloj() {
        ResultadoBuscarOCrear r = servicio.buscarOCrearPorDni(datos("30.999.888", " Ana@Ejemplo.TEST "));

        assertThat(r.creado()).isTrue();
        assertThat(r.paciente().id()).isNotNull();
        assertThat(r.paciente().dni()).isEqualTo("30999888");
        assertThat(r.paciente().email()).isEqualTo("ana@ejemplo.test");
        assertThat(r.paciente().activo()).isTrue();
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT consultorio_id, creado_en, actualizado_en FROM paciente WHERE id = ?", r.paciente().id());
        assertThat(fila.get("consultorio_id")).isEqualTo(consultorioActual.id());
        assertThat(((Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(FixedClockTestConfig.AHORA);
        assertThat(((Timestamp) fila.get("actualizado_en")).toInstant()).isEqualTo(FixedClockTestConfig.AHORA);
    }

    @Test
    void dniExistenteDevuelveElMismoPacienteSinSobrescribirElContacto() {
        PacienteVista ana = servicio.crear(datos("30123456", "ana@ejemplo.test"));

        ResultadoBuscarOCrear r = servicio.buscarOCrearPorDni(
                new DatosPaciente("Otra", "Persona", "30.123.456", null, "999", "otra@ejemplo.test", "X 1"));

        assertThat(r.creado()).isFalse();
        assertThat(r.paciente().id()).isEqualTo(ana.id());
        assertThat(jdbc.queryForObject("SELECT email FROM paciente WHERE id = ?", String.class, ana.id()))
                .isEqualTo("ana@ejemplo.test");
        assertThat(jdbc.queryForObject("SELECT nombre FROM paciente WHERE id = ?", String.class, ana.id()))
                .isEqualTo("Ana");
    }

    @Test
    void elInsertSiNoExisteDevuelveVacioAnteUnDniDuplicadoSinRomperLaTransaccion() {
        servicio.crear(datos("30123458", "ana@ejemplo.test"));

        assertThat(pacientes.insertarSiNoExiste(consultorioActual.id(), "B", "C", "30123458", null, "1",
                "b@ejemplo.test", "", FixedClockTestConfig.AHORA)).isEmpty();
        // la transacción sigue usable
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = '30123458'", Integer.class))
                .isEqualTo(1);
    }
}
