package ar.edu.odontogestion.api.turno;

import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * {@code POST /api/v1/turnos/{id}/ausente} y {@code /revertir-ausencia} con sesión, CSRF y PostgreSQL reales. "Ahora"
 * es el reloj fijo (2026-11-20T12:00Z); tolerancia del seed: 15 min.
 */
class AusenciaTurnoApiIT extends AbstractTurnoIT {

    private static final String URL = "/api/v1/turnos/";
    private static final String PROBLEMAS = "https://odontogestion.example/problemas/";

    private Cliente c;
    private Long paciente;
    private Long prestacion;

    @BeforeEach
    void iniciar() throws Exception {
        c = iniciarSesionSeed();
        paciente = crearPaciente();
        prestacion = crearPrestacion(30);
    }

    private Long turnoQueEmpezoHace(int minutos, EstadoTurno estado) {
        Instant inicio = AHORA.minus(Duration.ofMinutes(minutos));
        return insertarTurno(paciente, prestacion, estado, inicio, inicio.plus(Duration.ofMinutes(30)));
    }

    private ResultActions accion(Object turno, String accion) throws Exception {
        return mockMvc.perform(c.firmar(post(URL + turno + "/" + accion)));
    }

    private String estado(Long turno) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno);
    }

    // ---- 6.1 marcar ausente ----

    @Test
    void marcarAusenteDevuelve200ConElTurnoAusenteLaMarcaYElContador() throws Exception {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.RESERVADO);

        accion(turno, "ausente")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(turno))
                .andExpect(jsonPath("$.estado").value("AUSENTE"))
                .andExpect(jsonPath("$.ausenteMarcadoEn").value("2026-11-20T12:00:00Z"))
                .andExpect(jsonPath("$.paciente.cantidadAusencias").value(1))
                .andExpect(jsonPath("$.paciente.dni").doesNotExist())
                .andExpect(jsonPath("$.paciente.email").doesNotExist());
    }

    @Test
    void antesDeLaToleranciaDevuelve422ConSuTipo() throws Exception {
        Long turno = turnoQueEmpezoHace(10, EstadoTurno.RESERVADO);

        accion(turno, "ausente")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(PROBLEMAS + "ausente-antes-de-tolerancia"))
                .andExpect(jsonPath("$.status").value(422));

        assertThat(estado(turno)).isEqualTo("RESERVADO");
    }

    @Test
    void desdeAtendidoDevuelve422TransicionInvalida() throws Exception {
        Long turno = turnoQueEmpezoHace(60, EstadoTurno.ATENDIDO);

        accion(turno, "ausente")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(PROBLEMAS + "transicion-invalida"));
    }

    @Test
    void turnoInexistenteDevuelve404() throws Exception {
        accion(999_999_999L, "ausente").andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void sinCsrfDevuelve403SinCambios() throws Exception {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.RESERVADO);

        mockMvc.perform(post(URL + turno + "/ausente").session(c.session())).andExpect(status().isForbidden());

        assertThat(estado(turno)).isEqualTo("RESERVADO");
    }

    @Test
    void sinSesionDevuelve401SinCambios() throws Exception {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.RESERVADO);
        Cliente anonimo = clienteAnonimo();

        mockMvc.perform(anonimo.firmar(post(URL + turno + "/ausente"))).andExpect(status().isUnauthorized());
        mockMvc.perform(anonimo.firmar(post(URL + turno + "/revertir-ausencia"))).andExpect(status().isUnauthorized());

        assertThat(estado(turno)).isEqualTo("RESERVADO");
    }

    // ---- 6.2 revertir ----

    @Test
    void revertirDevuelve200ConElEstadoPrevio() throws Exception {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.CONFIRMADO);
        accion(turno, "ausente").andExpect(status().isOk());

        accion(turno, "revertir-ausencia")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.ausenteMarcadoEn").doesNotExist())
                .andExpect(jsonPath("$.paciente.cantidadAusencias").value(0));
    }

    @Test
    void revertirFueraDePlazoDevuelve422ConSuTipo() throws Exception {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.RESERVADO);
        accion(turno, "ausente").andExpect(status().isOk());
        reloj.advance(Duration.ofHours(24));

        accion(turno, "revertir-ausencia")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(PROBLEMAS + "reversion-fuera-de-plazo"));

        assertThat(estado(turno)).isEqualTo("AUSENTE");
    }

    @Test
    void revertirUnTurnoQueNoEstaAusenteDevuelve422() throws Exception {
        Long turno = turnoQueEmpezoHace(20, EstadoTurno.RESERVADO);

        accion(turno, "revertir-ausencia")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(PROBLEMAS + "transicion-invalida"));
    }

    @Test
    void revertirUnTurnoInexistenteDevuelve404() throws Exception {
        accion(999_999_999L, "revertir-ausencia").andExpect(status().isNotFound());
    }
}
