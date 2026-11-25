package ar.edu.odontogestion.application.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.edu.odontogestion.application.recordatorio.RecordatorioService;
import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import ar.edu.odontogestion.support.CanalEnMemoriaTestConfig;
import ar.edu.odontogestion.support.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Seed de demo (C-12 grupo 2, spec {@code demo-environment}). Usa una base propia, recién migrada, para reproducir el
 * primer arranque de la demo sin mezclarse con la base compartida del resto de los IT. El perfil {@code demo} activa
 * el seed ({@code app.seed-demo.enabled=true} en {@code application-demo.yml}); el reloj queda fijo en un miércoles
 * (2026-11-25 12:00 en Buenos Aires) desde antes del arranque, así el {@code DemoSeeder} siembra la semana del 23/11.
 */
@SpringBootTest(properties = {
        "app.bootstrap.odontologo-password=" + AbstractWebIntegrationTest.PASSWORD_SEED,
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import({CanalEnMemoriaTestConfig.class, DemoSeedIT.RelojMiercoles.class})
@ExtendWith(OutputCaptureExtension.class)
class DemoSeedIT {

    static final Instant MIERCOLES = Instant.parse("2026-11-25T15:00:00Z");
    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant LUNES = Instant.parse("2026-11-23T03:00:00Z");
    private static final Instant LUNES_SIGUIENTE = LUNES.plus(Duration.ofDays(7));

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
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
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private DemoSeedService seed;
    @Autowired
    private RecordatorioService recordatorios;
    @Autowired
    private MutableClock reloj;

    @AfterEach
    void restaurarReloj() {
        reloj.set(MIERCOLES);
        jdbc.update("DELETE FROM bloqueo_agenda WHERE motivo = 'IT-Demo'");
    }

    private int contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int turnosDeLaSemana() {
        return contar("SELECT count(*) FROM turno WHERE inicio >= ?::timestamptz AND inicio < ?::timestamptz",
                LUNES.toString(), LUNES_SIGUIENTE.toString());
    }

    // --- Primer arranque ---------------------------------------------------------------------------------------

    @Test
    void elPrimerArranqueCreaDiezPacientesYTurnosDeLaSemanaEnLosCuatroEstados() {
        assertThat(contar("SELECT count(*) FROM paciente")).isEqualTo(10);
        assertThat(turnosDeLaSemana()).isEqualTo(contar("SELECT count(*) FROM turno")).isGreaterThanOrEqualTo(12);
        List<String> estados = jdbc.queryForList("SELECT DISTINCT estado FROM turno", String.class);
        assertThat(estados).containsExactlyInAnyOrder("RESERVADO", "CONFIRMADO", "ATENDIDO", "AUSENTE");
    }

    @Test
    void hayTurnosDeHoyYAlMenosUnoConfirmado() {
        Instant hoy = ZonedDateTime.ofInstant(MIERCOLES, BA).toLocalDate().atStartOfDay(BA).toInstant();
        String deHoy = "SELECT count(*) FROM turno WHERE inicio >= ?::timestamptz AND inicio < ?::timestamptz";
        assertThat(contar(deHoy, hoy.toString(), hoy.plus(Duration.ofDays(1)).toString())).isGreaterThanOrEqualTo(2);
        assertThat(contar(deHoy + " AND estado = 'CONFIRMADO'", hoy.toString(),
                hoy.plus(Duration.ofDays(1)).toString())).isGreaterThanOrEqualTo(1);
    }

    @Test
    void tresAusenciasRepartidasEnDosPacientesConElContadorCoherenteEnLaApi() throws Exception {
        assertThat(contar("SELECT count(*) FROM turno WHERE estado = 'AUSENTE'")).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT count(*) FROM turno WHERE estado = 'AUSENTE' GROUP BY paciente_id "
                + "ORDER BY 1", Integer.class)).containsExactly(1, 2);

        MockHttpSession sesion = iniciarSesion();
        for (Map<String, Object> fila : jdbc.queryForList("SELECT p.id, (SELECT count(*) FROM turno t "
                + "WHERE t.paciente_id = p.id AND t.estado = 'AUSENTE') AS ausentes FROM paciente p")) {
            String cuerpo = mockMvc.perform(get("/api/v1/pacientes/" + fila.get("id") + "/ausencias").session(sesion))
                    .andReturn().getResponse().getContentAsString();
            JsonNode respuesta = json.readTree(cuerpo);
            assertThat(respuesta.get("cantidad").asLong()).as("paciente %s", fila.get("id"))
                    .isEqualTo(((Number) fila.get("ausentes")).longValue());
            assertThat(respuesta.get("ausencias")).hasSize(((Number) fila.get("ausentes")).intValue());
        }
    }

    @Test
    void losTurnosCaenDentroDelHorarioUsanPrestacionesActivasYNoSeSuperponen() {
        for (Map<String, Object> t : jdbc.queryForList("SELECT t.inicio, t.fin, p.activa FROM turno t "
                + "JOIN prestacion p ON p.id = t.prestacion_id")) {
            ZonedDateTime inicio = ((java.sql.Timestamp) t.get("inicio")).toInstant().atZone(BA);
            ZonedDateTime fin = ((java.sql.Timestamp) t.get("fin")).toInstant().atZone(BA);
            assertThat(t.get("activa")).isEqualTo(true);
            assertThat(fin.toLocalDate()).isEqualTo(inicio.toLocalDate());
            int dentro = contar("SELECT count(*) FROM horario_trabajo WHERE dia_semana = ? AND hora_desde <= ?::time "
                    + "AND hora_hasta >= ?::time", inicio.getDayOfWeek().getValue(),
                    inicio.toLocalTime().toString(), fin.toLocalTime().toString());
            assertThat(dentro).as("turno %s dentro de un horario de trabajo", inicio).isEqualTo(1);
        }
        assertThat(contar("SELECT count(*) FROM turno a JOIN turno b ON a.id < b.id "
                + "AND tstzrange(a.inicio, a.fin, '[)') && tstzrange(b.inicio, b.fin, '[)')")).isZero();
    }

    @Test
    void elSeedNoEncolaNiEnviaNotificaciones() {
        assertThat(contar("SELECT count(*) FROM notificacion")).isZero();
        assertThat(contar("SELECT count(*) FROM token_acceso")).isZero();
    }

    @Test
    void losDatosSonFicticios() {
        assertThat(jdbc.queryForList("SELECT email::text FROM paciente", String.class))
                .hasSize(10).allSatisfy(email -> assertThat(email).endsWith(".example"));
        assertThat(jdbc.queryForList("SELECT dni FROM paciente ORDER BY dni", String.class))
                .first().isEqualTo("90000001");
        assertThat(jdbc.queryForList("SELECT dni FROM paciente ORDER BY dni", String.class))
                .last().isEqualTo("90000010");
    }

    // --- Triangulación ----------------------------------------------------------------------------------------

    @Test
    void elSeedEsIdempotente() {
        int pacientes = contar("SELECT count(*) FROM paciente");
        int turnos = contar("SELECT count(*) FROM turno");

        ResultadoSeedDemo resultado = seed.sembrar();

        assertThat(resultado.pacientes()).isZero();
        assertThat(resultado.turnos()).isZero();
        assertThat(contar("SELECT count(*) FROM paciente")).isEqualTo(pacientes);
        assertThat(contar("SELECT count(*) FROM turno")).isEqualTo(turnos);
    }

    @Test
    void unBloqueoQuePisaUnaFranjaHaceQueEseTurnoSeOmitaSinFallar(CapturedOutput salida) {
        Long consultorioId = jdbc.queryForObject("SELECT min(id) FROM consultorio", Long.class);
        Instant juevesNueve = ZonedDateTime.of(2026, 11, 26, 9, 0, 0, 0, BA).toInstant();
        String nueveYMedia = juevesNueve.plus(Duration.ofMinutes(30)).toString();
        int previos = contar("SELECT count(*) FROM turno");
        int pisados = contar("SELECT count(*) FROM turno WHERE inicio < ?::timestamptz AND fin > ?::timestamptz",
                nueveYMedia, juevesNueve.toString());
        assertThat(pisados).as("el seed tiene un turno el jueves a las 09:00").isEqualTo(1);
        borrarSeed();
        jdbc.update("INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta, motivo) VALUES (?, ?::timestamptz, "
                + "?::timestamptz, 'IT-Demo')", consultorioId, juevesNueve.toString(), nueveYMedia);

        try {
            ResultadoSeedDemo resultado = seed.sembrar();

            assertThat(resultado.pacientes()).isEqualTo(10);
            assertThat(resultado.omitidos()).isEqualTo(1);
            assertThat(contar("SELECT count(*) FROM turno")).isEqualTo(previos - 1);
            assertThat(contar("SELECT count(*) FROM turno WHERE inicio < ?::timestamptz AND fin > ?::timestamptz",
                    nueveYMedia, juevesNueve.toString())).isZero();
            assertThat(salida.getOut()).contains("Seed de demo: se omite un turno")
                    .doesNotContain("@demo.example").doesNotContain("9000000");
        } finally {
            // Deja la base como la dejó el arranque, para los demás tests de la clase.
            jdbc.update("DELETE FROM bloqueo_agenda WHERE motivo = 'IT-Demo'");
            borrarSeed();
            seed.sembrar();
        }
    }

    /** C-12 grupo 5.2: los turnos sembrados nunca disparan recordatorios a las direcciones {@code .example}. */
    @Test
    void ningunTurnoSembradoDisparaRecordatoriosDuranteLaSemana() {
        for (Instant t = MIERCOLES; t.isBefore(LUNES_SIGUIENTE.plus(Duration.ofDays(1)));
                t = t.plus(Duration.ofMinutes(30))) {
            reloj.set(t);
            recordatorios.encolarRecordatorios();
        }

        assertThat(contar("SELECT count(*) FROM notificacion")).isZero();
    }

    @Test
    void losReservadosEmpiezanDentroDeLasHorasDelRecordatorioYLosLejanosSonConfirmados() {
        int horas = contar("SELECT recordatorio_horas_antes FROM consultorio");
        Instant limite = MIERCOLES.plus(Duration.ofHours(horas));
        assertThat(contar("SELECT count(*) FROM turno WHERE estado = 'RESERVADO' AND inicio >= ?::timestamptz",
                limite.toString())).isZero();
        assertThat(contar("SELECT count(*) FROM turno WHERE estado = 'CONFIRMADO' AND inicio >= ?::timestamptz",
                limite.toString())).isPositive();
    }

    // --- Apoyo -----------------------------------------------------------------------------------------------

    private void borrarSeed() {
        jdbc.update("DELETE FROM turno");
        jdbc.update("DELETE FROM paciente");
    }

    private MockHttpSession iniciarSesion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        Cookie xsrf = mockMvc.perform(get("/api/v1/auth/me").session(sesion)).andReturn().getResponse()
                .getCookie("XSRF-TOKEN");
        MockHttpServletResponse login = mockMvc.perform(post("/api/v1/auth/login").session(sesion).cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).contentType("application/json")
                .content("{\"email\":\"" + AbstractWebIntegrationTest.EMAIL_SEED + "\",\"password\":\""
                        + AbstractWebIntegrationTest.PASSWORD_SEED + "\"}"))
                .andReturn().getResponse();
        assertThat(login.getStatus()).isEqualTo(200);
        return sesion;
    }
}
