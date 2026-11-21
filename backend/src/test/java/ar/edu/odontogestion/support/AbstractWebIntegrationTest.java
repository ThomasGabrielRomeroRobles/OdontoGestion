package ar.edu.odontogestion.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base de los ITs que pasan por HTTP (MockMvc + cadena de seguridad real + PostgreSQL). Comparte una única
 * variante de contexto (reloj fijo + MockMvc). Los tests que fallan logins o modifican datos usan usuarios
 * propios (nunca el seed) y una IP distinta por intento, porque el limitador de login vive en el contexto.
 */
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
public abstract class AbstractWebIntegrationTest extends AbstractIntegrationTest {

    public static final String EMAIL_SEED = "odontologo@consultorio.example";
    public static final String PASSWORD_SEED = "Ficticia-Test-123";

    private static final AtomicInteger SECUENCIA_IP = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Sesión HTTP y cookie CSRF vigentes de un cliente. */
    public record Cliente(MockHttpSession session, Cookie xsrf) {

        public MockHttpServletRequestBuilder firmar(MockHttpServletRequestBuilder builder) {
            return builder.session(session).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
        }
    }

    protected static String ipNueva() {
        int n = SECUENCIA_IP.incrementAndGet();
        return "10.77." + (n / 250) + "." + (n % 250 + 1);
    }

    /** Cliente anónimo: GET /auth/me emite la cookie XSRF-TOKEN, como hace el frontend al arrancar. */
    protected Cliente clienteAnonimo() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse respuesta = mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andReturn().getResponse();
        return new Cliente(session, respuesta.getCookie("XSRF-TOKEN"));
    }

    /** El login borra y vuelve a emitir la cookie: el navegador se queda con la última. */
    public static Cookie ultimaCookie(MockHttpServletResponse respuesta, String nombre) {
        Cookie ultima = null;
        for (Cookie c : respuesta.getCookies()) {
            if (c.getName().equals(nombre)) {
                ultima = c;
            }
        }
        return ultima;
    }

    protected static String cuerpoLogin(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    protected MockHttpServletResponse login(Cliente cliente, String email, String password, String ip)
            throws Exception {
        return mockMvc.perform(cliente.firmar(post("/api/v1/auth/login"))
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType("application/json")
                        .content(cuerpoLogin(email, password)))
                .andReturn().getResponse();
    }

    /** Inicia sesión y devuelve el cliente con la sesión y la cookie CSRF rotada por el login. */
    protected Cliente iniciarSesion(String email, String password) throws Exception {
        Cliente anonimo = clienteAnonimo();
        MockHttpServletResponse respuesta = login(anonimo, email, password, ipNueva());
        if (respuesta.getStatus() != 200) {
            throw new IllegalStateException("Login de prueba rechazado: HTTP " + respuesta.getStatus());
        }
        Cookie rotada = ultimaCookie(respuesta, "XSRF-TOKEN");
        return new Cliente(anonimo.session(), rotada != null ? rotada : anonimo.xsrf());
    }

    protected Cliente iniciarSesionSeed() throws Exception {
        return iniciarSesion(EMAIL_SEED, PASSWORD_SEED);
    }

    protected String crearUsuario(String prefijo, String password, boolean activo) {
        String email = prefijo + "-" + UUID.randomUUID().toString().substring(0, 8) + "@ejemplo.test";
        Long consultorioId = jdbc.queryForObject("SELECT min(id) FROM consultorio", Long.class);
        jdbc.update("INSERT INTO usuario (consultorio_id, email, password_hash, nombre, apellido, activo) "
                + "VALUES (?, ?, ?, 'Prueba', 'Ficticia', ?)", consultorioId, email,
                passwordEncoder.encode(password), activo);
        return email;
    }

    protected void borrarUsuario(String email) {
        jdbc.update("DELETE FROM usuario WHERE email = ?::citext", email);
    }
}
