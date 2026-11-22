package ar.edu.odontogestion.api.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
