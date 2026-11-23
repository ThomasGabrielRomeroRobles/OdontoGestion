package ar.edu.odontogestion.api.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

/** Listado y retiro del odontólogo (C-10 grupo 6): con sesión; el {@code DELETE} exige CSRF. */
class ListaEsperaApiIT extends AbstractListaEsperaIT {

    private static final String URL = "/api/v1/lista-espera";
    private static final LocalDate D1 = LocalDate.of(2026, 11, 23);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 30);

    @Autowired
    private ObjectMapper json;
    @Autowired
    private TokenAccesoService tokens;

    private Long entrada(String estado) {
        return insertarEntrada(crearPaciente(), crearPrestacion(30), estado, D1, D2);
    }

    private MockHttpServletResponse borrar(Cliente c, Long id) throws Exception {
        return mockMvc.perform(c.firmar(delete(URL + "/" + id))).andReturn().getResponse();
    }

    @Test
    void sinSesionElListadoYElRetiroDan401() throws Exception {
        assertThat(mockMvc.perform(get(URL)).andReturn().getResponse().getStatus()).isEqualTo(401);
        Cliente anonimo = clienteAnonimo();
        assertThat(borrar(anonimo, 1L).getStatus()).isEqualTo(401);
    }

    @Test
    void listaLasEntradasDelConsultorioDeLaMasRecienteALaMasAntiguaSinDniNiEmail() throws Exception {
        Cliente c = iniciarSesionSeed();
        Long vieja = entrada("CUBIERTA");
        Long nueva = entrada("ACTIVA");
        jdbc.update("UPDATE entrada_lista_espera SET creado_en = ?::timestamptz WHERE id = ?",
                reloj.instant().minusSeconds(7200).toString(), vieja);
        jdbc.update("UPDATE entrada_lista_espera SET creado_en = ?::timestamptz WHERE id = ?",
                reloj.instant().minusSeconds(60).toString(), nueva);

        MockHttpServletResponse r = mockMvc.perform(get(URL).session(c.session())).andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode lista = json.readTree(r.getContentAsString());
        java.util.List<Long> ids = new java.util.ArrayList<>();
        lista.forEach(n -> ids.add(n.get("id").asLong()));
        assertThat(ids.indexOf(nueva)).isLessThan(ids.indexOf(vieja));
        JsonNode fila = null;
        for (JsonNode n : lista) {
            if (n.get("id").asLong() == nueva) {
                fila = n;
            }
        }
        assertThat(fila.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "paciente", "prestacion",
                "fechaDesde", "fechaHasta", "estado", "creadoEn");
        assertThat(fila.get("paciente").asText()).startsWith("Ficticia").contains(", Ana");
        assertThat(fila.get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(fila.get("fechaDesde").asText()).isEqualTo("2026-11-23");
        assertThat(r.getContentAsString()).doesNotContain("@ejemplo.test").doesNotContainPattern("38[0-9]{6}");
    }

    @Test
    void retirarUnaEntradaActivaDa204LaCancelaYRevocaSuTokenDeConfirmacion() throws Exception {
        Cliente c = iniciarSesionSeed();
        Long id = entrada("PENDIENTE_CONFIRMACION");
        tokens.emitir(PropositoToken.CONFIRMAR_LISTA_ESPERA, new ReferenciaToken.EntradaListaEspera(id),
                reloj.instant().plusSeconds(3600));

        MockHttpServletResponse r = borrar(c, id);

        assertThat(r.getStatus()).isEqualTo(204);
        assertThat(estadoEntrada(id)).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("SELECT vence_en <= ?::timestamptz FROM token_acceso WHERE "
                + "entrada_lista_espera_id = ?", Boolean.class, reloj.instant().toString(), id)).isTrue();
    }

    @Test
    void retirarUnaEntradaActivaDeOtroEstadoFinalDa422SinCambios() throws Exception {
        Cliente c = iniciarSesionSeed();
        Long cubierta = entrada("CUBIERTA");
        Long cancelada = entrada("CANCELADA");

        assertThat(borrar(c, cubierta).getStatus()).isEqualTo(422);
        assertThat(borrar(c, cancelada).getStatus()).isEqualTo(422);
        assertThat(estadoEntrada(cubierta)).isEqualTo("CUBIERTA");
    }

    @Test
    void inexistenteOdeOtroConsultorioDa404() throws Exception {
        Cliente c = iniciarSesionSeed();
        Long otro = crearOtroConsultorio();
        Long pacienteAjeno = crearPaciente(otro, true);
        Long prestacionAjena = crearPrestacion(otro, 30, true);
        Long ajena = jdbc.queryForObject("INSERT INTO entrada_lista_espera (consultorio_id, paciente_id, "
                + "prestacion_id, fecha_desde, fecha_hasta, estado, origen) VALUES (?, ?, ?, ?, ?, 'ACTIVA', "
                + "'PUBLICA') RETURNING id", Long.class, otro, pacienteAjeno, prestacionAjena, D1, D2);

        assertThat(borrar(c, 999_999_999L).getStatus()).isEqualTo(404);
        assertThat(borrar(c, ajena).getStatus()).isEqualTo(404);
        assertThat(estadoEntrada(ajena)).isEqualTo("ACTIVA");
        jdbc.update("DELETE FROM entrada_lista_espera WHERE id = ?", ajena);
    }

    @Test
    void sinCsrfElRetiroDa403() throws Exception {
        Cliente c = iniciarSesionSeed();
        Long id = entrada("ACTIVA");

        MockHttpServletResponse r = mockMvc.perform(delete(URL + "/" + id).session(c.session()))
                .andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(estadoEntrada(id)).isEqualTo("ACTIVA");
    }
}
