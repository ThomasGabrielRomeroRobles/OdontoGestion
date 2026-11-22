package ar.edu.odontogestion.api.agenda;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * API de bloqueos con sesión, CSRF y PostgreSQL reales. "Ahora" es el reloj fijo de los ITs (2026-11-20T12:00Z).
 * El seed no trae bloqueos: cada caso borra los que crea.
 */
class BloqueoApiIT extends AbstractWebIntegrationTest {

    private static final String URL = "/api/v1/bloqueos";

    private Cliente c;

    @BeforeEach
    void iniciar() throws Exception {
        limpiar();
        c = iniciarSesionSeed();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM bloqueo_agenda");
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return mockMvc.perform(c.firmar(post(URL)).contentType("application/json").content(cuerpo));
    }

    private static String bloqueo(String desde, String hasta, String motivoJson) {
        return "{\"desde\":\"" + desde + "\",\"hasta\":\"" + hasta + "\""
                + (motivoJson == null ? "" : ",\"motivo\":" + motivoJson) + "}";
    }

    private Long insertar(String desde, String hasta, String motivo) {
        return jdbc.queryForObject("INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta, motivo) "
                + "SELECT min(id), ?::timestamptz, ?::timestamptz, ? FROM consultorio RETURNING id",
                Long.class, desde, hasta, motivo);
    }

    /** Ayer, la semana próxima y el año próximo respecto del reloj fijo. */
    private void tresBloqueos() {
        insertar("2026-11-19T03:00:00Z", "2026-11-19T15:00:00Z", "Ayer");
        insertar("2026-11-27T03:00:00Z", "2026-11-28T03:00:00Z", "Semana proxima");
        insertar("2027-11-20T03:00:00Z", "2027-11-21T03:00:00Z", "Anio proximo");
    }

    @Test
    void altaValidaEnUtcConMotivo() throws Exception {
        crear(bloqueo("2026-12-24T00:00:00-03:00", "2026-12-26T03:00:00Z", "\"Fiestas\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.desde").value("2026-12-24T03:00:00Z"))
                .andExpect(jsonPath("$.hasta").value("2026-12-26T03:00:00Z"))
                .andExpect(jsonPath("$.motivo").value("Fiestas"));
    }

    @Test
    void sinMotivoONullDevuelveTextoVacio() throws Exception {
        crear(bloqueo("2026-12-24T03:00:00Z", "2026-12-26T03:00:00Z", null))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.motivo").value(""));
        crear(bloqueo("2027-01-02T03:00:00Z", "2027-01-03T03:00:00Z", "null"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.motivo").value(""));
    }

    @Test
    void rangoInvertidoYSinZonaDan422PorCampo() throws Exception {
        crear(bloqueo("2026-12-26T03:00:00Z", "2026-12-24T03:00:00Z", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='hasta')]").exists());
        crear(bloqueo("2026-12-24T00:00:00", "2026-12-26T03:00:00Z", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='desde')]").exists());
    }

    @Test
    void fechaMalFormadaDa422EnDesdeNoUn400() throws Exception {
        crear(bloqueo("no-es-fecha", "2026-12-26T03:00:00Z", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='desde')]").exists());
    }

    @Test
    void campoAusenteDa400() throws Exception {
        crear("{\"desde\":\"2026-12-24T03:00:00Z\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='hasta')]").exists());
    }

    @Test
    void sinFiltrosVigentesYFuturos() throws Exception {
        tresBloqueos();
        mockMvc.perform(get(URL).session(c.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].motivo").value("Semana proxima"))
                .andExpect(jsonPath("$[1].motivo").value("Anio proximo"));
    }

    @Test
    void filtroPorRangoAmbos() throws Exception {
        tresBloqueos();
        mockMvc.perform(get(URL).session(c.session())
                        .param("desde", "2026-11-01T03:00:00Z").param("hasta", "2026-12-01T03:00:00Z"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].motivo").value("Ayer"))
                .andExpect(jsonPath("$[1].motivo").value("Semana proxima"));
    }

    @Test
    void soloDesdeIncluyePasadosSinLimiteSuperior() throws Exception {
        tresBloqueos();
        mockMvc.perform(get(URL).session(c.session()).param("desde", "2026-11-13T00:00:00Z"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void soloHastaArrancaEnAhora() throws Exception {
        tresBloqueos();
        mockMvc.perform(get(URL).session(c.session()).param("hasta", "2026-12-20T00:00:00Z"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].motivo").value("Semana proxima"));
    }

    @Test
    void filtroMalFormadoOInvertidoDa422() throws Exception {
        mockMvc.perform(get(URL).session(c.session()).param("desde", "ayer"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='desde')]").exists());
        mockMvc.perform(get(URL).session(c.session())
                        .param("desde", "2026-12-01T03:00:00Z").param("hasta", "2026-11-01T03:00:00Z"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='hasta')]").exists());
    }

    @Test
    void edicionBajaY404() throws Exception {
        Long id = insertar("2026-12-24T03:00:00Z", "2026-12-26T03:00:00Z", "Fiestas");
        mockMvc.perform(c.firmar(put(URL + "/" + id)).contentType("application/json")
                        .content(bloqueo("2026-12-24T03:00:00Z", "2026-12-26T03:00:00Z", "\"Vacaciones\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivo").value("Vacaciones"));

        mockMvc.perform(c.firmar(delete(URL + "/" + id))).andExpect(status().isNoContent());
        mockMvc.perform(c.firmar(delete(URL + "/" + id))).andExpect(status().isNotFound());
        mockMvc.perform(c.firmar(put(URL + "/999999")).contentType("application/json")
                        .content(bloqueo("2026-12-24T03:00:00Z", "2026-12-26T03:00:00Z", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinSesionDa401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
