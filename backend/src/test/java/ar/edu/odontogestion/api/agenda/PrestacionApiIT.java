package ar.edu.odontogestion.api.agenda;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** API de prestaciones con sesión, CSRF y PostgreSQL reales. Cada caso deja el seed de V4 como estaba. */
class PrestacionApiIT extends AbstractWebIntegrationTest {

    private static final String URL = "/api/v1/prestaciones";

    private Cliente c;

    @BeforeEach
    void iniciar() throws Exception {
        restaurarSeed();
        c = iniciarSesionSeed();
    }

    @AfterEach
    void restaurarSeed() {
        jdbc.update("DELETE FROM prestacion WHERE nombre NOT IN ('Consulta', 'Limpieza', 'Obturación', 'Endodoncia', "
                + "'Extracción', 'Control')");
        jdbc.update("UPDATE prestacion SET activa = true, duracion_min = CASE nombre::text WHEN 'Consulta' THEN 30 "
                + "WHEN 'Limpieza' THEN 45 WHEN 'Obturación' THEN 60 WHEN 'Endodoncia' THEN 90 "
                + "WHEN 'Extracción' THEN 45 ELSE 15 END");
        jdbc.update("UPDATE consultorio SET granularidad_min = 15");
    }

    private ResultActions crear(String nombre, Object duracion) throws Exception {
        return mockMvc.perform(c.firmar(post(URL)).contentType("application/json")
                .content("{\"nombre\":" + json(nombre) + ",\"duracionMin\":" + duracion + "}"));
    }

    private static String json(String texto) {
        return texto == null ? "null" : "\"" + texto + "\"";
    }

    private Long idDe(String nombre) {
        return jdbc.queryForObject("SELECT id FROM prestacion WHERE nombre = ?::citext", Long.class, nombre);
    }

    @Test
    void elSeedSeVePorLaApiOrdenadoPorNombre() throws Exception {
        mockMvc.perform(get(URL).session(c.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].nombre").value("Consulta"))
                .andExpect(jsonPath("$[0].duracionMin").value(30))
                .andExpect(jsonPath("$[0].activa").value(true))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[*].nombre").value(hasItems("Control", "Endodoncia", "Obturación")));
    }

    @Test
    void altaValidaDa201ConLocationYQuedaActiva() throws Exception {
        crear("Blanqueamiento", 60)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(URL + "/" + idDe("Blanqueamiento"))))
                .andExpect(jsonPath("$.nombre").value("Blanqueamiento"))
                .andExpect(jsonPath("$.duracionMin").value(60))
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void duracionNoMultiploCeroYConOtraGranularidadDan422() throws Exception {
        crear("Rara", 40).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='duracionMin')]").exists());
        crear("Rara", 0).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='duracionMin')]").exists());

        jdbc.update("UPDATE consultorio SET granularidad_min = 30");
        crear("Rara", 45).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void nombreDuplicadoSinDistinguirMayusculasYVacioDan422EnNombre() throws Exception {
        crear("consulta", 30).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='nombre')]").exists());
        crear("   ", 30).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='nombre')]").exists());
    }

    @Test
    void campoObligatorioAusenteDa400() throws Exception {
        crear(null, 30).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='nombre')]").exists());
        mockMvc.perform(c.firmar(post(URL)).contentType("application/json").content("{\"nombre\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='duracionMin')]").exists());
    }

    @Test
    void edicionValidaYDeInexistente() throws Exception {
        mockMvc.perform(c.firmar(put(URL + "/" + idDe("Consulta"))).contentType("application/json")
                        .content("{\"nombre\":\"Consulta\",\"duracionMin\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionMin").value(45));

        mockMvc.perform(c.firmar(put(URL + "/999999")).contentType("application/json")
                        .content("{\"nombre\":\"X\",\"duracionMin\":30}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void edicionAlNombreDeOtraDa422() throws Exception {
        mockMvc.perform(c.firmar(put(URL + "/" + idDe("Control"))).contentType("application/json")
                        .content("{\"nombre\":\"CONSULTA\",\"duracionMin\":15}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='nombre')]").exists());
    }

    @Test
    void desactivarYReactivarSinPerderLaPrestacion() throws Exception {
        Long id = idDe("Endodoncia");
        mockMvc.perform(c.firmar(post(URL + "/" + id + "/desactivar")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));
        mockMvc.perform(get(URL).session(c.session()))
                .andExpect(jsonPath("$[?(@.nombre=='Endodoncia')].activa").value(false));
        mockMvc.perform(c.firmar(post(URL + "/" + id + "/activar")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void desactivarFuncionaAunqueLaDuracionYaNoSeaMultiplo() throws Exception {
        jdbc.update("UPDATE consultorio SET granularidad_min = 30");
        mockMvc.perform(c.firmar(post(URL + "/" + idDe("Limpieza") + "/desactivar")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));
    }

    @Test
    void elCambioDeGranularidadNoEsRetroactivo() throws Exception {
        jdbc.update("UPDATE consultorio SET granularidad_min = 30");
        mockMvc.perform(get(URL).session(c.session()))
                .andExpect(jsonPath("$[?(@.nombre=='Limpieza')].duracionMin").value(45));
    }

    @Test
    void noHayBorradoFisico() throws Exception {
        Long id = idDe("Control");
        mockMvc.perform(c.firmar(delete(URL + "/" + id))).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get(URL).session(c.session()))
                .andExpect(jsonPath("$[?(@.nombre=='Control')]").exists());
    }

    // --- 2.4: acceso ---

    @Test
    void sinSesionDa401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void escrituraSinCsrfDa403YNoCrea() throws Exception {
        mockMvc.perform(post(URL).session(c.session()).contentType("application/json")
                        .content("{\"nombre\":\"Sin token\",\"duracionMin\":30}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL).session(c.session()))
                .andExpect(jsonPath("$[?(@.nombre=='Sin token')]").doesNotExist());
    }
}
