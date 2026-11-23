package ar.edu.odontogestion.api.acciones;

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
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

/** {@code /api/v1/acciones/{token}} con {@code CONFIRMAR_LISTA_ESPERA} (C-10 D3) y regresión de las de turno. */
class ConfirmarListaEsperaApiIT extends AbstractListaEsperaIT {

    private static final String BASE = "/api/v1/acciones/";
    private static final LocalDate D1 = LocalDate.of(2026, 11, 23);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 30);

    @Autowired
    private TokenAccesoService tokens;
    @Autowired
    private ObjectMapper json;

    private Long entrada(String estado) {
        return insertarEntrada(crearPaciente(), crearPrestacion(30), estado, D1, D2);
    }

    private TokenEmitido token(Long entradaId) {
        return tokens.emitir(PropositoToken.CONFIRMAR_LISTA_ESPERA, new ReferenciaToken.EntradaListaEspera(entradaId),
                reloj.instant().plusSeconds(3600));
    }

    private MockHttpServletResponse obtener(String token) throws Exception {
        return mockMvc.perform(get(BASE + token)).andReturn().getResponse();
    }

    private MockHttpServletResponse confirmar(String token) throws Exception {
        return mockMvc.perform(clienteAnonimo().firmar(post(BASE + token)).contentType("application/json")
                .content("{\"accion\":\"CONFIRMAR_LISTA_ESPERA\"}")).andReturn().getResponse();
    }

    private boolean usado(Long entradaId) {
        return jdbc.queryForObject("SELECT usado_en IS NOT NULL FROM token_acceso WHERE entrada_lista_espera_id = ?",
                Boolean.class, entradaId);
    }

    @Test
    void getMuestraLaEntradaSinConsumirElTokenYConTurnoNulo() throws Exception {
        Long id = entrada("PENDIENTE_CONFIRMACION");
        TokenEmitido t = token(id);

        MockHttpServletResponse r1 = obtener(t.valor());
        MockHttpServletResponse r2 = obtener(t.valor());

        assertThat(r1.getStatus()).isEqualTo(200);
        assertThat(r2.getStatus()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(r1.getContentAsString());
        assertThat(cuerpo.get("accion").asText()).isEqualTo("CONFIRMAR_LISTA_ESPERA");
        assertThat(cuerpo.get("disponible").asBoolean()).isTrue();
        assertThat(cuerpo.get("turno").isNull()).isTrue();
        JsonNode e = cuerpo.get("entrada");
        assertThat(e.get("prestacion").asText()).startsWith("IT-Turno-");
        assertThat(e.get("fechaDesde").asText()).isEqualTo("2026-11-23");
        assertThat(e.get("fechaHasta").asText()).isEqualTo("2026-11-30");
        assertThat(e.get("estado").asText()).isEqualTo("PENDIENTE_CONFIRMACION");
        assertThat(r1.getContentAsString()).doesNotContain("dni").doesNotContain("email");
        assertThat(usado(id)).isFalse();
        assertThat(estadoEntrada(id)).isEqualTo("PENDIENTE_CONFIRMACION");
    }

    @Test
    void postActivaLaEntradaYConsumeElToken() throws Exception {
        Long id = entrada("PENDIENTE_CONFIRMACION");
        TokenEmitido t = token(id);

        MockHttpServletResponse r = confirmar(t.valor());

        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(r.getContentAsString());
        assertThat(cuerpo.get("entrada").get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(cuerpo.get("turno").isNull()).isTrue();
        assertThat(estadoEntrada(id)).isEqualTo("ACTIVA");
        assertThat(usado(id)).isTrue();
        assertThat(confirmar(t.valor()).getStatus()).isEqualTo(410);
    }

    @Test
    void unaEntradaCanceladaDa410SinActivarNiConsumir() throws Exception {
        Long id = entrada("CANCELADA");
        TokenEmitido t = token(id);

        assertThat(obtener(t.valor()).getStatus()).isEqualTo(410);
        assertThat(confirmar(t.valor()).getStatus()).isEqualTo(410);
        assertThat(estadoEntrada(id)).isEqualTo("CANCELADA");
        assertThat(usado(id)).isFalse();
    }

    @Test
    void unaPendienteConPlazoVencidoDa410() throws Exception {
        Long id = insertarEntrada(crearPaciente(), crearPrestacion(30), "PENDIENTE_CONFIRMACION", D1, D2,
                reloj.instant().minusSeconds(10));
        TokenEmitido t = token(id);

        assertThat(confirmar(t.valor()).getStatus()).isEqualTo(410);
        assertThat(estadoEntrada(id)).isEqualTo("PENDIENTE_CONFIRMACION");
        assertThat(usado(id)).isFalse();
    }

    @Test
    void laAccionDeTurnoSigueIgualConEntradaNula() throws Exception {
        Long turno = insertarTurno(crearPaciente(), crearPrestacion(30), EstadoTurno.RESERVADO, lunes("10:00"),
                lunes("10:30"));
        TokenEmitido t = tokens.emitir(PropositoToken.CANCELAR_TURNO, new ReferenciaToken.Turno(turno),
                lunes("10:00"));

        JsonNode cuerpo = json.readTree(obtener(t.valor()).getContentAsString());

        assertThat(cuerpo.get("turno").get("prestacion").asText()).startsWith("IT-Turno-");
        assertThat(cuerpo.has("entrada")).isTrue();
        assertThat(cuerpo.get("entrada").isNull()).isTrue();
    }
}
