package ar.edu.odontogestion.api.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TurnosAfectadosApiIT extends AbstractTurnoIT {

    private Cliente c;

    @BeforeEach
    void iniciar() throws Exception {
        c = iniciarSesionSeed();
    }

    private Long insertarBloqueo(Long consultorioId, Instant desde, Instant hasta) {
        return jdbc.queryForObject("INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta, motivo) "
                + "VALUES (?, ?::timestamptz, ?::timestamptz, 'IT-Turno bloqueo') RETURNING id", Long.class,
                consultorioId, desde.toString(), hasta.toString());
    }

    @Test
    void devuelveSoloLosTurnosQueOcupanAgendaYSolapanElBloqueo() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:30"), lunes("11:00"));
        insertarTurno(paciente, prestacion, EstadoTurno.CANCELADO, lunes("11:00"), lunes("11:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.CONFIRMADO, lunes("09:30"), lunes("10:00"));
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("12:00"), lunes("12:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.ATENDIDO, lunes("11:30"), lunes("12:00"));
        Long bloqueo = insertarBloqueo(consultorioId(), lunes("10:00"), lunes("12:00"));

        mockMvc.perform(get("/api/v1/bloqueos/" + bloqueo + "/turnos-afectados").session(c.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].inicio").value("2026-11-23T13:30:00Z"))
                .andExpect(jsonPath("$[0].estado").value("RESERVADO"))
                .andExpect(jsonPath("$[0].paciente.id").value(paciente))
                .andExpect(jsonPath("$[0].paciente.dni").doesNotExist())
                .andExpect(jsonPath("$[1].inicio").value("2026-11-23T14:30:00Z"))
                .andExpect(jsonPath("$[1].estado").value("ATENDIDO"));
    }

    @Test
    void bloqueoInexistenteODeOtroConsultorioDa404() throws Exception {
        Long ajeno = insertarBloqueo(crearOtroConsultorio(), lunes("10:00"), lunes("12:00"));

        mockMvc.perform(get("/api/v1/bloqueos/999999999/turnos-afectados").session(c.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/bloqueos/" + ajeno + "/turnos-afectados").session(c.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinSesionDa401() throws Exception {
        mockMvc.perform(get("/api/v1/bloqueos/1/turnos-afectados")).andExpect(status().isUnauthorized());
    }

    @Test
    void crearUnBloqueoQueSePisaConTurnosNoLosModifica() throws Exception {
        Long paciente = crearPaciente();
        Long turno = insertarTurno(paciente, crearPrestacion(30), EstadoTurno.RESERVADO, lunes("10:30"),
                lunes("11:00"));

        mockMvc.perform(c.firmar(post("/api/v1/bloqueos")).contentType("application/json")
                        .content("{\"desde\":\"" + lunes("10:00") + "\",\"hasta\":\"" + lunes("12:00")
                                + "\",\"motivo\":\"IT-Turno vacaciones\"}"))
                .andExpect(status().isCreated());

        assertThat(estadoDe(turno)).isEqualTo("RESERVADO");
        assertThat(jdbc.queryForObject("SELECT version FROM turno WHERE id = ?", Long.class, turno)).isZero();
    }
}
