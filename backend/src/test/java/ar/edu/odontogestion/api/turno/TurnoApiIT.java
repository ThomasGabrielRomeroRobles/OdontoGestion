package ar.edu.odontogestion.api.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** API de turnos con sesión, CSRF y PostgreSQL reales. "Ahora" es el reloj fijo (viernes 2026-11-20 09:00 BA). */
class TurnoApiIT extends AbstractTurnoIT {

    private static final String URL = "/api/v1/turnos";

    private Cliente c;

    @BeforeEach
    void iniciar() throws Exception {
        c = iniciarSesionSeed();
    }

    private static String cuerpo(Long paciente, Long prestacion, Instant inicio) {
        return "{\"pacienteId\":" + paciente + ",\"prestacionId\":" + prestacion + ",\"inicio\":\"" + inicio + "\"}";
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return mockMvc.perform(c.firmar(post(URL)).contentType("application/json").content(cuerpo));
    }

    @Test
    void creaUnTurnoDevuelve201ConLocationYSinDatosPersonales() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);

        String respuesta = crear(cuerpo(paciente, prestacion, lunes("10:00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.matchesPattern(".*/api/v1/turnos/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.estado").value("RESERVADO"))
                .andExpect(jsonPath("$.origen").value("ODONTOLOGO"))
                .andExpect(jsonPath("$.inicio").value("2026-11-23T13:00:00Z"))
                .andExpect(jsonPath("$.fin").value("2026-11-23T13:30:00Z"))
                .andExpect(jsonPath("$.paciente.id").value(paciente))
                .andExpect(jsonPath("$.paciente.nombre").value("Ana"))
                .andExpect(jsonPath("$.prestacion.id").value(prestacion))
                .andExpect(jsonPath("$.pendienteHasta").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.canceladoPor").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.motivoCancelacion").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.paciente.dni").doesNotExist())
                .andExpect(jsonPath("$.paciente.email").doesNotExist())
                .andExpect(jsonPath("$.paciente.telefono").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain("@ejemplo.test").doesNotContainPattern("38\\d{6}");
        assertThat(contarTurnos()).isEqualTo(1);
    }

    @Test
    void sinSesionDa401YNoCreaNada() throws Exception {
        Cliente anonimo = clienteAnonimo();

        mockMvc.perform(anonimo.firmar(post(URL)).contentType("application/json")
                        .content(cuerpo(crearPaciente(), crearPrestacion(30), lunes("10:00"))))
                .andExpect(status().isUnauthorized());

        assertThat(contarTurnos()).isZero();
    }

    @Test
    void sinCsrfDa403() throws Exception {
        mockMvc.perform(post(URL).session(c.session()).contentType("application/json")
                        .content(cuerpo(crearPaciente(), crearPrestacion(30), lunes("10:00"))))
                .andExpect(status().isForbidden());

        assertThat(contarTurnos()).isZero();
    }

    @Test
    void cuerpoInvalidoDa400() throws Exception {
        crear("{\"prestacionId\":1,\"inicio\":\"2026-11-23T13:00:00Z\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='pacienteId')]").exists());
        crear("{\"pacienteId\":1,\"prestacionId\":1,\"inicio\":\"mañana\"}").andExpect(status().isBadRequest());
        crear("no es json").andExpect(status().isBadRequest());
    }

    @Test
    void horarioOcupadoDa409ConElTipoDeConflictoYAlternativas() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        crear(cuerpo(paciente, prestacion, lunes("10:15")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/hueco-no-disponible"))
                .andExpect(jsonPath("$.alternativas.length()").value(3))
                .andExpect(jsonPath("$.alternativas[0].inicio").value("2026-11-23T13:30:00Z"))
                .andExpect(jsonPath("$.alternativas[0].fin").value("2026-11-23T14:00:00Z"))
                .andExpect(jsonPath("$.alternativas[2].inicio").value("2026-11-23T14:00:00Z"));
        assertThat(contarTurnos()).isEqualTo(1);
    }

    @Test
    void pacienteOPrestacionInexistentesDan404() throws Exception {
        Long prestacion = crearPrestacion(30);
        crear(cuerpo(999_999_999L, prestacion, lunes("10:00"))).andExpect(status().isNotFound());
        crear(cuerpo(crearPaciente(), 999_999_999L, lunes("10:00"))).andExpect(status().isNotFound());
    }

    @Test
    void horarioInvalidoOPrestacionInactivaDan422ConElCampo() throws Exception {
        Long paciente = crearPaciente();
        crear(cuerpo(paciente, crearPrestacion(30), lunes("12:45")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='inicio')]").exists());
        crear(cuerpo(paciente, crearPrestacionInactiva(30), lunes("10:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='prestacionId')]").exists());
        crear(cuerpo(crearPacienteDeBaja(), crearPrestacion(30), lunes("10:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='pacienteId')]").exists());
        assertThat(contarTurnos()).isZero();
    }

    // ---- mover / cancelar / atendido ----

    private Long turnoExistente(EstadoTurno estado, String inicio, String fin) {
        return insertarTurno(crearPaciente(), crearPrestacion(30), estado, lunes(inicio), lunes(fin));
    }

    private ResultActions mover(Long id, String cuerpo) throws Exception {
        return mockMvc.perform(c.firmar(put(URL + "/" + id + "/mover")).contentType("application/json")
                .content(cuerpo));
    }

    private static String inicio(Instant inicio) {
        return "{\"inicio\":\"" + inicio + "\"}";
    }

    @Test
    void moverDevuelve200ConElNuevoRangoYElMismoEstado() throws Exception {
        Long id = turnoExistente(EstadoTurno.CONFIRMADO, "10:00", "10:30");

        mover(id, inicio(lunes("11:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.inicio").value("2026-11-23T14:00:00Z"))
                .andExpect(jsonPath("$.fin").value("2026-11-23T14:30:00Z"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.paciente.dni").doesNotExist());
    }

    @Test
    void moverAUnHuecoOcupadoDa409ConAlternativasYElTurnoNoCambia() throws Exception {
        Long id = turnoExistente(EstadoTurno.RESERVADO, "10:00", "10:30");
        insertarTurno(crearPaciente(), crearPrestacion(30), EstadoTurno.RESERVADO, lunes("11:00"), lunes("11:30"));

        mover(id, inicio(lunes("11:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/hueco-no-disponible"))
                .andExpect(jsonPath("$.alternativas.length()").value(3))
                .andExpect(jsonPath("$.alternativas[0].inicio").value("2026-11-23T14:30:00Z"));
        assertThat(jdbc.queryForObject("SELECT inicio = ?::timestamptz FROM turno WHERE id = ?", Boolean.class,
                lunes("10:00").toString(), id)).isTrue();
    }

    @Test
    void moverConHorarioInvalidoOEstadoNoMovibleDa422() throws Exception {
        Long reservado = turnoExistente(EstadoTurno.RESERVADO, "10:00", "10:30");
        Long atendido = turnoExistente(EstadoTurno.ATENDIDO, "11:00", "11:30");

        mover(reservado, inicio(lunes("12:45"))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='inicio')]").exists());
        mover(atendido, inicio(lunes("15:00"))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/transicion-invalida"));
    }

    @Test
    void moverConIdInexistenteSinSesionSinCsrfOCuerpoInvalido() throws Exception {
        Long id = turnoExistente(EstadoTurno.RESERVADO, "10:00", "10:30");

        mover(999_999_999L, inicio(lunes("11:00"))).andExpect(status().isNotFound());
        mover(id, "{}").andExpect(status().isBadRequest());
        mover(id, "{\"inicio\":\"nunca\"}").andExpect(status().isBadRequest());
        mockMvc.perform(clienteAnonimo().firmar(put(URL + "/" + id + "/mover")).contentType("application/json")
                .content(inicio(lunes("11:00")))).andExpect(status().isUnauthorized());
        mockMvc.perform(put(URL + "/" + id + "/mover").session(c.session()).contentType("application/json")
                .content(inicio(lunes("11:00")))).andExpect(status().isForbidden());
    }

    @Test
    void cancelarConMotivoDevuelve200ConLosDatosDeLaCancelacion() throws Exception {
        Long id = turnoExistente(EstadoTurno.RESERVADO, "10:00", "10:30");

        mockMvc.perform(c.firmar(post(URL + "/" + id + "/cancelar")).contentType("application/json")
                        .content("{\"motivo\":\"Imprevisto del consultorio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.canceladoPor").value("ODONTOLOGO"))
                .andExpect(jsonPath("$.motivoCancelacion").value("Imprevisto del consultorio"));
    }

    @Test
    void cancelarSinCuerpoOConCuerpoVacioDejaElMotivoNulo() throws Exception {
        Long sinCuerpo = turnoExistente(EstadoTurno.CONFIRMADO, "10:00", "10:30");
        Long cuerpoVacio = turnoExistente(EstadoTurno.RESERVADO, "11:00", "11:30");

        mockMvc.perform(c.firmar(post(URL + "/" + sinCuerpo + "/cancelar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.motivoCancelacion").value(Matchers.nullValue()));
        mockMvc.perform(c.firmar(post(URL + "/" + cuerpoVacio + "/cancelar")).contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
    }

    @Test
    void cancelarConMotivoDeMasDe200Da400YUnoDe200Da200() throws Exception {
        Long id = turnoExistente(EstadoTurno.RESERVADO, "10:00", "10:30");

        mockMvc.perform(c.firmar(post(URL + "/" + id + "/cancelar")).contentType("application/json")
                        .content("{\"motivo\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='motivo')]").exists());
        assertThat(estadoDe(id)).isEqualTo("RESERVADO");
        mockMvc.perform(c.firmar(post(URL + "/" + id + "/cancelar")).contentType("application/json")
                        .content("{\"motivo\":\"" + "x".repeat(200) + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void cancelarUnTurnoAtendidoDa422YUnoInexistenteDa404() throws Exception {
        Long atendido = turnoExistente(EstadoTurno.ATENDIDO, "10:00", "10:30");

        mockMvc.perform(c.firmar(post(URL + "/" + atendido + "/cancelar"))).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(c.firmar(post(URL + "/999999999/cancelar"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void atendidoDevuelve200Y422DesdeUnEstadoFinalY404() throws Exception {
        Long confirmado = turnoExistente(EstadoTurno.CONFIRMADO, "10:00", "10:30");
        Long cancelado = turnoExistente(EstadoTurno.CANCELADO, "11:00", "11:30");

        mockMvc.perform(c.firmar(post(URL + "/" + confirmado + "/atendido")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDO"));
        mockMvc.perform(c.firmar(post(URL + "/" + cancelado + "/atendido")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/transicion-invalida"));
        assertThat(estadoDe(cancelado)).isEqualTo("CANCELADO");
        mockMvc.perform(c.firmar(post(URL + "/999999999/atendido"))).andExpect(status().isNotFound());
    }

    @Test
    void cancelarYAtenderSinSesionDan401YSinCsrfDan403() throws Exception {
        Long id = turnoExistente(EstadoTurno.RESERVADO, "10:00", "10:30");
        Cliente anonimo = clienteAnonimo();

        for (String accion : new String[] {"cancelar", "atendido"}) {
            mockMvc.perform(anonimo.firmar(post(URL + "/" + id + "/" + accion))).andExpect(status().isUnauthorized());
            mockMvc.perform(post(URL + "/" + id + "/" + accion).session(c.session()))
                    .andExpect(status().isForbidden());
        }
        assertThat(estadoDe(id)).isEqualTo("RESERVADO");
    }
}
