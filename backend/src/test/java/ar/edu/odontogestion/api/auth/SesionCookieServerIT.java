package ar.edu.odontogestion.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sesión y cookies sobre el Tomcat real (RANDOM_PORT): el cliente HTTP no gestiona cookies, así se ven los
 * Set-Cookie crudos. Cada test usa un usuario propio (el limitador de login vive en el contexto y todas las
 * peticiones llegan desde 127.0.0.1, así que solo se hacen logins exitosos o rechazos por CSRF).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.bootstrap.odontologo-password=Ficticia-Test-123")
@Import({FixedClockTestConfig.class, SesionCookieServerIT.ExpiracionPorCabeceraConfig.class})
class SesionCookieServerIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Ficticia-Server-1";

    /** Solo de test: acorta el timeout de la sesión viva a 1 s cuando llega la cabecera X-Test-Inactividad-Seg. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ExpiracionPorCabeceraConfig {
        @Bean
        OncePerRequestFilter acortarInactividadSoloEnTests() {
            return new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws ServletException, IOException {
                    String seg = req.getHeader("X-Test-Inactividad-Seg");
                    HttpSession s = req.getSession(false);
                    if (seg != null && s != null) {
                        s.setMaxInactiveInterval(Integer.parseInt(seg));
                    }
                    chain.doFilter(req, res);
                }
            };
        }
    }

    @LocalServerPort
    int puerto;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private String email;

    @BeforeEach
    void crearUsuario() {
        email = "server-" + UUID.randomUUID().toString().substring(0, 8) + "@ejemplo.test";
        Long consultorioId = jdbc.queryForObject("SELECT min(id) FROM consultorio", Long.class);
        jdbc.update("INSERT INTO usuario (consultorio_id, email, password_hash, nombre, apellido, activo) "
                + "VALUES (?, ?, ?, 'Prueba', 'Ficticia', true)", consultorioId, email, encoder.encode(PASSWORD));
    }

    @AfterEach
    void borrarUsuario() {
        jdbc.update("DELETE FROM usuario WHERE email = ?::citext", email);
    }

    // ---- helpers ----

    private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo, String cookies, String xsrf,
            String... cabecerasExtra) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .timeout(Duration.ofSeconds(10));
        if (cuerpo != null) {
            b.header("Content-Type", "application/json");
        }
        if (cookies != null && !cookies.isBlank()) {
            b.header("Cookie", cookies);
        }
        if (xsrf != null) {
            b.header("X-XSRF-TOKEN", xsrf);
        }
        for (int i = 0; i < cabecerasExtra.length; i += 2) {
            b.header(cabecerasExtra[i], cabecerasExtra[i + 1]);
        }
        b.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Último Set-Cookie con ese nombre (el navegador se queda con el último). */
    private static Optional<String> setCookie(HttpResponse<?> r, String nombre) {
        List<String> todas = r.headers().allValues("set-cookie");
        return todas.stream().filter(c -> c.startsWith(nombre + "=")).reduce((a, b) -> b);
    }

    private static String valor(String setCookie) {
        String par = setCookie.split(";", 2)[0];
        return par.substring(par.indexOf('=') + 1);
    }

    private static String lower(String s) {
        return s.toLowerCase();
    }

    private static String login(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    private record Sesion(String jsessionid, String xsrf) {
        String cookies() {
            return "JSESSIONID=" + jsessionid + "; XSRF-TOKEN=" + xsrf;
        }
    }

    private String xsrfAnonimo() throws Exception {
        HttpResponse<String> me = enviar("GET", "/api/v1/auth/me", null, null, null);
        return valor(setCookie(me, "XSRF-TOKEN").orElseThrow());
    }

    private Sesion iniciarSesion() throws Exception {
        String xsrf = xsrfAnonimo();
        HttpResponse<String> r = enviar("POST", "/api/v1/auth/login", login(email), "XSRF-TOKEN=" + xsrf, xsrf);
        assertThat(r.statusCode()).isEqualTo(200);
        return new Sesion(valor(setCookie(r, "JSESSIONID").orElseThrow()),
                valor(setCookie(r, "XSRF-TOKEN").orElseThrow()));
    }

    // ---- tests ----

    @Test
    void meSinSesionDa401YEmiteXsrfTokenLegibleDesdeJs() throws Exception {
        HttpResponse<String> r = enviar("GET", "/api/v1/auth/me", null, null, null);

        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(r.headers().firstValue("content-type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/problem+json"));
        String xsrf = setCookie(r, "XSRF-TOKEN").orElseThrow();
        assertThat(lower(xsrf)).doesNotContain("httponly");
        assertThat(valor(xsrf)).isNotBlank();
    }

    @Test
    void loginExitosoEmiteCookieDeSesionHttpOnlySameSiteLaxSinSecureYRotaElXsrf() throws Exception {
        String xsrfPrevio = xsrfAnonimo();

        HttpResponse<String> r = enviar("POST", "/api/v1/auth/login", login(email), "XSRF-TOKEN=" + xsrfPrevio,
                xsrfPrevio);

        assertThat(r.statusCode()).isEqualTo(200);
        String sesion = lower(setCookie(r, "JSESSIONID").orElseThrow());
        assertThat(sesion).contains("httponly").contains("samesite=lax").doesNotContain("secure;")
                .doesNotEndWith("secure");
        String xsrf = setCookie(r, "XSRF-TOKEN").orElseThrow();
        assertThat(lower(xsrf)).doesNotContain("httponly");
        assertThat(valor(xsrf)).isNotBlank().isNotEqualTo(xsrfPrevio);
    }

    @Test
    void elIdDeSesionPrevioNoSeReutilizaTrasElLogin() throws Exception {
        String xsrf = xsrfAnonimo();
        String fijado = "S1-FIJADO-POR-ATACANTE";

        HttpResponse<String> r = enviar("POST", "/api/v1/auth/login", login(email),
                "JSESSIONID=" + fijado + "; XSRF-TOKEN=" + xsrf, xsrf);

        assertThat(r.statusCode()).isEqualTo(200);
        String nuevo = valor(setCookie(r, "JSESSIONID").orElseThrow());
        assertThat(nuevo).isNotBlank().isNotEqualTo(fijado);
        HttpResponse<String> conFijado = enviar("GET", "/api/v1/auth/me", null, "JSESSIONID=" + fijado, null);
        assertThat(conFijado.statusCode()).isEqualTo(401);
        HttpResponse<String> conNuevo = enviar("GET", "/api/v1/auth/me", null, "JSESSIONID=" + nuevo, null);
        assertThat(conNuevo.statusCode()).isEqualTo(200);
    }

    @Test
    void ningunaRespuestaExponeElIdDeSesionEnLaUrl() throws Exception {
        String xsrf = xsrfAnonimo();
        HttpResponse<String> login = enviar("POST", "/api/v1/auth/login", login(email), "XSRF-TOKEN=" + xsrf, xsrf);
        Sesion s = new Sesion(valor(setCookie(login, "JSESSIONID").orElseThrow()),
                valor(setCookie(login, "XSRF-TOKEN").orElseThrow()));
        HttpResponse<String> me = enviar("GET", "/api/v1/auth/me", null, s.cookies(), null);
        HttpResponse<String> raiz = enviar("GET", "/", null, s.cookies(), null);

        for (HttpResponse<String> r : List.of(login, me, raiz)) {
            assertThat(r.headers().firstValue("location").orElse("")).doesNotContainIgnoringCase("jsessionid");
            assertThat(r.body()).doesNotContainIgnoringCase(";jsessionid=");
        }
    }

    @Test
    void loginSinCabeceraCsrfDa403ProblemDetailYNoCreaSesionAutenticada() throws Exception {
        String xsrf = xsrfAnonimo();

        HttpResponse<String> r = enviar("POST", "/api/v1/auth/login", login(email), "XSRF-TOKEN=" + xsrf, null);

        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(r.headers().firstValue("content-type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/problem+json"));
        assertThat(setCookie(r, "JSESSIONID")).isEmpty();
    }

    @Test
    void loginConCabeceraCsrfQueNoCoincideConLaCookieDa403() throws Exception {
        String xsrf = xsrfAnonimo();

        HttpResponse<String> r = enviar("POST", "/api/v1/auth/login", login(email), "XSRF-TOKEN=" + xsrf,
                "otro-valor-distinto");

        assertThat(r.statusCode()).isEqualTo(403);
    }

    @Test
    void elCambioDePasswordRotaElJsessionidYElNuevoSigueFuncionando() throws Exception {
        Sesion s = iniciarSesion();

        HttpResponse<String> r = enviar("POST", "/api/v1/perfil/password",
                "{\"passwordActual\":\"" + PASSWORD + "\",\"passwordNueva\":\"Ficticia-Nueva-77\"}",
                s.cookies(), s.xsrf());

        assertThat(r.statusCode()).isEqualTo(204);
        String nuevo = valor(setCookie(r, "JSESSIONID").orElseThrow());
        assertThat(nuevo).isNotEqualTo(s.jsessionid());
        assertThat(enviar("GET", "/api/v1/auth/me", null, "JSESSIONID=" + nuevo, null).statusCode()).isEqualTo(200);
        assertThat(enviar("GET", "/api/v1/auth/me", null, "JSESSIONID=" + s.jsessionid(), null).statusCode())
                .isEqualTo(401);
    }

    @Test
    void logoutDa204ExpiraLaCookieYLaSesionVieja401() throws Exception {
        Sesion s = iniciarSesion();

        HttpResponse<String> r = enviar("POST", "/api/v1/auth/logout", null, s.cookies(), s.xsrf());

        assertThat(r.statusCode()).isEqualTo(204);
        String expirada = lower(setCookie(r, "JSESSIONID").orElseThrow());
        assertThat(expirada).contains("max-age=0");
        assertThat(enviar("GET", "/api/v1/auth/me", null, "JSESSIONID=" + s.jsessionid(), null).statusCode())
                .isEqualTo(401);
    }

    @Test
    void sesionInactivaExpiraYDa401() throws Exception {
        Sesion s = iniciarSesion();
        // Acorta el timeout de la sesión viva a 1 s (hook solo de test) y la toca para fijar el último acceso.
        HttpResponse<String> toque = enviar("GET", "/api/v1/auth/me", null, s.cookies(), null,
                "X-Test-Inactividad-Seg", "1");
        assertThat(toque.statusCode()).isEqualTo(200);

        Thread.sleep(2500);

        assertThat(enviar("GET", "/api/v1/auth/me", null, s.cookies(), null).statusCode()).isEqualTo(401);
    }
}
