package ar.edu.odontogestion.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.api.error.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SeguridadTestController.class)
@Import({SecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class, GlobalExceptionHandler.class})
class CsrfWebMvcTest {

    private static final String RUTA = "/api/v1/seguridad-prueba/privado";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void reiniciarContador() {
        SeguridadTestController.INVOCACIONES.set(0);
    }

    @Test
    void escrituraPrivadaSinCabeceraCsrfDevuelve403ProblemJsonYNoLlegaAlController() throws Exception {
        mockMvc.perform(put(RUTA).with(user("odontologo")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value(RUTA));

        assertThat(SeguridadTestController.INVOCACIONES.get()).isZero();
    }

    @Test
    void cabeceraQueNoCoincideConLaCookieDevuelve403() throws Exception {
        mockMvc.perform(put(RUTA).with(user("odontologo"))
                        .cookie(new Cookie("XSRF-TOKEN", "valor-de-la-cookie"))
                        .header("X-XSRF-TOKEN", "otro-valor"))
                .andExpect(status().isForbidden());

        assertThat(SeguridadTestController.INVOCACIONES.get()).isZero();
    }

    @Test
    void elValorCrudoDeLaCookieEnLaCabeceraEsAceptado() throws Exception {
        mockMvc.perform(put(RUTA).with(user("odontologo"))
                        .cookie(new Cookie("XSRF-TOKEN", "valor-crudo-de-la-cookie"))
                        .header("X-XSRF-TOKEN", "valor-crudo-de-la-cookie"))
                .andExpect(status().isOk());

        assertThat(SeguridadTestController.INVOCACIONES.get()).isEqualTo(1);
    }

    @Test
    void laCookieXsrfSeEmiteEnLaRespuesta401DeAuthMeSinHttpOnlyYConPathRaiz() throws Exception {
        var respuesta = mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().path("XSRF-TOKEN", "/"))
                .andReturn().getResponse();

        assertThat(respuesta.getCookie("XSRF-TOKEN").getValue()).isNotBlank();
        assertThat(respuesta.getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(h -> h.startsWith("XSRF-TOKEN="));
    }

    @Test
    void laCookieXsrfTambienSaleEnUnaRespuestaExitosa() throws Exception {
        mockMvc.perform(get(RUTA).with(user("odontologo")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void unGetSinCabeceraCsrfNoEsRechazadoPorCsrf() throws Exception {
        mockMvc.perform(get(RUTA).with(user("odontologo"))).andExpect(status().isOk());
    }
}
