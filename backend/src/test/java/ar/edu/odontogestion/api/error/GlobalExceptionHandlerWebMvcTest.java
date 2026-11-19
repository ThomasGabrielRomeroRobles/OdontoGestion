package ar.edu.odontogestion.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(ErrorTestController.class)
@AutoConfigureMockMvc(addFilters = false)
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
        mockMvc.perform(post("/api/v1/recurso-de-prueba/99/explota"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    void campoObligatorioAusenteDevuelve400ConErrorDelCampo() throws Exception {
        mockMvc.perform(post("/api/v1/recurso-de-prueba").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("nombre"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void variosCamposInvalidosGeneranUnaEntradaCadaUno() throws Exception {
        mockMvc.perform(post("/api/v1/recurso-de-prueba").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"email\":\"no-es-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field==\"nombre\")]").exists())
                .andExpect(jsonPath("$.errors[?(@.field==\"email\")]").exists());
    }

    @Test
    void jsonMalFormadoDevuelve400SinTextoDelParser() throws Exception {
        var respuesta = mockMvc.perform(post("/api/v1/recurso-de-prueba").contentType(MediaType.APPLICATION_JSON)
                        .content("{nombre: sin-comillas"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain("Unexpected character").doesNotContain("JSON parse error")
                .doesNotContain("com.fasterxml").doesNotContain("sin-comillas");
    }

    @Test
    void cuerpoAusenteDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/recurso-de-prueba").contentType(MediaType.APPLICATION_JSON))
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
        mockMvc.perform(post("/api/v1/recurso-de-prueba/explota-con-cuerpo").content("x"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado. Intente nuevamente más tarde."));
    }

    @Test
    void errorInesperadoSeRegistraSinElCuerpoDeLaSolicitud(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/recurso-de-prueba/explota-con-cuerpo").content("cuerpo-sensible-123"))
                .andExpect(status().isInternalServerError());

        assertThat(output.getAll()).contains("Error no controlado").contains("fallo-interno")
                .doesNotContain("cuerpo-sensible-123");
    }
}
