package ar.edu.odontogestion.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.edu.odontogestion.infrastructure.notificacion.ProcesadorNotificacionesJob;
import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import ar.edu.odontogestion.support.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Recorrido principal de punta a punta (C-12 design D4, spec {@code demo-environment}): perfil {@code demo},
 * PostgreSQL real con base propia, el {@code EmailChannel} real contra GreenMail y el reloj fijo en el miércoles
 * 2026-11-25 12:00 (Buenos Aires). Se pide el primer hueco del lunes siguiente, así el turno queda a más de 24 h y
 * después califica para el recordatorio de C-11. Datos ficticios: dominio reservado {@code .example}.
 */
@SpringBootTest(properties = {
        "app.bootstrap.odontologo-password=" + AbstractWebIntegrationTest.PASSWORD_SEED,
        "app.scheduling.enabled=false",
        "app.seed-demo.enabled=false",
        "app.mail-from=no-reply@consultorio.example",
        "spring.mail.host=localhost",
        "spring.mail.username=",
        "spring.mail.password=",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.starttls.required=false"})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(DemoSmokeIT.RelojMiercoles.class)
@ExtendWith(OutputCaptureExtension.class)
class DemoSmokeIT {

    static final Instant MIERCOLES = Instant.parse("2026-11-25T15:00:00Z");
    private static final String EMAIL = "smoke-demo@paciente.example";
    private static final String DNI = "91234567";
    private static final String TELEFONO = "+54 11 5555-7777";
    private static final Pattern ENLACE = Pattern.compile("/acciones/([A-Za-z0-9_-]+)");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort())
            .withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registro) {
        registro.add("spring.mail.port", () -> SMTP.getSmtp().getPort());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojMiercoles {

        @Bean
        @Primary
        MutableClock relojMiercoles() {
            return new MutableClock(MIERCOLES);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProcesadorNotificacionesJob outbox;

    /** Resultado de los pasos 1 a 5: el turno confirmado y el token usado. */
    private record TurnoConfirmado(Long id, Instant inicio, String token) {
    }

    @Test
    void solicitudYConfirmacionPorEmail(CapturedOutput salida) throws Exception {
        TurnoConfirmado turno = solicitarYConfirmar();

        assertThat(estado(turno.id())).isEqualTo("RESERVADO");
        MimeMessage confirmar = mensajes("Confirmá tu solicitud").get(0);
        assertThat(((InternetAddress) confirmar.getAllRecipients()[0]).getAddress()).isEqualTo(EMAIL);
        String cuerpo = texto(confirmar);
        assertThat(cuerpo).contains("http://localhost:8080/acciones/" + turno.token());
        // C-12 3.2: el email no lleva el DNI ni el teléfono; el token no aparece en ningún log.
        assertThat(cuerpo).doesNotContain(DNI).doesNotContain(TELEFONO).doesNotContain("Ficticia");
        assertThat(salida.getAll()).doesNotContain(turno.token()).doesNotContain(DNI);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE turno_id = ? AND estado = 'ENVIADA'",
                Integer.class, turno.id())).isGreaterThanOrEqualTo(1);
    }

    @Test
    void elAvisoDeTurnoConfirmadoTampocoLlevaDatosPersonalesYElEnlaceEsDeUnSoloUso(CapturedOutput salida)
            throws Exception {
        TurnoConfirmado turno = solicitarYConfirmar();

        outbox.ejecutar();

        List<MimeMessage> confirmados = mensajes("Tu turno en");
        assertThat(confirmados).hasSize(1);
        assertThat(texto(confirmados.get(0))).doesNotContain(DNI).doesNotContain(TELEFONO)
                .doesNotContain("/acciones/");
        int reuso = confirmarPorEnlace(turno.token()).getStatus();
        assertThat(reuso).isEqualTo(410);
        assertThat(salida.getAll()).doesNotContain(turno.token()).doesNotContain(EMAIL);
    }

    // --- Pasos del recorrido -----------------------------------------------------------------------------------

    private TurnoConfirmado solicitarYConfirmar() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        Cookie xsrf = mockMvc.perform(get("/api/v1/auth/me").session(sesion)).andReturn().getResponse()
                .getCookie("XSRF-TOKEN");

        // 1. Disponibilidad pública: primer hueco de "Consulta" del lunes siguiente.
        long prestacionId = 0;
        for (JsonNode p : leer(mockMvc.perform(get("/api/v1/publico/prestaciones")).andReturn().getResponse())) {
            if ("Consulta".equals(p.get("nombre").asText())) {
                prestacionId = p.get("id").asLong();
            }
        }
        assertThat(prestacionId).isPositive();
        JsonNode huecos = leer(mockMvc.perform(get("/api/v1/publico/disponibilidad")
                .param("prestacionId", String.valueOf(prestacionId))
                .param("desde", "2026-11-30").param("hasta", "2026-11-30")).andReturn().getResponse());
        assertThat(huecos.size()).isPositive();
        Instant inicio = Instant.parse(huecos.get(0).get("inicio").asText());

        // 2. Solicitud pública con datos ficticios.
        String solicitud = json.writeValueAsString(java.util.Map.of("prestacionId", prestacionId,
                "inicio", inicio.toString(), "nombre", "Prueba", "apellido", "Ficticia", "dni", DNI,
                "email", EMAIL, "telefono", TELEFONO));
        MockHttpServletResponse recibida = mockMvc.perform(post("/api/v1/publico/solicitudes-turno").session(sesion)
                .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()).with(r -> {
                    r.setRemoteAddr("10.99.0.1");
                    return r;
                }).contentType("application/json").content(solicitud)).andReturn().getResponse();
        assertThat(recibida.getStatus()).as(recibida.getContentAsString()).isBetween(200, 202);
        Long turnoId = jdbc.queryForObject("SELECT id FROM turno WHERE inicio = ?::timestamptz",
                Long.class, inicio.toString());
        assertThat(estado(turnoId)).isEqualTo("PENDIENTE_CONFIRMACION");

        // 3. Outbox procesado en forma directa, sin esperar el @Scheduled.
        outbox.ejecutar();

        // 4. Email recibido en el servidor SMTP de pruebas: token del enlace /acciones/{token}.
        List<MimeMessage> confirmar = mensajes("Confirmá tu solicitud");
        assertThat(confirmar).hasSize(1);
        Matcher m = ENLACE.matcher(texto(confirmar.get(0)));
        assertThat(m.find()).isTrue();
        String token = m.group(1);

        // 5. Confirmación por el enlace (otra sesión, como al abrir el email): el turno queda RESERVADO.
        MockHttpServletResponse confirmada = confirmarPorEnlace(token);
        assertThat(confirmada.getStatus()).as(confirmada.getContentAsString()).isEqualTo(200);
        return new TurnoConfirmado(turnoId, inicio, token);
    }

    // --- Apoyo -----------------------------------------------------------------------------------------------

    @BeforeEach
    void vaciarBuzon() throws Exception {
        // Lo que dejó encolado un test anterior sale ahora y no se mezcla con los emails de este test.
        outbox.ejecutar();
        SMTP.purgeEmailFromAllMailboxes();
    }

    private MockHttpServletResponse confirmarPorEnlace(String token) throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        Cookie xsrf = mockMvc.perform(get("/api/v1/auth/me").session(sesion)).andReturn().getResponse()
                .getCookie("XSRF-TOKEN");
        return mockMvc.perform(post("/api/v1/acciones/" + token).session(sesion).cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).contentType("application/json")
                .content("{\"accion\":\"CONFIRMAR_SOLICITUD\"}")).andReturn().getResponse();
    }

    private JsonNode leer(MockHttpServletResponse respuesta) throws Exception {
        assertThat(respuesta.getStatus()).as(respuesta.getContentAsString()).isEqualTo(200);
        return json.readTree(respuesta.getContentAsString());
    }

    private String estado(Long turnoId) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turnoId);
    }

    /** Cuerpo decodificado (el crudo viene en quoted-printable y corta las líneas largas, tokens incluidos). */
    private static String texto(MimeMessage mensaje) throws Exception {
        return mensaje.getContent().toString();
    }

    private static List<MimeMessage> mensajes(String asunto) {
        return Arrays.stream(SMTP.getReceivedMessages()).filter(m -> {
            try {
                return m.getSubject().startsWith(asunto);
            } catch (jakarta.mail.MessagingException e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }
}
