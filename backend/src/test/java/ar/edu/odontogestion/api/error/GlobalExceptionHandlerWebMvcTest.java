package ar.edu.odontogestion.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import ar.edu.odontogestion.infrastructure.security.ProblemDetailAccessDeniedHandler;
import ar.edu.odontogestion.infrastructure.security.ProblemDetailAuthenticationEntryPoint;
import ar.edu.odontogestion.infrastructure.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(ErrorTestController.class)
@Import({SecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class})
@WithMockUser
class GlobalExceptionHandlerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void errorTieneCuerpoProblemDetailConStatusCoherente() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/99/explota"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.type").isNotEmpty());
    }

    @Test
    void instanceIndicaElPathSolicitado() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/99/explota"))
                .andExpect(jsonPath("$.instance").value("/api/v1/recurso-de-prueba/99/explota"));
        mockMvc.perform(get("/api/v1/recurso-de-prueba/7/explota"))
                .andExpect(jsonPath("$.instance").value("/api/v1/recurso-de-prueba/7/explota"));
    }

    @Test
    void tituloEsBreveYNoVacio() throws Exception {
        var body = mockMvc.perform(get("/api/v1/recurso-de-prueba/1/explota"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"title\":\"");
        mockMvc.perform(get("/api/v1/recurso-de-prueba/1/explota")).andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    void errorDeRutaInexistenteTambienEsProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/recurso-de-prueba/99/explota").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    void campoObligatorioAusenteDevuelve400ConErrorDelCampo() throws Exception {
        mockMvc.perform(post("/api/v1/recurso-de-prueba").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("nombre"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void variosCamposInvalidosGeneranUnaEntradaCadaUno() throws Exception {
        mockMvc.perform(post("/api/v1/recurso-de-prueba").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"email\":\"no-es-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field==\"nombre\")]").exists())
                .andExpect(jsonPath("$.errors[?(@.field==\"email\")]").exists());
    }

    @Test
    void jsonMalFormadoDevuelve400SinTextoDelParser() throws Exception {
        var respuesta = mockMvc.perform(post("/api/v1/recurso-de-prueba").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{nombre: sin-comillas"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain("Unexpected character").doesNotContain("JSON parse error")
                .doesNotContain("com.fasterxml").doesNotContain("sin-comillas");
    }

    @Test
    void cuerpoAusenteDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/recurso-de-prueba").with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void recursoNoEncontradoDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/5/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/recurso-no-encontrado"))
                .andExpect(jsonPath("$.detail").value("Paciente 5 no existe"))
                .andExpect(jsonPath("$.instance").value("/api/v1/recurso-de-prueba/5/no-encontrado"));
    }

    @Test
    void conflictoDevuelve409() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/5/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/conflicto"));
    }

    @Test
    void reglaDeNegocioDevuelve422ConElMensajeDeLaReglaEnDetail() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/5/regla"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("La duración debe ser múltiplo de 15 minutos"))
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/regla-de-negocio"));
    }

    @Test
    void errorInesperadoNoFiltraElMensajeNiLaTraza() throws Exception {
        var respuesta = mockMvc.perform(get("/api/v1/recurso-de-prueba/1/explota"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain("secreto-interno").doesNotContain("IllegalStateException")
                .doesNotContain("at ar.edu").doesNotContain("stackTrace");
    }

    @Test
    void errorInesperadoUsaDetalleGenericoFijo() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/1/explota"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado. Intente nuevamente más tarde."));
        mockMvc.perform(post("/api/v1/recurso-de-prueba/explota-con-cuerpo").with(csrf()).content("x"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado. Intente nuevamente más tarde."));
    }

    @Test
    void errorInesperadoSeRegistraSinElCuerpoDeLaSolicitud(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/recurso-de-prueba/explota-con-cuerpo").with(csrf()).content("cuerpo-sensible-123"))
                .andExpect(status().isInternalServerError());

        assertThat(output.getAll()).contains("Error no controlado").contains("fallo-interno")
                .doesNotContain("cuerpo-sensible-123");
    }

    @Test
    void validacionDeDominioDevuelve422ConUnaEntradaPorCampo() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/1/validacion-dominio"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("granularidadMin"))
                .andExpect(jsonPath("$.errors[0].message").value("Debe ser mayor que cero"))
                .andExpect(jsonPath("$.errors[1].field").value("recordatorioHorasAntes"));
    }

    @Test
    void reglaDeNegocioSinCamposNoIncluyeErrors() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/5/regla"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void demasiadosIntentosDevuelve429ConRetryAfterRedondeadoHaciaArriba() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/1/demasiados-intentos"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/demasiados-intentos"))
                .andExpect(jsonPath("$.instance").value("/api/v1/recurso-de-prueba/1/demasiados-intentos"));
    }

    @Test
    void tokenInvalidoDevuelve410ProblemDetailGenerico() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/token/" + "a".repeat(43)))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/token-invalido"))
                .andExpect(jsonPath("$.title").value("Enlace no disponible"))
                .andExpect(jsonPath("$.detail").value("El enlace no es válido o ya fue utilizado."));
    }

    @Test
    void causasDistintasYTokensDistintosProducenCuerposIdenticos() throws Exception {
        String vencido = mockMvc.perform(get("/api/v1/recurso-de-prueba/token/" + "V".repeat(43)).param("causa", "vencido"))
                .andExpect(status().isGone()).andReturn().getResponse().getContentAsString();
        String inexistente = mockMvc.perform(get("/api/v1/recurso-de-prueba/token/" + "I".repeat(43))
                        .param("causa", "inexistente"))
                .andExpect(status().isGone()).andReturn().getResponse().getContentAsString();

        assertThat(vencido).isEqualTo(inexistente);
    }

    @Test
    void elValorDelTokenNoApareceEnLaRespuestaNiEnLosLogs(CapturedOutput output) throws Exception {
        String valor = "Tk9_-" + "z".repeat(38);

        String cuerpo = mockMvc.perform(get("/api/v1/recurso-de-prueba/token/" + valor))
                .andExpect(status().isGone()).andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain(valor).doesNotContain("token/");
        assertThat(output.getAll()).doesNotContain(valor);
    }

    @Test
    void huecoNoDisponibleDevuelve409ConLasAlternativas() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/1/hueco-no-disponible"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/hueco-no-disponible"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.alternativas.length()").value(2))
                .andExpect(jsonPath("$.alternativas[0].inicio").value("2026-11-23T13:30:00Z"))
                .andExpect(jsonPath("$.alternativas[0].fin").value("2026-11-23T14:00:00Z"))
                .andExpect(jsonPath("$.alternativas[1].inicio").value("2026-11-23T13:45:00Z"));
    }

    @Test
    void huecoNoDisponibleSinAlternativasDevuelveLaListaVacia() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/1/hueco-no-disponible?alternativas=0"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.alternativas").isArray())
                .andExpect(jsonPath("$.alternativas.length()").value(0));
    }

    @Test
    void transicionInvalidaDevuelve422ConSuTipo() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/1/transicion-invalida"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/transicion-invalida"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CANCELADO")));
    }

    @Test
    void modificacionConcurrenteDevuelve409SinFiltrarClasesNiDetallesInternos() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/v1/recurso-de-prueba/77/modificacion-concurrente"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://odontogestion.example/problemas/modificacion-concurrente"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("ar.edu").doesNotContain("Hibernate").doesNotContain("Optimistic")
                .doesNotContain("SQL");
    }

    @Test
    void modificacionConcurrenteNoSeRegistraComoErrorInesperado(CapturedOutput salida) throws Exception {
        mockMvc.perform(get("/api/v1/recurso-de-prueba/78/modificacion-concurrente")).andExpect(status().isConflict());

        assertThat(salida.getOut() + salida.getErr()).doesNotContain("Error no controlado");
    }
}
