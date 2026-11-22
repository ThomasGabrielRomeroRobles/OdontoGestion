package ar.edu.odontogestion.api.turno;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** "Hoy" es el viernes 2026-11-20 en Buenos Aires (reloj fijo 12:00Z = 09:00 locales). */
class TurnosHoyApiIT extends AbstractTurnoIT {

    private static final String URL = "/api/v1/turnos/hoy";

    private Cliente c;
    private Long paciente;
    private Long prestacion;

    @BeforeEach
    void iniciar() throws Exception {
        c = iniciarSesionSeed();
        paciente = crearPaciente();
        prestacion = crearPrestacion(30);
    }

    /** Hora local de Buenos Aires del viernes de "hoy" (tres días antes del lunes {@code LUNES}). */
    private Long turno(EstadoTurno estado, int offsetDias, String hora) {
        Instant inicio = dia(offsetDias, hora);
        return insertarTurno(paciente, prestacion, estado, inicio, inicio.plus(Duration.ofMinutes(30)));
    }

    @Test
    void listaElDiaOrdenadoSinCanceladosNiExpiradosYConElIdDelPaciente() throws Exception {
        turno(EstadoTurno.CONFIRMADO, -3, "09:00");
        turno(EstadoTurno.ATENDIDO, -3, "08:00");
        turno(EstadoTurno.CANCELADO, -3, "10:00");
        turno(EstadoTurno.EXPIRADO, -3, "11:00");
        turno(EstadoTurno.AUSENTE, -3, "12:00");

        mockMvc.perform(get(URL).session(c.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].estado").value("ATENDIDO"))
                .andExpect(jsonPath("$[0].inicio").value("2026-11-20T11:00:00Z"))
                .andExpect(jsonPath("$[1].estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$[2].estado").value("AUSENTE"))
                .andExpect(jsonPath("$[1].paciente.id").value(paciente))
                .andExpect(jsonPath("$[1].paciente.nombre").value("Ana"))
                .andExpect(jsonPath("$[1].prestacion.nombre").isNotEmpty())
                .andExpect(jsonPath("$[1].paciente.dni").doesNotExist())
                .andExpect(jsonPath("$[1].paciente.email").doesNotExist());
    }

    @Test
    void elLimiteDelDiaEsElDeBuenosAiresYNoElDeUtc() throws Exception {
        // 22:00 locales = 01:00Z del día siguiente: cuenta como de hoy.
        turno(EstadoTurno.RESERVADO, -3, "22:00");
        // 23:30 de ayer (02:30Z de hoy, ya "hoy" en UTC) y 00:30 de mañana (03:30Z de mañana): no cuentan.
        turno(EstadoTurno.RESERVADO, -4, "23:30");
        turno(EstadoTurno.RESERVADO, -2, "00:30");

        mockMvc.perform(get(URL).session(c.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].inicio").value("2026-11-21T01:00:00Z"));
    }

    @Test
    void unDiaSinTurnosDevuelveListaVaciaYUnaPendienteVigenteCuenta() throws Exception {
        mockMvc.perform(get(URL).session(c.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        turno(EstadoTurno.PENDIENTE_CONFIRMACION, -3, "15:00");

        mockMvc.perform(get(URL).session(c.session())).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pendienteHasta").isNotEmpty());
    }

    @Test
    void sinSesionDa401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
