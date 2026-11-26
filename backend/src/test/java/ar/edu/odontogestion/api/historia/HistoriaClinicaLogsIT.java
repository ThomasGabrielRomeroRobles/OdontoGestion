package ar.edu.odontogestion.api.historia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.support.AbstractHistoriaIT;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

/** Ningún dato clínico llega a los logs ni al cuerpo de un error (RN-HC-06, regla dura 10). Marcadores ficticios. */
@ExtendWith(OutputCaptureExtension.class)
class HistoriaClinicaLogsIT extends AbstractHistoriaIT {

    private static final List<String> LOGGERS = List.of("ar.edu.odontogestion", "org.springframework.jdbc");
    private static final List<String> MARCADORES = List.of("AlergiaFicticiaXYZ", "ObsFicticiaXYZ", "NotaFicticiaXYZ");

    @Autowired
    private LoggingSystem logging;

    @BeforeEach
    void logsDetallados() {
        LOGGERS.forEach(l -> logging.setLogLevel(l, LogLevel.TRACE));
    }

    @AfterEach
    void restaurar() {
        LOGGERS.forEach(l -> logging.setLogLevel(l, null));
    }

    @Test
    void escriturasYErroresSinDatosClinicos(CapturedOutput salida) throws Exception {
        Cliente c = iniciarSesionSeed();
        String base = "/api/v1/pacientes/" + crearPaciente() + "/historia";
        int inicio = salida.getAll().length();

        mockMvc.perform(c.firmar(put(base + "/anamnesis")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"alergias\":\"AlergiaFicticiaXYZ\"}")).andExpect(status().isOk());
        mockMvc.perform(c.firmar(put(base + "/odontograma/piezas/16")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"CARIES\",\"observacion\":\"ObsFicticiaXYZ\"}")).andExpect(status().isOk());
        mockMvc.perform(c.firmar(post(base + "/evoluciones")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\":\"NotaFicticiaXYZ\"}")).andExpect(status().isCreated());
        mockMvc.perform(get(base).session(c.session())).andExpect(status().isOk());
        String error = mockMvc.perform(c.firmar(post(base + "/evoluciones")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":\"NotaFicticiaXYZ" + "x".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        String logs = salida.getAll().substring(inicio);
        assertThat(logs).isNotBlank();
        MARCADORES.forEach(m -> assertThat(logs).doesNotContain(m));
        assertThat(error).doesNotContain("NotaFicticiaXYZ");
    }
}
