package ar.edu.odontogestion.api.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

/** {@code /api/v1/avisos/{token}} de punta a punta (C-10 grupos 4 y 5): sin sesión, POST con CSRF. */
class AvisoApiIT extends AbstractListaEsperaIT {

    private static final String BASE = "/api/v1/avisos/";
    private static final LocalDate LUN = LocalDate.of(2026, 11, 23);

    @Autowired
    private TokenAccesoService tokens;
    @Autowired
    private ObjectMapper json;

    private record Aviso(Long id, Long entrada, Long turnoOrigen, Long prestacion, TokenEmitido token) {
    }

    private Aviso aviso(Long turnoOrigen, Long prestacion, Instant venceEn) {
        Long entrada = insertarEntrada(crearPaciente(), prestacion, "ACTIVA", LUN, LUN);
        Long id = insertarAviso(entrada, turnoOrigen, prestacion, lunes("10:00"), lunes("10:30"), "ENVIADO", venceEn);
        return new Aviso(id, entrada, turnoOrigen, prestacion, tokens.emitir(PropositoToken.ACEPTAR_AVISO,
                new ReferenciaToken.Aviso(id), reloj.instant().plusSeconds(3600)));
    }

    private Aviso aviso() {
        Long prestacion = crearPrestacion(30);
        Long origen = insertarTurno(crearPaciente(), prestacion, EstadoTurno.CANCELADO, lunes("10:00"),
                lunes("10:30"));
        return aviso(origen, prestacion, reloj.instant().plusSeconds(3600));
    }

    private MockHttpServletResponse obtener(String token) throws Exception {
        return mockMvc.perform(get(BASE + token)).andReturn().getResponse();
    }

    private MockHttpServletResponse aceptar(String token) throws Exception {
        return mockMvc.perform(clienteAnonimo().firmar(post(BASE + token + "/aceptar"))).andReturn().getResponse();
    }

    private boolean usado(Long avisoId) {
        return jdbc.queryForObject("SELECT usado_en IS NOT NULL FROM token_acceso WHERE aviso_id = ?",
                Boolean.class, avisoId);
    }

    @Test
    void getMuestraElHuecoSinConsumirNiDatosDelPaciente() throws Exception {
        Aviso a = aviso();

        MockHttpServletResponse r1 = obtener(a.token().valor());
        MockHttpServletResponse r2 = obtener(a.token().valor());

        assertThat(r1.getStatus()).isEqualTo(200);
        assertThat(r2.getStatus()).isEqualTo(200);
        JsonNode c = json.readTree(r1.getContentAsString());
        assertThat(c.fieldNames()).toIterable().containsExactlyInAnyOrder("consultorio", "prestacion", "inicio",
                "fin", "venceEn", "disponible");
        assertThat(c.get("disponible").asBoolean()).isTrue();
        assertThat(c.get("inicio").asText()).isEqualTo(lunes("10:00").toString());
        assertThat(r1.getContentAsString()).doesNotContain("Ana").doesNotContain("@ejemplo.test");
        assertThat(usado(a.id())).isFalse();
        assertThat(estadoAviso(a.id())).isEqualTo("ENVIADO");
    }

    @Test
    void getDeUnAvisoCerradoDevuelveDisponibleFalso() throws Exception {
        Aviso a = aviso();
        jdbc.update("UPDATE aviso_hueco SET estado = 'CERRADO' WHERE id = ?", a.id());

        MockHttpServletResponse r = obtener(a.token().valor());

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(json.readTree(r.getContentAsString()).get("disponible").asBoolean()).isFalse();
    }

