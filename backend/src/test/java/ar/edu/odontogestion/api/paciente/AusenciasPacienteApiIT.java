package ar.edu.odontogestion.api.paciente;

import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** {@code GET /api/v1/pacientes/{id}/ausencias} (US-061, C-08 design D6) con sesión y PostgreSQL reales. */
class AusenciasPacienteApiIT extends AbstractTurnoIT {

    private Cliente c;
    private Long paciente;
    private Long prestacion;
    private String nombrePrestacion;

    @BeforeEach
    void iniciar() throws Exception {
        c = iniciarSesionSeed();
        paciente = crearPaciente();
        prestacion = crearPrestacion(30);
        nombrePrestacion = jdbc.queryForObject("SELECT nombre FROM prestacion WHERE id = ?", String.class, prestacion);
    }

    private ResultActions historial(Object pacienteId) throws Exception {
        return mockMvc.perform(get("/api/v1/pacientes/" + pacienteId + "/ausencias").session(c.session()));
    }

    private Long turno(EstadoTurno estado, String inicioUtc) {
        Instant inicio = Instant.parse(inicioUtc);
        return insertarTurno(paciente, prestacion, estado, inicio, inicio.plus(Duration.ofMinutes(30)));
    }

    @Test
    void pacienteConAusenciasDevuelveCantidadYLaMasRecientePrimero() throws Exception {
        Long del3 = turno(EstadoTurno.AUSENTE, "2026-11-03T13:00:00Z");
        Long del10 = turno(EstadoTurno.AUSENTE, "2026-11-10T13:00:00Z");
        turno(EstadoTurno.ATENDIDO, "2026-11-17T13:00:00Z");
        jdbc.update("UPDATE paciente SET cantidad_ausencias = 2 WHERE id = ?", paciente);

        historial(paciente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(2))
                .andExpect(jsonPath("$.ausencias.length()").value(2))
                .andExpect(jsonPath("$.ausencias[0].turnoId").value(del10))
                .andExpect(jsonPath("$.ausencias[0].inicio").value("2026-11-10T13:00:00Z"))
                .andExpect(jsonPath("$.ausencias[0].prestacionNombre").value(nombrePrestacion))
                .andExpect(jsonPath("$.ausencias[1].turnoId").value(del3))
                .andExpect(jsonPath("$.ausencias[1].inicio").value("2026-11-03T13:00:00Z"));
    }

    @Test
    void pacienteSinAusenciasDevuelveCeroYListaVacia() throws Exception {
        turno(EstadoTurno.ATENDIDO, "2026-11-17T13:00:00Z");

        historial(paciente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(0))
                .andExpect(jsonPath("$.ausencias.length()").value(0));
    }

    @Test
    void unaAusenciaRevertidaNoAparece() throws Exception {
        Instant inicio = AHORA.minus(Duration.ofMinutes(20));
        Long turno = insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, inicio,
                inicio.plus(Duration.ofMinutes(30)));
        mockMvc.perform(c.firmar(post("/api/v1/turnos/" + turno + "/ausente"))).andExpect(status().isOk());
        mockMvc.perform(c.firmar(post("/api/v1/turnos/" + turno + "/revertir-ausencia"))).andExpect(status().isOk());

        historial(paciente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(0))
                .andExpect(jsonPath("$.ausencias.length()").value(0));
    }

    @Test
    void pacienteDeOtroConsultorioOInexistenteDevuelve404() throws Exception {
        Long ajeno = crearPaciente(crearOtroConsultorio(), true);

        historial(ajeno).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        historial(999_999_999L).andExpect(status().isNotFound());
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes/" + paciente + "/ausencias")).andExpect(status().isUnauthorized());
    }
}
