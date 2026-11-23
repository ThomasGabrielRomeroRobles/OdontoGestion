package ar.edu.odontogestion.api.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.edu.odontogestion.application.turno.ExpiracionTurnosService;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@code POST /api/v1/publico/solicitudes-turno} de punta a punta (C-09 grupo 5): sin sesión, con CSRF, PostgreSQL
 * real. "Ahora" = viernes 2026-11-20 09:00 BA; los lunes son huecos válidos para la anticipación pública. Los DNI
 * nuevos usan el prefijo {@code 38} para que la limpieza de {@link AbstractTurnoIT} los borre; cada test usa DNI,
 * emails e IP propios porque el limitador vive en el contexto compartido.
 */
@ExtendWith(OutputCaptureExtension.class)
class SolicitudPublicaApiIT extends AbstractTurnoIT {

    private static final String URL = "/api/v1/publico/solicitudes-turno";

    @Autowired
    private ObjectMapper json;
    @Autowired
    private ExpiracionTurnosService expiracion;

    private static RequestPostProcessor desde(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String cuerpo(Long prestacion, Instant inicio, String dni, String email, String sitioWeb) {
        return """
                {"prestacionId":%d,"inicio":"%s","nombre":"Zoilo","apellido":"Quispe","dni":"%s","email":"%s",
                 "telefono":"+54 11 4321-8765","sitioWeb":%s}"""
                .formatted(prestacion, inicio, dni, email, sitioWeb == null ? "null" : "\"" + sitioWeb + "\"");
    }

    private MockHttpServletResponse enviar(Cliente cliente, String ip, String cuerpo) throws Exception {
        return mockMvc.perform(cliente.firmar(post(URL)).with(desde(ip)).contentType("application/json")
                .content(cuerpo)).andReturn().getResponse();
    }

    private MockHttpServletResponse enviar(String ip, String cuerpo) throws Exception {
        return enviar(clienteAnonimo(), ip, cuerpo);
    }

    private int turnosDe(Long prestacion) {
        return jdbc.queryForObject("SELECT count(*) FROM turno WHERE prestacion_id = ?", Integer.class, prestacion);
    }

    private int pacientesConDni(String dni) {
        return jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = ?", Integer.class, dni);
    }

    // --- 5.1 ---

    @Test
    void solicitudValidaDa202ConElCuerpoGenericoYCreaElTurnoPendiente() throws Exception {
        Long prestacion = crearPrestacion(30);

        MockHttpServletResponse r = enviar(ipNueva(),
                cuerpo(prestacion, lunes("10:00"), "38.991.001", "api1@ejemplo.test", ""));

        assertThat(r.getStatus()).isEqualTo(202);
        JsonNode cuerpo = json.readTree(r.getContentAsString());
        assertThat(cuerpo.fieldNames()).toIterable().containsExactly("mensaje");
        assertThat(cuerpo.get("mensaje").asText())
                .isEqualTo("Si los datos son correctos, vas a recibir un email para confirmar el turno.");
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE prestacion_id = ?", String.class, prestacion))
                .isEqualTo("PENDIENTE_CONFIRMACION");
        assertThat(pacientesConDni("38991001")).isEqualTo(1);
    }

    @Test
    void datosInvalidosDan400ConElCampoYSinEfectos() throws Exception {
        Long prestacion = crearPrestacion(30);
        String ip = ipNueva();

        MockHttpServletResponse dniMalo = enviar(ip,
                cuerpo(prestacion, lunes("10:00"), "12AB", "api2@ejemplo.test", null));
        MockHttpServletResponse sinEmail = enviar(ip, """
                {"prestacionId":%d,"inicio":"%s","nombre":"Zoilo","apellido":"Quispe","dni":"38991002",
                 "telefono":"+54 11 4321-8765"}""".formatted(prestacion, lunes("10:00")));
        MockHttpServletResponse emailMalo = enviar(ip,
                cuerpo(prestacion, lunes("10:00"), "38991002", "no-es-un-email", null));
        MockHttpServletResponse senueloLargo = enviar(ip,
                cuerpo(prestacion, lunes("10:00"), "38991002", "api2@ejemplo.test", "x".repeat(201)));

        assertThat(dniMalo.getStatus()).isEqualTo(400);
        assertThat(json.readTree(dniMalo.getContentAsString()).get("errors").get(0).get("field").asText())
                .isEqualTo("dni");
        assertThat(sinEmail.getStatus()).isEqualTo(400);
        assertThat(sinEmail.getContentAsString()).contains("\"field\":\"email\"");
        assertThat(emailMalo.getStatus()).isEqualTo(400);
        assertThat(senueloLargo.getStatus()).isEqualTo(400);
        assertThat(turnosDe(prestacion)).isZero();
        assertThat(pacientesConDni("38991002")).isZero();
    }

    @Test
    void huecoOcupadoDa409ConAlternativasPublicas() throws Exception {
        Long ocupante = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(ocupante, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        MockHttpServletResponse r = enviar(ipNueva(),
                cuerpo(prestacion, lunes("10:00"), "38991003", "api3@ejemplo.test", null));

        assertThat(r.getStatus()).isEqualTo(409);
        JsonNode problema = json.readTree(r.getContentAsString());
        assertThat(problema.get("type").asText()).endsWith("/hueco-no-disponible");
        assertThat(problema.get("alternativas")).hasSize(3);
        assertThat(problema.get("alternativas").get(0).get("inicio").asText())
                .isEqualTo(lunes("10:30").toString());
        assertThat(pacientesConDni("38991003")).isZero();
        assertThat(turnosDe(prestacion)).isEqualTo(1);
    }

    @Test
    void horarioInvalidoDa422ConErrorEnInicioYPrestacionInexistenteDa404() throws Exception {
        Long prestacion = crearPrestacion(30);
        String ip = ipNueva();

        MockHttpServletResponse domingo = enviar(ip,
                cuerpo(prestacion, dia(6, "10:00"), "38991004", "api4@ejemplo.test", null));
        MockHttpServletResponse inexistente = enviar(ip,
                cuerpo(999_999_999L, lunes("10:00"), "38991004", "api4@ejemplo.test", null));

        assertThat(domingo.getStatus()).isEqualTo(422);
        assertThat(domingo.getContentAsString()).contains("\"field\":\"inicio\"");
        assertThat(inexistente.getStatus()).isEqualTo(404);
        assertThat(pacientesConDni("38991004")).isZero();
    }

    @Test
    void sinCabeceraCsrfDa403YNoCreaNada() throws Exception {
        Long prestacion = crearPrestacion(30);
        Cliente cliente = clienteAnonimo();

        MockHttpServletResponse r = mockMvc.perform(post(URL).session(cliente.session()).with(desde(ipNueva()))
                .contentType("application/json")
                .content(cuerpo(prestacion, lunes("10:00"), "38991005", "api5@ejemplo.test", null)))
                .andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(turnosDe(prestacion)).isZero();
    }

    @Test
    void unGetPreviaALaPaginaEntregaLaCookieCsrfYElPostPosteriorFunciona() throws Exception {
        Long prestacion = crearPrestacion(30);
        MockHttpServletResponse previa = mockMvc.perform(get("/api/v1/publico/prestaciones")).andReturn()
                .getResponse();
        jakarta.servlet.http.Cookie xsrf = previa.getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        MockHttpServletResponse r = mockMvc.perform(post(URL).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .with(desde(ipNueva())).contentType("application/json")
                        .content(cuerpo(prestacion, lunes("10:00"), "38991006", "api6@ejemplo.test", null)))
                .andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(202);
    }

    // --- 5.2 ---

    @Test
    void elCuerpoEsIdenticoByteAByteParaDniNuevoExistenteDeBajaYSenuelo() throws Exception {
        Long existente = crearPaciente();
        String dniExistente = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, existente);
        Long deBaja = crearPacienteDeBaja();
        String dniBaja = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, deBaja);
        Long prestacion = crearPrestacion(30);

        MockHttpServletResponse nuevo = enviar(ipNueva(),
                cuerpo(prestacion, lunes("10:00"), "38991010", "api10@ejemplo.test", null));
        MockHttpServletResponse conDni = enviar(ipNueva(),
                cuerpo(prestacion, lunes("10:30"), dniExistente, "api11@ejemplo.test", null));
        MockHttpServletResponse baja = enviar(ipNueva(),
                cuerpo(prestacion, lunes("11:00"), dniBaja, "api12@ejemplo.test", null));
        MockHttpServletResponse senuelo = enviar(ipNueva(),
                cuerpo(prestacion, lunes("11:30"), "38991013", "api13@ejemplo.test", "http://spam.example"));

        List<MockHttpServletResponse> todas = List.of(nuevo, conDni, baja, senuelo);
        assertThat(todas).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(202));
        byte[] referencia = nuevo.getContentAsByteArray();
        assertThat(todas).allSatisfy(r -> assertThat(r.getContentAsByteArray()).isEqualTo(referencia));
        assertThat(nuevo.getContentAsString()).doesNotContain("\"id\"").doesNotContain("turnoId")
                .doesNotContain("pacienteId");
        // Efectos distintos detrás de la misma respuesta: nuevo y existente crean turno; baja y señuelo no.
        assertThat(turnosDe(prestacion)).isEqualTo(2);
        assertThat(pacientesConDni("38991013")).isZero();
    }

    // --- 5.3 ---

    @Test
    void laSextaSolicitudDeLaMismaIpDa429ConRetryAfterYNoCreaTurno() throws Exception {
        Long prestacion = crearPrestacion(30);
        String ip = ipNueva();
        String[] horas = {"09:00", "09:30", "10:00", "10:30", "11:00", "11:30"};
        List<MockHttpServletResponse> respuestas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            respuestas.add(enviar(ip, cuerpo(prestacion, lunes(horas[i]), "3899102" + i,
                    "iplimite" + i + "@ejemplo.test", null)));
        }

        assertThat(respuestas.subList(0, 5)).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(202));
        MockHttpServletResponse sexta = respuestas.get(5);
        assertThat(sexta.getStatus()).isEqualTo(429);
        assertThat(sexta.getHeader("Retry-After")).isEqualTo("3600");
        assertThat(sexta.getContentAsString()).contains("/demasiadas-solicitudes");
        assertThat(turnosDe(prestacion)).isEqualTo(5);
        assertThat(pacientesConDni("38991025")).isZero();
    }

    @Test
    void laSextaSolicitudDelMismoDniDesdeIpDistintasDa429ConElMismoCuerpoQueElLimiteDeIp() throws Exception {
        Long deBaja = crearPacienteDeBaja();
        String dniBaja = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, deBaja);
        Long prestacion = crearPrestacion(30);
        List<MockHttpServletResponse> porDni = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            // Un paciente de baja no crea turnos: aísla el límite por DNI del máximo de pendientes.
            porDni.add(enviar(ipNueva(), cuerpo(prestacion, lunes("10:00"), dniBaja,
                    "dnilimite" + i + "@ejemplo.test", null)));
        }
        String ipLlena = ipNueva();
        MockHttpServletResponse porIp = null;
        for (int i = 0; i < 6; i++) {
            porIp = enviar(ipLlena, cuerpo(prestacion, lunes("10:00"), "3899103" + i,
                    "iplleno" + i + "@ejemplo.test", "senuelo"));
        }

        assertThat(porDni.subList(0, 5)).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(202));
        assertThat(porDni.get(5).getStatus()).isEqualTo(429);
        assertThat(porIp.getStatus()).isEqualTo(429);
        assertThat(porDni.get(5).getContentAsString()).isEqualTo(porIp.getContentAsString());
        assertThat(porDni.get(5).getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void laTerceraPendienteDelMismoDniDa429ConElMismoCuerpo() throws Exception {
        Long prestacion = crearPrestacion(30);
        List<MockHttpServletResponse> r = new ArrayList<>();
        String[] horas = {"09:00", "09:30", "10:00"};
        for (int i = 0; i < 3; i++) {
            r.add(enviar(ipNueva(), cuerpo(prestacion, lunes(horas[i]), "38991040", "pend" + i + "@ejemplo.test",
                    null)));
        }

        assertThat(r.get(0).getStatus()).isEqualTo(202);
        assertThat(r.get(1).getStatus()).isEqualTo(202);
        assertThat(r.get(2).getStatus()).isEqualTo(429);
        assertThat(r.get(2).getHeader("Retry-After")).isEqualTo("1800");
        assertThat(turnosDe(prestacion)).isEqualTo(2);
    }

    // --- 5.4 ---

    @Test
    void dosSolicitudesSimultaneasDelMismoHorarioDejanUnSolo202YUnSolo409() throws Exception {
        Long prestacion = crearPrestacion(30);
        String[] horas = {"09:00", "09:30", "10:00", "10:30", "11:00"};
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int ronda = 0; ronda < horas.length; ronda++) {
                Instant inicio = lunes(horas[ronda]);
                Cliente a = clienteAnonimo();
                Cliente b = clienteAnonimo();
                String ipA = ipNueva();
                String ipB = ipNueva();
                String cuerpoA = cuerpo(prestacion, inicio, "3899105" + ronda, "carrera-a" + ronda + "@ejemplo.test",
                        null);
                String cuerpoB = cuerpo(prestacion, inicio, "3899106" + ronda, "carrera-b" + ronda + "@ejemplo.test",
                        null);
                CountDownLatch salida = new CountDownLatch(1);
                Future<Integer> fa = pool.submit(() -> {
                    salida.await();
                    return enviar(a, ipA, cuerpoA).getStatus();
                });
                Future<Integer> fb = pool.submit(() -> {
                    salida.await();
                    return enviar(b, ipB, cuerpoB).getStatus();
                });
                salida.countDown();
                List<Integer> estados = List.of(fa.get(30, TimeUnit.SECONDS), fb.get(30, TimeUnit.SECONDS));

                assertThat(estados).as("ronda %d", ronda).containsExactlyInAnyOrder(202, 409);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE prestacion_id = ? AND inicio = "
                        + "?::timestamptz", Integer.class, prestacion, inicio.toString())).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni LIKE '3899105_' OR dni LIKE "
                + "'3899106_'", Integer.class)).as("el perdedor no deja paciente").isEqualTo(horas.length);
    }

    // --- 5.5 ---

    @Test
    void elHuecoReaparecePorLaExpiracionTrasElVencimiento() throws Exception {
        Long prestacion = crearPrestacion(30);
        assertThat(enviar(ipNueva(), cuerpo(prestacion, lunes("10:00"), "38991070", "exp@ejemplo.test", null))
                .getStatus()).isEqualTo(202);
        String disponibilidad = "/api/v1/publico/disponibilidad?prestacionId=" + prestacion
                + "&desde=2026-11-23&hasta=2026-11-23";
        String antes = mockMvc.perform(get(disponibilidad)).andReturn().getResponse().getContentAsString();

        reloj.advance(Duration.ofMinutes(30));
        int expiradas = expiracion.expirarVencidas();
        String despues = mockMvc.perform(get(disponibilidad)).andReturn().getResponse().getContentAsString();

        assertThat(antes).doesNotContain("\"inicio\":\"" + lunes("10:00") + "\"");
        assertThat(expiradas).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE prestacion_id = ?", String.class, prestacion))
                .isEqualTo("EXPIRADO");
        assertThat(despues).contains("\"inicio\":\"" + lunes("10:00") + "\"");
    }

    // --- 5.6 ---

    @Test
    void losLogsNoContienenDatosPersonalesEnSolicitudExitosaLimitadaNiConSenuelo(CapturedOutput salida)
            throws Exception {
        Long prestacion = crearPrestacion(30);
        String ipLimitada = ipNueva();
        String[] horas = {"09:00", "09:30", "10:00", "10:30", "11:00", "11:30"};
        for (int i = 0; i < 6; i++) { // la sexta queda limitada
            enviar(ipLimitada, cuerpo(prestacion, lunes(horas[i]), "3899108" + i, "zoilo.logs" + i + "@ejemplo.test",
                    null));
        }
        enviar(ipNueva(), cuerpo(prestacion, lunes("12:00"), "38991090", "zoilo.senuelo@ejemplo.test",
                "http://spam.example"));

        // Solo las líneas del log de la aplicación: MockMvc imprime cada pedido por consola (artefacto del test).
        String logs = salida.getAll().lines().filter(l -> l.matches("^20\\d\\d-\\d\\d-\\d\\dT.*"))
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
        assertThat(logs).doesNotContain("3899108").doesNotContain("38.99").doesNotContain("zoilo.logs")
                .doesNotContain("zoilo.senuelo").doesNotContain("4321-8765").doesNotContain("Zoilo")
                .doesNotContain("Quispe").doesNotContain("spam.example").doesNotContain(ipLimitada);
    }
}
