package ar.edu.odontogestion.api.notificacion;

import ar.edu.odontogestion.support.TurnosFicticios;
import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.application.notificacion.CanalEnMemoria;
import ar.edu.odontogestion.application.notificacion.NotificationService;
import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registro de envíos con sesión, CSRF y PostgreSQL reales. "Ahora" es el reloj fijo de los ITs
 * (2026-11-20T12:00Z). Ids de referencia ficticios >= 900000; cada caso borra sus filas.
 */
@TurnosFicticios
class NotificacionApiIT extends AbstractWebIntegrationTest {

    private static final String URL = "/api/v1/notificaciones";
    private static final String DATOS = "{\"consultorio\": \"Consultorio Ficticio Sonrisa\", "
            + "\"inicio\": \"2026-11-24T13:00:00Z\", \"prestacion\": \"Limpieza ficticia\"}";

    @Autowired
    private NotificationService service;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private CanalEnMemoria canal;

    private Cliente c;

    @BeforeEach
    void iniciar() throws Exception {
        limpiar();
        c = iniciarSesionSeed();
    }

    @AfterEach
    void limpiar() {
        canal.reiniciar();
        jdbc.update("DELETE FROM notificacion WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
        jdbc.update("DELETE FROM token_acceso WHERE coalesce(turno_id, aviso_id, entrada_lista_espera_id) >= 900000");
    }

    /** Inserta una notificación con estado y creación controlados; devuelve su id. */
    private Long insertar(long turno, String tipo, String estado, Duration antiguedad) {
        Timestamp creada = Timestamp.from(AHORA.minus(antiguedad));
        return jdbc.queryForObject("INSERT INTO notificacion (consultorio_id, tipo, destinatario, turno_id, datos, "
                + "estado, intentos, programada_para, creado_en, actualizado_en) "
                + "SELECT min(id), ?, 'paciente@ejemplo.test', ?, ?::jsonb, ?, "
                + "CASE WHEN ? = 'FALLIDA' THEN 3 ELSE 0 END, ?, ?, ? FROM consultorio RETURNING id",
                Long.class, tipo, turno, DATOS, estado, estado, creada, creada, creada);
    }

    @Test
    void listadoPaginadoOrdenadoPorCreacionDescendente() throws Exception {
        for (int i = 0; i < 25; i++) {
            insertar(900600 + i, "CONFIRMACION_TURNO", "ENVIADA", Duration.ofMinutes(25 - i));
        }

        mockMvc.perform(c.firmar(get(URL).param("page", "0").param("size", "20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(20))
                .andExpect(jsonPath("$.totalElementos").value(25))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.contenido[0].referencia.id").value(900624))
                .andExpect(jsonPath("$.contenido[19].referencia.id").value(900605));

        mockMvc.perform(c.firmar(get(URL).param("page", "1").param("size", "20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(5))
                .andExpect(jsonPath("$.contenido[4].referencia.id").value(900600));
    }

    @Test
    void filtrarFallidasDeRecordatorio() throws Exception {
        insertar(900700, "RECORDATORIO", "FALLIDA", Duration.ofMinutes(5));
        insertar(900701, "RECORDATORIO", "ENVIADA", Duration.ofMinutes(4));
        insertar(900702, "CANCELACION", "FALLIDA", Duration.ofMinutes(3));
        insertar(900703, "RECORDATORIO", "FALLIDA", Duration.ofMinutes(2));

        mockMvc.perform(c.firmar(get(URL).param("estado", "FALLIDA").param("tipo", "RECORDATORIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].tipo").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("RECORDATORIO"))))
                .andExpect(jsonPath("$.contenido[*].estado").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("FALLIDA"))));

        mockMvc.perform(c.firmar(get(URL).param("estado", "FALLIDA")))
                .andExpect(jsonPath("$.totalElementos").value(3));
    }

    // ---- 9.4 reintento manual ----

    private Map<String, Object> fila(Long id) {
        return jdbc.queryForMap("SELECT estado, intentos, reintentos_manuales, error, programada_para, actualizado_en "
                + "FROM notificacion WHERE id = ?", id);
    }

    private org.springframework.test.web.servlet.ResultActions reintentar(Object id) throws Exception {
        return mockMvc.perform(c.firmar(post(URL + "/" + id + "/reintentar")));
    }

    @Test
    void reintentoDeUnaFallidaLaVuelveAPendienteProgramadaParaAhora() throws Exception {
        Long id = insertar(900900, "CONFIRMACION_TURNO", "FALLIDA", Duration.ofHours(1));

        reintentar(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.intentos").value(0))
                .andExpect(jsonPath("$.reintentosManuales").value(1))
                .andExpect(jsonPath("$.programadaPara").value("2026-11-20T12:00:00Z"));

        Map<String, Object> despues = fila(id);
        assertThat(despues).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 0)
                .containsEntry("reintentos_manuales", 1);
        assertThat(((Timestamp) despues.get("programada_para")).toInstant()).isEqualTo(AHORA);
    }

    @Test
    void elReintentoSeEnviaEnElSiguienteProcesamiento() throws Exception {
        Long id = insertar(900901, "CONFIRMACION_TURNO", "FALLIDA", Duration.ofHours(1));

        reintentar(id).andExpect(status().isOk());
        service.procesarPendientes();

        assertThat(fila(id)).containsEntry("estado", "ENVIADA").containsEntry("intentos", 1);
        assertThat(canal.enviados()).hasSize(1);
    }

    @Test
    void reintentoDeUnaEnviadaDevuelve409YNoLaCambia() throws Exception {
        Long id = insertar(900902, "CONFIRMACION_TURNO", "ENVIADA", Duration.ofHours(1));
        Map<String, Object> antes = fila(id);

        reintentar(id).andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));

        assertThat(fila(id)).isEqualTo(antes);
    }

    @Test
    void reintentoConTresReintentosManualesPreviosDevuelve422YNoLaCambia() throws Exception {
        Long id = insertar(900903, "CONFIRMACION_TURNO", "FALLIDA", Duration.ofHours(1));
        jdbc.update("UPDATE notificacion SET reintentos_manuales = 3 WHERE id = ?", id);
        Map<String, Object> antes = fila(id);

        reintentar(id).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value(422));

        assertThat(fila(id)).isEqualTo(antes);
    }

    @Test
    void reintentoConEnlaceVencidoDevuelve422YNoLaCambia() throws Exception {
        Long id = insertar(900904, "CONFIRMAR_SOLICITUD", "FALLIDA", Duration.ofHours(1));
        jdbc.update("UPDATE notificacion SET enlaces = ?::jsonb WHERE id = ?",
                "[{\"proposito\": \"CONFIRMAR_SOLICITUD\", \"venceEn\": \"2026-11-20T11:30:00Z\"}]", id);
        Map<String, Object> antes = fila(id);

        reintentar(id).andExpect(status().isUnprocessableEntity());

        assertThat(fila(id)).isEqualTo(antes);
    }

    @Test
    void reintentoDeNotificacionInexistenteDevuelve404ProblemDetail() throws Exception {
        reintentar(999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void laRespuestaNoIncluyeElCuerpoNiLosTokensDeUnRecordatorioEnviado() throws Exception {
        tx.executeWithoutResult(s -> service.encolar(TipoNotificacion.RECORDATORIO, "paciente@ejemplo.test",
                new ReferenciaToken.Turno(900800), Map.of("consultorio", "Consultorio Ficticio Sonrisa",
                        "inicio", "2026-11-24T13:00:00Z", "prestacion", "Limpieza ficticia"), List.of(
                        new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, AHORA.plus(Duration.ofHours(24))),
                        new EnlaceProgramado(PropositoToken.CANCELAR_TURNO, AHORA.plus(Duration.ofHours(24))))));
        service.procesarPendientes();
        String cuerpo = canal.enviados().getFirst().cuerpo();
        Matcher tokens = Pattern.compile("/acciones/([A-Za-z0-9_-]{43})").matcher(cuerpo);

        String json = mockMvc.perform(c.firmar(get(URL).param("tipo", "RECORDATORIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].estado").value("ENVIADA"))
                .andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContain("Te recordamos").doesNotContain("Limpieza ficticia")
                .doesNotContain("/acciones/");
        int encontrados = 0;
        while (tokens.find()) {
            assertThat(json).doesNotContain(tokens.group(1));
            encontrados++;
        }
        assertThat(encontrados).isEqualTo(2);
    }
}
