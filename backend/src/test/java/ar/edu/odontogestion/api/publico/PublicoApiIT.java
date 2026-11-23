package ar.edu.odontogestion.api.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Consultas públicas sin sesión (C-09 grupo 2). "Ahora" = viernes 2026-11-20 09:00 BA, anticipación de 120 min. */
class PublicoApiIT extends AbstractTurnoIT {

    @Autowired
    private ObjectMapper json;

    @Test
    void prestacionesSinSesionDevuelveSoloIdNombreYDuracionDeLasActivas() throws Exception {
        Long activa = crearPrestacion(45);
        Long inactiva = crearPrestacionInactiva(30);

        String cuerpo = mockMvc.perform(get("/api/v1/publico/prestaciones"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> lista = json.readValue(cuerpo, new TypeReference<>() { });
        assertThat(lista).extracting(m -> ((Number) m.get("id")).longValue()).contains(activa)
                .doesNotContain(inactiva);
        assertThat(lista).allSatisfy(m -> assertThat(m.keySet()).containsExactlyInAnyOrder("id", "nombre",
                "duracionMin"));
        Map<String, Object> la45 = lista.stream().filter(m -> ((Number) m.get("id")).longValue() == activa)
                .findFirst().orElseThrow();
        assertThat(la45.get("duracionMin")).isEqualTo(45);
    }

    @Test
    void disponibilidadSinSesionDevuelveSoloInicioYFinSinDatosDeTurnosNiPacientes() throws Exception {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        String cuerpo = mockMvc.perform(get("/api/v1/publico/disponibilidad")
                        .param("prestacionId", prestacion.toString()).param("desde", "2026-11-23")
                        .param("hasta", "2026-11-23"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> huecos = json.readValue(cuerpo, new TypeReference<>() { });
        assertThat(huecos).isNotEmpty().allSatisfy(h -> assertThat(h.keySet()).containsExactlyInAnyOrder("inicio",
                "fin"));
        assertThat(huecos).extracting(h -> h.get("inicio")).contains("2026-11-23T12:00:00Z")
                .doesNotContain("2026-11-23T13:00:00Z");
        assertThat(cuerpo).doesNotContain("paciente").doesNotContain("Ficticia").doesNotContain("estado")
                .doesNotContain("@ejemplo.test");
    }

    @Test
    void laDisponibilidadAplicaLaAnticipacionPublica() throws Exception {
        Long prestacion = crearPrestacion(30);

        String cuerpo = mockMvc.perform(get("/api/v1/publico/disponibilidad")
                        .param("prestacionId", prestacion.toString()).param("desde", "2026-11-20")
                        .param("hasta", "2026-11-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inicio").value("2026-11-20T14:00:00Z"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("2026-11-20T13:00:00Z").doesNotContain("2026-11-20T13:30:00Z");
    }

    @Test
    void erroresDeLaDisponibilidad() throws Exception {
        Long inactiva = crearPrestacionInactiva(30);
        Long activa = crearPrestacion(30);

        mockMvc.perform(get("/api/v1/publico/disponibilidad").param("prestacionId", inactiva.toString())
                        .param("desde", "2026-11-23").param("hasta", "2026-11-23"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/publico/disponibilidad").param("prestacionId", activa.toString())
                        .param("desde", "2026-11-23").param("hasta", "2027-01-30"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/publico/disponibilidad").param("prestacionId", "999999999")
                        .param("desde", "2026-11-23").param("hasta", "2026-11-23"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/publico/disponibilidad").param("prestacionId", activa.toString())
                        .param("desde", "ayer").param("hasta", "2026-11-23"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/publico/disponibilidad").param("prestacionId", activa.toString())
                        .param("desde", "2026-11-23"))
                .andExpect(status().isBadRequest());
    }
}
