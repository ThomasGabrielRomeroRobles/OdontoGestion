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

/** API de horarios con sesión, CSRF y PostgreSQL reales. Cada caso deja los horarios del seed de V4. */
class HorarioApiIT extends AbstractWebIntegrationTest {

    private static final String URL = "/api/v1/horarios";

    private Cliente c;

    @BeforeEach
    void iniciar() throws Exception {
        restaurarSeed();
        c = iniciarSesionSeed();
    }

    @AfterEach
    void restaurarSeed() {
        jdbc.update("DELETE FROM horario_trabajo");
        jdbc.update("INSERT INTO horario_trabajo (consultorio_id, dia_semana, hora_desde, hora_hasta) "
                + "SELECT c.id, d, f.desde, f.hasta FROM consultorio c CROSS JOIN generate_series(1, 5) d "
                + "CROSS JOIN (VALUES (time '09:00', time '13:00'), (time '14:00', time '18:00')) f (desde, hasta)");
        jdbc.update("UPDATE consultorio SET granularidad_min = 15");
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return mockMvc.perform(c.firmar(post(URL)).contentType("application/json").content(cuerpo));
    }

    private static String franja(int dia, String desde, String hasta) {
        return "{\"diaSemana\":" + dia + ",\"horaDesde\":\"" + desde + "\",\"horaHasta\":\"" + hasta + "\"}";
    }

    private Long idDe(int dia, String desde) {
        return jdbc.queryForObject("SELECT id FROM horario_trabajo WHERE dia_semana = ? AND hora_desde = ?::time",
                Long.class, dia, desde);
    }

    private Integer cantidad() {
        return jdbc.queryForObject("SELECT count(*) FROM horario_trabajo", Integer.class);
    }

    @Test
    void altaValidaDelSabado() throws Exception {
        crear(franja(6, "09:00", "12:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.diaSemana").value(6))
                .andExpect(jsonPath("$.horaDesde").value("09:00"))
                .andExpect(jsonPath("$.horaHasta").value("12:00"));
    }

    @Test
    void reglasDeNegocioDan422PorCampo() throws Exception {
        crear(franja(6, "14:00", "13:00")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='horaHasta')]").exists());
        crear(franja(8, "09:00", "12:00")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='diaSemana')]").exists());
        crear(franja(6, "09:10", "12:00")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='horaDesde')]").exists());
        assertCantidad(10);
    }

    @Test
    void horaMalFormadaDa422NoUn400() throws Exception {
        crear(franja(6, "9", "12:00")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='horaDesde')]").exists());
        crear(franja(6, "09:00", "doce")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='horaHasta')]").exists());
    }

    @Test
    void campoAusenteDa400() throws Exception {
        crear("{\"diaSemana\":6,\"horaDesde\":\"09:00\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='horaHasta')]").exists());
    }

    @Test
    void solapamientoConElSeedRechazadoYContiguoUOtroDiaPermitidos() throws Exception {
        crear(franja(1, "12:00", "15:00")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='horaDesde')]").exists());
        crear(franja(1, "13:00", "14:00")).andExpect(status().isCreated());
        crear(franja(6, "09:00", "13:00")).andExpect(status().isCreated());
        assertCantidad(12);
    }

    @Test
    void edicionQueSolapaNoModifica() throws Exception {
        Long tarde = idDe(1, "14:00");
        mockMvc.perform(c.firmar(put(URL + "/" + tarde)).contentType("application/json")
                        .content(franja(1, "12:00", "18:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='horaDesde')]").exists());

        mockMvc.perform(get(URL).session(c.session()))
                .andExpect(jsonPath("$[?(@.id==" + tarde + ")].horaDesde").value("14:00"));
    }

    @Test
    void edicionValidaSobreSiMisma() throws Exception {
        mockMvc.perform(c.firmar(put(URL + "/" + idDe(1, "09:00"))).contentType("application/json")
                        .content(franja(1, "08:00", "13:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaDesde").value("08:00"));
    }

    @Test
    void listadoOrdenadoPorDiaYHora() throws Exception {
        crear(franja(2, "07:00", "08:00")).andExpect(status().isCreated());
        mockMvc.perform(get(URL).session(c.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[0].diaSemana").value(1))
                .andExpect(jsonPath("$[0].horaDesde").value("09:00"))
                .andExpect(jsonPath("$[1].horaDesde").value("14:00"))
                .andExpect(jsonPath("$[2].diaSemana").value(2))
                .andExpect(jsonPath("$[2].horaDesde").value("07:00"));
    }

    @Test
    void bajaYBajaDeInexistente() throws Exception {
        mockMvc.perform(c.firmar(delete(URL + "/" + idDe(2, "09:00")))).andExpect(status().isNoContent());
        assertCantidad(9);
        mockMvc.perform(c.firmar(delete(URL + "/999999"))).andExpect(status().isNotFound());
    }

    @Test
    void escrituraSinCsrfDa403() throws Exception {
        mockMvc.perform(post(URL).session(c.session()).contentType("application/json")
                        .content(franja(6, "09:00", "12:00")))
                .andExpect(status().isForbidden());
        assertCantidad(10);
    }

    private void assertCantidad(int esperada) {
        org.assertj.core.api.Assertions.assertThat(cantidad()).isEqualTo(esperada);
    }
}
