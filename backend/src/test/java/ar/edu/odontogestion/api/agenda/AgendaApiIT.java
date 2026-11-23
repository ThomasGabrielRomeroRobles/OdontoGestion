package ar.edu.odontogestion.api.agenda;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Consultas de agenda con sesión real y PostgreSQL. Reloj fijo: viernes 2026-11-20 09:00 BA. */
class AgendaApiIT extends AbstractTurnoIT {

    private static final String DISPONIBILIDAD = "/api/v1/agenda/disponibilidad";
    private static final String AGENDA = "/api/v1/agenda";
    private static final String LUNES_ISO = "2026-11-23";

    private Cliente c;

    @BeforeEach
    void iniciar() throws Exception {
        c = iniciarSesionSeed();
    }

    private ResultActions consultar(String url) throws Exception {
        return mockMvc.perform(get(url).session(c.session()));
    }

    @Test
    void disponibilidadDeUnDiaDevuelveLosHuecosOrdenados() throws Exception {
        Long prestacion = crearPrestacion(30);

        consultar(DISPONIBILIDAD + "?prestacionId=" + prestacion + "&desde=" + LUNES_ISO + "&hasta=" + LUNES_ISO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30))
                .andExpect(jsonPath("$[0].inicio").value("2026-11-23T12:00:00Z"))
                .andExpect(jsonPath("$[0].fin").value("2026-11-23T12:30:00Z"))
                .andExpect(jsonPath("$[1].inicio").value("2026-11-23T12:15:00Z"))
                .andExpect(jsonPath("$[29].inicio").value("2026-11-23T20:30:00Z"));
    }

    @Test
    void disponibilidadDescuentaTurnosYUnDiaSinHorarioEsVacia() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("09:00"), lunes("09:30"));

        consultar(DISPONIBILIDAD + "?prestacionId=" + prestacion + "&desde=" + LUNES_ISO + "&hasta=" + LUNES_ISO)
                .andExpect(jsonPath("$.length()").value(28))
                .andExpect(jsonPath("$[0].inicio").value("2026-11-23T12:30:00Z"));
        consultar(DISPONIBILIDAD + "?prestacionId=" + prestacion + "&desde=2026-11-28&hasta=2026-11-29")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void disponibilidadConPrestacionInexistenteInactivaORangoInvalido() throws Exception {
        consultar(DISPONIBILIDAD + "?prestacionId=999999999&desde=" + LUNES_ISO + "&hasta=" + LUNES_ISO)
                .andExpect(status().isNotFound());
        consultar(DISPONIBILIDAD + "?prestacionId=" + crearPrestacionInactiva(30) + "&desde=" + LUNES_ISO + "&hasta="
                + LUNES_ISO).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='prestacionId')]").exists());
        Long prestacion = crearPrestacion(30);
        consultar(DISPONIBILIDAD + "?prestacionId=" + prestacion + "&desde=2026-12-30&hasta=2026-11-23")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='hasta')]").exists());
        consultar(DISPONIBILIDAD + "?prestacionId=" + prestacion + "&desde=2026-11-23&hasta=2027-01-22")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void disponibilidadConParametrosFaltantesOMalFormadosDa400() throws Exception {
        consultar(DISPONIBILIDAD + "?desde=" + LUNES_ISO + "&hasta=" + LUNES_ISO).andExpect(status().isBadRequest());
        consultar(DISPONIBILIDAD + "?prestacionId=1&hasta=" + LUNES_ISO).andExpect(status().isBadRequest());
        consultar(DISPONIBILIDAD + "?prestacionId=1&desde=ayer&hasta=" + LUNES_ISO).andExpect(status().isBadRequest());
    }

    @Test
    void agendaSemanalListaTodosLosEstadosOrdenadosConVersion() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.CANCELADO, dia(2, "10:00"), dia(2, "10:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("09:00"), lunes("09:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, dia(1, "11:00"), dia(1, "11:30"));

        consultar(AGENDA + "?desde=" + LUNES_ISO + "&hasta=2026-11-29")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].estado").value("RESERVADO"))
                .andExpect(jsonPath("$[1].estado").value("PENDIENTE_CONFIRMACION"))
                .andExpect(jsonPath("$[2].estado").value("CANCELADO"))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].paciente.id").value(paciente))
                .andExpect(jsonPath("$[0].paciente.apellido").isNotEmpty())
                .andExpect(jsonPath("$[0].prestacion.nombre").isNotEmpty())
                .andExpect(jsonPath("$[0].origen").value("ODONTOLOGO"))
                .andExpect(jsonPath("$[0].paciente.dni").doesNotExist())
                .andExpect(jsonPath("$[0].paciente.email").doesNotExist());
    }

    @Test
    void agendaIncluyeElDiaHastaCompletoEnLaZonaDelConsultorio() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        // 22:30 locales del lunes = 01:30Z del martes: sigue siendo del lunes en Buenos Aires.
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("22:30"), lunes("23:00"));
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, dia(1, "00:30"), dia(1, "01:00"));

        consultar(AGENDA + "?desde=" + LUNES_ISO + "&hasta=" + LUNES_ISO)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].inicio").value("2026-11-24T01:30:00Z"));
    }

    @Test
    void agendaConRangoInvertidoOMuyLargoDa422YSinDesdeDa400() throws Exception {
        consultar(AGENDA + "?desde=2026-11-30&hasta=2026-11-23").andExpect(status().isUnprocessableEntity());
        consultar(AGENDA + "?desde=2026-11-01&hasta=2027-01-30").andExpect(status().isUnprocessableEntity());
        consultar(AGENDA + "?hasta=" + LUNES_ISO).andExpect(status().isBadRequest());
    }

    @Test
    void turnoAusenteTraeLaMarcaYElContadorDelPacienteSinDatosDeContacto() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        jdbc.update("UPDATE paciente SET cantidad_ausencias = 2 WHERE id = ?", paciente);
        insertarTurno(paciente, prestacion, EstadoTurno.AUSENTE, lunes("09:00"), lunes("09:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        String json = consultar(AGENDA + "?desde=" + LUNES_ISO + "&hasta=" + LUNES_ISO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.estado == 'AUSENTE')].ausenteMarcadoEn")
                        .value(org.hamcrest.Matchers.contains(lunes("09:30").toString())))
                .andExpect(jsonPath("$[?(@.estado == 'RESERVADO')].ausenteMarcadoEn")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[*].paciente.cantidadAusencias")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(2))))
                .andReturn().getResponse().getContentAsString();

        String dni = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, paciente);
        String email = jdbc.queryForObject("SELECT email FROM paciente WHERE id = ?", String.class, paciente);
        org.assertj.core.api.Assertions.assertThat(json).doesNotContain(dni).doesNotContain(email)
                .doesNotContain("\"dni\"").doesNotContain("\"telefono\"");
    }

    @Test
    void sinSesionAmbasConsultasDan401() throws Exception {
        mockMvc.perform(get(AGENDA + "?desde=" + LUNES_ISO + "&hasta=" + LUNES_ISO)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(DISPONIBILIDAD + "?prestacionId=1&desde=" + LUNES_ISO + "&hasta=" + LUNES_ISO))
                .andExpect(status().isUnauthorized());
    }
}
