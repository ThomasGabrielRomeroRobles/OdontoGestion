package ar.edu.odontogestion.api.acciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * El token no se filtra (C-09 D7/D8): cabecera {@code Referrer-Policy}, {@code instance} constante en los errores de
 * las rutas por token y logs sin el valor. Los errores de las demás rutas conservan su {@code instance}.
 */
@ExtendWith(OutputCaptureExtension.class)
class RutasPorTokenIT extends AbstractTurnoIT {

    private static final String CONSTANTE = "urn:odontogestion:ruta-por-token";
    private static final String ENLACE_NO_DISPONIBLE = "urn:odontogestion:enlace-no-disponible";

    @Autowired
    private TokenAccesoService tokens;
    @Autowired
    private ObjectMapper json;

    private Long turno(EstadoTurno estado, Instant inicio) {
        return insertarTurno(crearPaciente(), crearPrestacion(30), estado, inicio, inicio.plusSeconds(1800));
    }

    private TokenEmitido token(PropositoToken proposito, Long turnoId, Instant venceEn) {
        return tokens.emitir(proposito, new ReferenciaToken.Turno(turnoId), venceEn);
    }

    private String instance(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString()).path("instance").asText();
    }

    // --- 10.1 Referrer-Policy ---

    @Test
    void laApiPorTokenEnviaReferrerPolicyEnElExitoYEnElError() throws Exception {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        MockHttpServletResponse ok = mockMvc.perform(get("/api/v1/acciones/" + t.valor())).andReturn().getResponse();
        MockHttpServletResponse gone = mockMvc.perform(get("/api/v1/acciones/inexistente-" + "x".repeat(31)))
                .andReturn().getResponse();

        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(ok.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(gone.getStatus()).isEqualTo(410);
        assertThat(gone.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void laPaginaDelEnlaceYLaRaizTambienEnvianReferrerPolicy() throws Exception {
        MockHttpServletResponse raiz = mockMvc.perform(get("/")).andReturn().getResponse();
        MockHttpServletResponse enlace = mockMvc.perform(get("/acciones/" + "x".repeat(43))).andReturn()
                .getResponse();
        MockHttpServletResponse aviso = mockMvc.perform(get("/avisos/" + "x".repeat(43))).andReturn().getResponse();

        assertThat(raiz.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(enlace.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(aviso.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void lasRutasPrivadasConSesionYLos401TambienEnvianReferrerPolicy() throws Exception {
        Cliente sesion = iniciarSesionSeed();

        MockHttpServletResponse privada = mockMvc.perform(sesion.firmar(get("/api/v1/consultorio"))).andReturn()
                .getResponse();
        MockHttpServletResponse sinSesion = mockMvc.perform(get("/api/v1/consultorio")).andReturn().getResponse();

        assertThat(privada.getStatus()).isEqualTo(200);
        assertThat(privada.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(sinSesion.getStatus()).isEqualTo(401);
        assertThat(sinSesion.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    // --- 10.2 instance sin token ---

    @Test
    void unaReglaDeNegocioEnUnaAccionPorTokenNoLlevaElTokenNiLaRutaEnInstance() throws Exception {
        Long id = turno(EstadoTurno.ATENDIDO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        MockHttpServletResponse r = mockMvc.perform(clienteAnonimo().firmar(post("/api/v1/acciones/" + t.valor()))
                .contentType("application/json").content("{\"accion\":\"CONFIRMAR_TURNO\"}")).andReturn()
                .getResponse();

        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(r.getContentAsString()).doesNotContain(t.valor());
        assertThat(instance(r)).isEqualTo(CONSTANTE);
    }

    @Test
    void cuatrocientosTresYCuatrocientosDeUnaAccionPorTokenNoLlevanElToken() throws Exception {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido t = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));

        MockHttpServletResponse sinCsrf = mockMvc.perform(post("/api/v1/acciones/" + t.valor())
                .contentType("application/json").content("{\"accion\":\"CONFIRMAR_TURNO\"}")).andReturn()
                .getResponse();
        MockHttpServletResponse invalida = mockMvc.perform(clienteAnonimo().firmar(
                post("/api/v1/acciones/" + t.valor())).contentType("application/json").content("{}")).andReturn()
                .getResponse();

        assertThat(sinCsrf.getStatus()).isEqualTo(403);
        assertThat(sinCsrf.getContentAsString()).doesNotContain(t.valor());
        assertThat(instance(sinCsrf)).isEqualTo(CONSTANTE);
        assertThat(invalida.getStatus()).isEqualTo(400);
        assertThat(invalida.getContentAsString()).doesNotContain(t.valor());
        assertThat(instance(invalida)).isEqualTo(CONSTANTE);
    }

    @Test
    void rutasInexistentesOMetodosNoPermitidosBajoLaRutaPorTokenNoFiltranElTokenEnNingunCampo() throws Exception {
        String valor = "TOKEN-secreto-" + "y".repeat(29);
        Cliente c = clienteAnonimo();

        MockHttpServletResponse noEncontrada = mockMvc.perform(get("/api/v1/acciones/" + valor + "/otra"))
                .andReturn().getResponse();
        MockHttpServletResponse metodo = mockMvc.perform(c.firmar(put("/api/v1/acciones/" + valor))
                .contentType("application/json").content("{}")).andReturn().getResponse();
        MockHttpServletResponse aviso = mockMvc.perform(post("/api/v1/avisos/" + valor)
                .contentType("application/json").content("{}")).andReturn().getResponse();

        for (MockHttpServletResponse r : new MockHttpServletResponse[] {noEncontrada, metodo, aviso}) {
            assertThat(r.getStatus()).isGreaterThanOrEqualTo(400);
            assertThat(r.getContentAsString()).doesNotContain(valor);
            assertThat(instance(r)).isEqualTo(CONSTANTE);
        }
        assertThat(aviso.getStatus()).isEqualTo(403);
    }

    @Test
    void elDiezMilCuatrocientosDiezDeUnTokenInvalidoConservaSuInstanceConstanteDeC03() throws Exception {
        MockHttpServletResponse r = mockMvc.perform(get("/api/v1/acciones/inexistente-" + "x".repeat(31)))
                .andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(410);
        assertThat(instance(r)).isEqualTo(ENLACE_NO_DISPONIBLE);
        assertThat(r.getContentAsString()).doesNotContain("inexistente-");
    }

    @Test
    void otrasRutasConservanSuInstanceConLaRutaDeLaSolicitud() throws Exception {
        Cliente sesion = iniciarSesionSeed();
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        MockHttpServletResponse conflicto = mockMvc.perform(sesion.firmar(post("/api/v1/turnos"))
                .contentType("application/json").content("{\"pacienteId\":" + paciente + ",\"prestacionId\":"
                        + prestacion + ",\"inicio\":\"" + lunes("10:00") + "\"}")).andReturn().getResponse();
        MockHttpServletResponse sinCsrf = mockMvc.perform(post("/api/v1/turnos").session(sesion.session())
                .contentType("application/json").content("{}")).andReturn().getResponse();

        assertThat(conflicto.getStatus()).isEqualTo(409);
        assertThat(instance(conflicto)).isEqualTo("/api/v1/turnos");
        assertThat(sinCsrf.getStatus()).isEqualTo(403);
        assertThat(instance(sinCsrf)).isEqualTo("/api/v1/turnos");
    }

    @Test
    void laRespuestaDeErrorNoTrae404ConElDetalleDeLaRutaCompleta() throws Exception {
        String valor = "OTRO-secreto-" + "z".repeat(30);

        MockHttpServletResponse r = mockMvc.perform(get("/api/v1/acciones/" + valor + "/otra")).andReturn()
                .getResponse();

        JsonNode cuerpo = json.readTree(r.getContentAsString());
        assertThat(cuerpo.toString()).doesNotContain(valor);
    }

    // --- 10.4 logs ---

    @Test
    void losLogsNoContienenElValorDeNingunTokenNiEnExitoNiEnError(CapturedOutput salida) throws Exception {
        Long id = turno(EstadoTurno.RESERVADO, lunes("10:00"));
        TokenEmitido confirmar = token(PropositoToken.CONFIRMAR_TURNO, id, lunes("10:00"));
        TokenEmitido cancelar = token(PropositoToken.CANCELAR_TURNO, id, lunes("10:00"));
        Long atendido = turno(EstadoTurno.ATENDIDO, lunes("11:00"));
        TokenEmitido sobreAtendido = token(PropositoToken.CONFIRMAR_TURNO, atendido, lunes("11:00"));
        String invalido = "invalido-" + "q".repeat(34);
        Cliente c = clienteAnonimo();

        mockMvc.perform(get("/api/v1/acciones/" + confirmar.valor()));
        mockMvc.perform(c.firmar(post("/api/v1/acciones/" + confirmar.valor())).contentType("application/json")
                .content("{\"accion\":\"CONFIRMAR_TURNO\"}"));
        mockMvc.perform(c.firmar(post("/api/v1/acciones/" + sobreAtendido.valor())).contentType("application/json")
                .content("{\"accion\":\"CONFIRMAR_TURNO\"}"));
        mockMvc.perform(get("/api/v1/acciones/" + invalido));
        mockMvc.perform(c.firmar(post("/api/v1/acciones/" + invalido)).contentType("application/json")
                .content("{\"accion\":\"CANCELAR_TURNO\"}"));
        mockMvc.perform(get("/api/v1/acciones/" + cancelar.valor() + "/otra"));
        reloj.advance(Duration.ofMinutes(1));

        // Solo las líneas del log de la aplicación: MockMvc imprime cada pedido por consola (artefacto del test).
        String logs = salida.getAll().lines().filter(l -> l.matches("^20\\d\\d-\\d\\d-\\d\\dT.*"))
                .collect(Collectors.joining(System.lineSeparator()));
        assertThat(logs).doesNotContain(confirmar.valor()).doesNotContain(cancelar.valor())
                .doesNotContain(sobreAtendido.valor()).doesNotContain(invalido).doesNotContain("/acciones/");
    }
}
