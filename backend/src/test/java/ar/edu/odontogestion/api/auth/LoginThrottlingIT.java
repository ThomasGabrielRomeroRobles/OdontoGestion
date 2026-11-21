package ar.edu.odontogestion.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

@ExtendWith(OutputCaptureExtension.class)
class LoginThrottlingIT extends AbstractWebIntegrationTest {

    @Autowired
    private ObjectMapper json;

    @Test
    void cincoFallosYLuegoUnoCorrectoDa429ConRetryAfterYSinSesion(CapturedOutput output) throws Exception {
        String email = crearUsuario("limite", "Ficticia-Limite-1", true);
        try {
            Cliente c = clienteAnonimo();
            for (int i = 0; i < 5; i++) {
                assertThat(login(c, email, "Mala-Clave-ZZ" + i, ipNueva()).getStatus()).isEqualTo(401);
            }

            Cliente nuevo = clienteAnonimo();
            MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login").cookie(nuevo.xsrf())
                            .header("X-XSRF-TOKEN", nuevo.xsrf().getValue())
                            .with(r -> {
                                r.setRemoteAddr(ipNueva());
                                return r;
                            })
                            .contentType("application/json").content(cuerpoLogin(email, "Ficticia-Limite-1")))
                    .andReturn();

            MockHttpServletResponse r = resultado.getResponse();
            assertThat(r.getStatus()).isEqualTo(429);
            assertThat(Long.parseLong(r.getHeader("Retry-After"))).isBetween(1L, 60L);
            assertThat(json.readTree(r.getContentAsString()).get("status").asInt()).isEqualTo(429);
            assertThat(r.getCookie("JSESSIONID")).isNull();
            assertThat(resultado.getRequest().getSession(false)).isNull();
            assertThat(output.getAll()).doesNotContain("Mala-Clave-ZZ").doesNotContain("Ficticia-Limite-1");
        } finally {
            borrarUsuario(email);
        }
    }

    @Test
    void conUnEmailInexistenteElSextoIntentoTambienDa429() throws Exception {
        String inexistente = "fantasma-" + System.nanoTime() + "@ejemplo.test";
        Cliente c = clienteAnonimo();
        for (int i = 0; i < 5; i++) {
            assertThat(login(c, inexistente, "Cualquier-Clave-1", ipNueva()).getStatus()).isEqualTo(401);
        }

        assertThat(login(c, inexistente, "Cualquier-Clave-1", ipNueva()).getStatus()).isEqualTo(429);
    }

    @Test
    void cincoFallosDesdeLaMismaIpConXForwardedForDistintoBloqueanElSexto() throws Exception {
        String ip = ipNueva();
        Cliente c = clienteAnonimo();
        for (int i = 0; i < 5; i++) {
            final int n = i;
            MvcResult r = mockMvc.perform(c.firmar(post("/api/v1/auth/login"))
                            .with(req -> {
                                req.setRemoteAddr(ip);
                                return req;
                            })
                            .header("X-Forwarded-For", "203.0.113." + n)
                            .contentType("application/json")
                            .content(cuerpoLogin("persona" + n + "@ejemplo.test", "Cualquier-Clave-1")))
                    .andReturn();
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
        }

        MvcResult sexto = mockMvc.perform(c.firmar(post("/api/v1/auth/login"))
                        .with(req -> {
                            req.setRemoteAddr(ip);
                            return req;
                        })
                        .header("X-Forwarded-For", "203.0.113.99")
                        .contentType("application/json")
                        .content(cuerpoLogin("otra@ejemplo.test", "Cualquier-Clave-1")))
                .andReturn();

        assertThat(sexto.getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void ningunaContrasenaApareceEnElLog(CapturedOutput output) throws Exception {
        Cliente c = clienteAnonimo();

        login(c, EMAIL_SEED, "Clave-Secreta-Del-Log-9", ipNueva());
        login(c, "nadie@ejemplo.test", "Otra-Clave-Secreta-9", ipNueva());

        assertThat(output.getAll()).doesNotContain("Clave-Secreta-Del-Log-9").doesNotContain("Otra-Clave-Secreta-9");
    }
}
