package ar.edu.odontogestion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;

/**
 * Fallback de la SPA (C-12, requisito "Rutas del frontend servidas desde el mismo origen" de
 * {@code runtime-configuration}). El {@code index.html} y {@code assets/app-test.js} de prueba viven en
 * {@code src/test/resources/static/}, en lugar del build de Vite.
 */
@ActiveProfiles("demo")
class SpaFallbackIT extends AbstractWebIntegrationTest {

    private static final String MARCA_INDEX = "index-de-prueba";
    private static final String MARCA_ASSET = "asset-de-prueba";

    private MockHttpServletResponse pedir(String ruta) throws Exception {
        return mockMvc.perform(get(ruta)).andReturn().getResponse();
    }

    private static void esIndex(MockHttpServletResponse r, String ruta) throws Exception {
        assertThat(r.getStatus()).as("status de %s", ruta).isEqualTo(200);
        assertThat(r.getContentType()).as("content type de %s", ruta).startsWith(MediaType.TEXT_HTML_VALUE);
        assertThat(r.getContentAsString()).as("cuerpo de %s", ruta).contains(MARCA_INDEX);
    }

    private static void noEsIndex(MockHttpServletResponse r, String ruta) throws Exception {
        assertThat(r.getContentAsString()).as("cuerpo de %s", ruta).doesNotContain(MARCA_INDEX);
    }

    // ---- 1.1: escenarios del requisito ----

    @Test
    void entradaDirectaAUnaRutaPrivadaSinSesionDevuelveElIndex() throws Exception {
        esIndex(pedir("/agenda"), "/agenda");
    }

    @Test
    void elIndexDelFallbackSaleSinCacheDeLargaDuracion() throws Exception {
        assertThat(pedir("/agenda").getHeader("Cache-Control")).isEqualTo("no-cache");
    }

    @Test
    void losEnlacesDeEmailDevuelvenElIndex() throws Exception {
        esIndex(pedir("/acciones/tok-ficticio"), "/acciones/tok-ficticio");
        esIndex(pedir("/avisos/tok-ficticio"), "/avisos/tok-ficticio");
    }

    @Test
    void unaRutaDeLaApiInexistenteSigueSiendo404ProblemDetail() throws Exception {
        Cliente sesion = iniciarSesionSeed();

        MockHttpServletResponse r = mockMvc.perform(sesion.firmar(get("/api/v1/no-existe"))).andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(404);
        assertThat(r.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        noEsIndex(r, "/api/v1/no-existe");
    }

    @Test
    void unArchivoEstaticoInexistenteSigueSiendo404() throws Exception {
        MockHttpServletResponse r = pedir("/assets/no-existe.js");

        assertThat(r.getStatus()).isEqualTo(404);
        noEsIndex(r, "/assets/no-existe.js");
    }

    @Test
    void unArchivoRealDelBuildSeSirveTalCual() throws Exception {
        MockHttpServletResponse r = pedir("/assets/app-test.js");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentAsString()).contains(MARCA_ASSET).doesNotContain(MARCA_INDEX);
    }

    @Test
    void unaSolicitudQueNoEsGetNoDevuelveElIndex() throws Exception {
        MockHttpServletResponse r = mockMvc.perform(post("/agenda")).andReturn().getResponse();

        assertThat(r.getStatus()).isNotEqualTo(200);
        noEsIndex(r, "POST /agenda");
    }

    // ---- 1.3: triangulación ----

    @Test
    void lasRutasAnidadasDelFrontendDevuelvenElIndex() throws Exception {
        for (String ruta : new String[] {"/agenda/hoy", "/publico/lista-espera", "/configuracion"}) {
            esIndex(pedir(ruta), ruta);
        }
    }

    @Test
    void unTokenBase64urlRealDevuelveElIndexSinConsumirse() throws Exception {
        String token = "Ab-_0123456789abcdefghijklmnopqrstuvwxyzABC";
        assertThat(token).hasSize(43);
        Integer tokensAntes = jdbc.queryForObject("SELECT count(*) FROM token_acceso", Integer.class);

        esIndex(pedir("/acciones/" + token), "/acciones/<token>");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_acceso", Integer.class)).isEqualTo(tokensAntes);
    }

    @Test
    void apiSolaYConBarraFinalNoDevuelvenElIndex() throws Exception {
        Cliente sesion = iniciarSesionSeed();
        for (String ruta : new String[] {"/api", "/api/"}) {
            MockHttpServletResponse r = mockMvc.perform(sesion.firmar(get(ruta))).andReturn().getResponse();
            assertThat(r.getStatus()).as("status de %s", ruta).isEqualTo(404);
            noEsIndex(r, ruta);
        }
    }

    @Test
    void unaRutaQueSoloEmpiezaConApiEsDelFrontend() throws Exception {
        esIndex(pedir("/apis"), "/apis");
    }

    @Test
    void unaRutaDelFrontendConPuntoQuedaParaLosEstaticos() throws Exception {
        MockHttpServletResponse r = pedir("/agenda/archivo.txt");

        assertThat(r.getStatus()).isEqualTo(404);
        noEsIndex(r, "/agenda/archivo.txt");
    }
}
