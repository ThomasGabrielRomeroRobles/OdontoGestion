package ar.edu.odontogestion.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.api.error.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SeguridadTestController.class)
@Import({SecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class, GlobalExceptionHandler.class})
class SecurityRulesWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rutaPrivadaSinSesionDevuelve401ProblemJsonSinCabecerasDeChallengeNiRedireccion() throws Exception {
        mockMvc.perform(get("/api/v1/consultorio"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/v1/consultorio"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void rutaDeLaApiInexistenteSinSesionTambienDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/ruta-inexistente")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v2/futura")).andExpect(status().isUnauthorized());
    }

    @Test
    void rutaPrivadaConSesionLlegaAlController() throws Exception {
        mockMvc.perform(get("/api/v1/seguridad-prueba/privado").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("odontologo")))
                .andExpect(status().isOk());
    }

    @Test
    void rutasPublicasNoExigenSesion() throws Exception {
        for (String ruta : new String[] {"/api/v1/publico/prueba", "/api/v1/acciones/prueba",
                "/api/v1/avisos/prueba"}) {
            mockMvc.perform(get(ruta)).andExpect(status().isOk());
        }
    }

    @Test
    void rutasPublicasInexistentesNoDan401Ni403() throws Exception {
        mockMvc.perform(get("/api/v1/publico/otra")).andExpect(status().isNotFound());
    }

    @Test
    void estaticosYRutasDelSpaNoSonRechazadosPorLaSeguridad() throws Exception {
        for (String ruta : new String[] {"/", "/assets/x.js", "/agenda"}) {
            int estado = mockMvc.perform(get(ruta)).andReturn().getResponse().getStatus();
            assertThat(estado).as(ruta).isNotIn(401, 403);
        }
    }

    @Test
    void basicAuthenticationSinSesionNoAutentica() throws Exception {
        String basic = Base64.getEncoder().encodeToString("odontologo:cualquiera".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/seguridad-prueba/privado").header(HttpHeaders.AUTHORIZATION, "Basic " + basic))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void preflightConOrigenAjenoNoRecibeCabecerasCors() throws Exception {
        var respuesta = mockMvc.perform(options("/api/v1/seguridad-prueba/privado")
                        .header(HttpHeaders.ORIGIN, "http://sitio-ajeno.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andReturn().getResponse();

        assertThat(respuesta.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    @WithMockUser
    void getConOrigenAjenoNoRecibeCabecerasCors() throws Exception {
        var respuesta = mockMvc.perform(get("/api/v1/seguridad-prueba/privado")
                        .header(HttpHeaders.ORIGIN, "http://sitio-ajeno.example"))
                .andReturn().getResponse();

        assertThat(respuesta.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }
}
