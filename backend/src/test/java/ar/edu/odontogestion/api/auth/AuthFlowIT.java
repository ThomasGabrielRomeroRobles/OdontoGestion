package ar.edu.odontogestion.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class AuthFlowIT extends AbstractWebIntegrationTest {

    @Autowired
    private ObjectMapper json;

    @Test
    void loginCorrectoConCsrfDevuelve200ConElEmail() throws Exception {
        Cliente anonimo = clienteAnonimo();

        mockMvc.perform(anonimo.firmar(post("/api/v1/auth/login")).contentType("application/json")
                        .with(r -> {
                            r.setRemoteAddr(ipNueva());
                            return r;
                        })
                        .content(cuerpoLogin(EMAIL_SEED, PASSWORD_SEED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("odontologo@consultorio.example"))
                .andExpect(jsonPath("$.nombre").value("Odontólogo Demo"))
                .andExpect(jsonPath("$.apellido").value("Ficticio"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void emailEnMayusculasAutenticaYDevuelveElEmailGuardado() throws Exception {
        Cliente anonimo = clienteAnonimo();

        MockHttpServletResponse r = login(anonimo, "ODONTOLOGO@Consultorio.EXAMPLE", PASSWORD_SEED, ipNueva());

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(json.readTree(r.getContentAsString()).get("email").asText())
                .isEqualTo("odontologo@consultorio.example");
    }

    @Test
    void passwordIncorrectaEmailInexistenteYUsuarioInactivoDanLaMismaRespuesta401() throws Exception {
        String inactivo = crearUsuario("inactivo", "Ficticia-Inactiva-1", false);
        try {
            Cliente c = clienteAnonimo();
            MockHttpServletResponse mala = login(c, EMAIL_SEED, "Contrasena-Incorrecta-1", ipNueva());
            MockHttpServletResponse inexistente = login(c, "nadie@ejemplo.test", "Ficticia-Test-123", ipNueva());
            MockHttpServletResponse desactivado = login(c, inactivo, "Ficticia-Inactiva-1", ipNueva());

            for (MockHttpServletResponse r : new MockHttpServletResponse[] {mala, inexistente, desactivado}) {
                assertThat(r.getStatus()).isEqualTo(401);
            }
            JsonNode a = json.readTree(mala.getContentAsString());
            JsonNode b = json.readTree(inexistente.getContentAsString());
            JsonNode d = json.readTree(desactivado.getContentAsString());
            assertThat(a.get("detail").asText()).isEqualTo("Email o contraseña incorrectos.");
            assertThat(b.get("title")).isEqualTo(a.get("title"));
            assertThat(b.get("detail")).isEqualTo(a.get("detail"));
            assertThat(d.get("title")).isEqualTo(a.get("title"));
            assertThat(d.get("detail")).isEqualTo(a.get("detail"));
        } finally {
            borrarUsuario(inactivo);
        }
    }

    @Test
    void sinPasswordDevuelve400ConElCampo() throws Exception {
        Cliente c = clienteAnonimo();

        mockMvc.perform(c.firmar(post("/api/v1/auth/login")).contentType("application/json")
                        .content("{\"email\":\"odontologo@consultorio.example\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
    }

    @Test
    void passwordDeMilVeinticincoCaracteresYEmailDeDoscientosCincuentaYCincoDan400ConCampo() throws Exception {
        Cliente c = clienteAnonimo();
        String emailLargo = "a".repeat(255 - "@ejemplo.test".length()) + "@ejemplo.test";

        mockMvc.perform(c.firmar(post("/api/v1/auth/login")).contentType("application/json")
                        .content(cuerpoLogin(EMAIL_SEED, "p".repeat(1025))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
        mockMvc.perform(c.firmar(post("/api/v1/auth/login")).contentType("application/json")
                        .content(cuerpoLogin(emailLargo, "Ficticia-Test-123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
    }

    @Test
    void tras401LaSesionSigueSinAutenticar() throws Exception {
        Cliente c = clienteAnonimo();

        assertThat(login(c, EMAIL_SEED, "Contrasena-Incorrecta-2", ipNueva()).getStatus()).isEqualTo(401);

        mockMvc.perform(get("/api/v1/auth/me").session(c.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void meConSesionDevuelveEmailNombreYApellido() throws Exception {
        Cliente c = iniciarSesionSeed();

        mockMvc.perform(get("/api/v1/auth/me").session(c.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.email").value("odontologo@consultorio.example"))
                .andExpect(jsonPath("$.nombre").value("Odontólogo Demo"))
                .andExpect(jsonPath("$.apellido").value("Ficticio"));
    }

    @Test
    void meSinSesionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void elLoginRotaElIdDeSesionAnonimaPrevia() throws Exception {
        Cliente anonimo = clienteAnonimo();
        String idPrevio = anonimo.session().getId();
        assertThat(anonimo.session().isNew() || idPrevio != null).isTrue();

        MockHttpServletResponse r = login(anonimo, EMAIL_SEED, PASSWORD_SEED, ipNueva());

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(anonimo.session().getId()).isNotEqualTo(idPrevio);
        assertThat(anonimo.session().getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
        // una sesión nueva con el id viejo no está autenticada
        mockMvc.perform(get("/api/v1/auth/me").session(new MockHttpSession(null, idPrevio)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elLoginEmiteUnXsrfTokenNuevoDistintoDelPrevio() throws Exception {
        Cliente anonimo = clienteAnonimo();

        MockHttpServletResponse r = login(anonimo, EMAIL_SEED, PASSWORD_SEED, ipNueva());

        Cookie nueva = ultimaCookie(r, "XSRF-TOKEN");
        assertThat(nueva).isNotNull();
        assertThat(nueva.getValue()).isNotBlank().isNotEqualTo(anonimo.xsrf().getValue());
    }

    @Test
    void elLoginActualizaUltimoAccesoConElRelojFijo() throws Exception {
        iniciarSesionSeed();

        OffsetDateTime ultimo = jdbc.queryForObject(
                "SELECT ultimo_acceso FROM usuario WHERE email = ?::citext", OffsetDateTime.class, EMAIL_SEED);

        assertThat(ultimo.toInstant()).isEqualTo(FixedClockTestConfig.AHORA);
    }

    @Test
    void loginConCredencialesCorrectasPeroSinCabeceraCsrfDa403YNoDejaSesion() throws Exception {
        Cliente anonimo = clienteAnonimo();

        mockMvc.perform(post("/api/v1/auth/login").session(anonimo.session()).cookie(anonimo.xsrf())
                        .contentType("application/json").content(cuerpoLogin(EMAIL_SEED, PASSWORD_SEED)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/auth/me").session(anonimo.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordDeCienBytesConEmailExistenteDa401GenericoYNoError500() throws Exception {
        Cliente c = clienteAnonimo();

        MockHttpServletResponse r = login(c, EMAIL_SEED, "x".repeat(100), ipNueva());

        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(json.readTree(r.getContentAsString()).get("detail").asText())
                .isEqualTo("Email o contraseña incorrectos.");
    }

    @Test
    void logoutConSesionYCsrfDevuelve204YLaSesionDejaDeValer() throws Exception {
        Cliente c = iniciarSesionSeed();

        mockMvc.perform(c.firmar(post("/api/v1/auth/logout"))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").session(c.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutExpiraLasCookiesXsrfTokenYJsessionid() throws Exception {
        Cliente c = iniciarSesionSeed();

        MockHttpServletResponse r = mockMvc.perform(c.firmar(post("/api/v1/auth/logout")))
                .andExpect(status().isNoContent()).andReturn().getResponse();

        Cookie xsrf = ultimaCookie(r, "XSRF-TOKEN");
        Cookie sesion = ultimaCookie(r, "JSESSIONID");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.getMaxAge()).isZero();
        assertThat(sesion).isNotNull();
        assertThat(sesion.getMaxAge()).isZero();
    }

    @Test
    void logoutSinSesionDevuelve204() throws Exception {
        Cliente anonimo = clienteAnonimo();

        mockMvc.perform(anonimo.firmar(post("/api/v1/auth/logout"))).andExpect(status().isNoContent());
    }

    @Test
    void logoutSinCsrfDevuelve403YLaSesionSigueVigente() throws Exception {
        Cliente c = iniciarSesionSeed();

        mockMvc.perform(post("/api/v1/auth/logout").session(c.session()).cookie(c.xsrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/auth/me").session(c.session())).andExpect(status().isOk());
    }

    @Test
    void unaSesionInvalidadaEnElServidorHace401EnLasRutasPrivadas() throws Exception {
        Cliente c = iniciarSesionSeed();
        mockMvc.perform(get("/api/v1/seguridad-prueba/privado").session(c.session())).andExpect(status().isOk());

        c.session().invalidate();

        mockMvc.perform(get("/api/v1/seguridad-prueba/privado").session(c.session()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/consultorio").session(c.session())).andExpect(status().isUnauthorized());
    }
}