    @Test
    void getConTokenInexistenteOdeOtroPropositoDa410Generico() throws Exception {
        Long turno = insertarTurno(crearPaciente(), crearPrestacion(30), EstadoTurno.RESERVADO, lunes("12:00"),
                lunes("12:30"));
        TokenEmitido deTurno = tokens.emitir(PropositoToken.CONFIRMAR_TURNO, new ReferenciaToken.Turno(turno),
                lunes("12:00"));

        MockHttpServletResponse inexistente = obtener("no-existe-" + "x".repeat(30));
        MockHttpServletResponse otro = obtener(deTurno.valor());

        assertThat(inexistente.getStatus()).isEqualTo(410);
        assertThat(otro.getStatus()).isEqualTo(410);
        assertThat(otro.getContentAsString()).isEqualTo(inexistente.getContentAsString())
                .doesNotContain(deTurno.valor());
        assertThat(jdbc.queryForObject("SELECT usado_en IS NULL FROM token_acceso WHERE turno_id = ?",
                Boolean.class, turno)).isTrue();
    }

    @Test
    void postAceptaYDevuelveElTurnoReservado() throws Exception {
        Aviso a = aviso();

        MockHttpServletResponse r = aceptar(a.token().valor());

        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode c = json.readTree(r.getContentAsString());
        assertThat(c.get("estado").asText()).isEqualTo("RESERVADO");
        assertThat(c.get("inicio").asText()).isEqualTo(lunes("10:00").toString());
        assertThat(c.fieldNames()).toIterable().containsExactlyInAnyOrder("prestacion", "inicio", "fin", "estado");
        assertThat(estadoAviso(a.id())).isEqualTo("ACEPTADO");
        // el mismo enlace otra vez: 410
        assertThat(aceptar(a.token().valor()).getStatus()).isEqualTo(410);
    }

    @Test
    void postDeUnAvisoCerradoDa409SinTurno() throws Exception {
        Aviso a = aviso();
        jdbc.update("UPDATE aviso_hueco SET estado = 'CERRADO' WHERE id = ?", a.id());

        MockHttpServletResponse r = aceptar(a.token().valor());

        assertThat(r.getStatus()).isEqualTo(409);
        assertThat(r.getContentAsString()).doesNotContain(a.token().valor());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE inicio = ?::timestamptz AND estado NOT IN "
                + "('CANCELADO','EXPIRADO')", Integer.class, lunes("10:00").toString())).isZero();
    }

    @Test
    void postConElHuecoTomadoPorOtraReservaDa409YCierraLosAvisos() throws Exception {
        Aviso a = aviso();
        insertarTurno(crearPaciente(), a.prestacion(), EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        MockHttpServletResponse r = aceptar(a.token().valor());

        assertThat(r.getStatus()).isEqualTo(409);
        assertThat(usado(a.id())).isFalse();
        assertThat(estadoEntrada(a.entrada())).isEqualTo("ACTIVA");
        assertThat(estadoAviso(a.id())).isEqualTo("CERRADO");
    }

    @Test
    void postExigeCsrfPeroNoSesion() throws Exception {
        Aviso a = aviso();

        MockHttpServletResponse sinCsrf = mockMvc.perform(post(BASE + a.token().valor() + "/aceptar"))
                .andReturn().getResponse();

        assertThat(sinCsrf.getStatus()).isEqualTo(403);
        assertThat(usado(a.id())).isFalse();
        assertThat(aceptar(a.token().valor()).getStatus()).isEqualTo(200);
    }

    @Test
    void unAvisoVencidoConTokenVigenteDa410EnGetYPostYQuedaExpirado() throws Exception {
        Long prestacion = crearPrestacion(30);
        Long origen = insertarTurno(crearPaciente(), prestacion, EstadoTurno.CANCELADO, lunes("10:00"),
                lunes("10:30"));
        Aviso a = aviso(origen, prestacion, reloj.instant().minusSeconds(5));

        assertThat(obtener(a.token().valor()).getStatus()).isEqualTo(410);
        assertThat(estadoAviso(a.id())).isEqualTo("EXPIRADO");
        jdbc.update("UPDATE aviso_hueco SET estado = 'ENVIADO' WHERE id = ?", a.id());
        assertThat(aceptar(a.token().valor()).getStatus()).isEqualTo(410);
        assertThat(estadoAviso(a.id())).isEqualTo("EXPIRADO");
        assertThat(usado(a.id())).isFalse();
    }
}
