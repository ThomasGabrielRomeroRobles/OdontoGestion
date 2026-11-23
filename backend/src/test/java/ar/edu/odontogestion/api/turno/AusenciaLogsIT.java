package ar.edu.odontogestion.api.turno;

import static ar.edu.odontogestion.support.FixedClockTestConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.application.notificacion.CanalEnMemoria;
import ar.edu.odontogestion.application.notificacion.NotificationService;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Las operaciones de ausentismo no escriben DNI, email ni teléfono del paciente en los logs (regla dura #10). */
@ExtendWith(OutputCaptureExtension.class)
class AusenciaLogsIT extends AbstractTurnoIT {

    private static final List<String> LOGGERS = List.of("ar.edu.odontogestion", "org.springframework.jdbc");

    @Autowired
    private LoggingSystem logging;

    @Autowired
    private NotificationService notificaciones;

    @Autowired
    private CanalEnMemoria canal;

    @BeforeEach
    void logsDetallados() {
        canal.reiniciar();
        LOGGERS.forEach(l -> logging.setLogLevel(l, LogLevel.TRACE));
    }

    @AfterEach
    void restaurar() {
        LOGGERS.forEach(l -> logging.setLogLevel(l, null));
        canal.reiniciar();
    }

    @Test
    void marcarRevertirYConsultarElHistorialNoLoguean(CapturedOutput salida) throws Exception {
        Cliente c = iniciarSesionSeed();
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Instant inicio = AHORA.minus(Duration.ofMinutes(20));
        Long turno = insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, inicio,
                inicio.plus(Duration.ofMinutes(30)));
        Map<String, Object> datos = jdbc.queryForMap(
                "SELECT dni, email::text AS email, telefono FROM paciente WHERE id = ?", paciente);
        int inicioSalida = salida.getAll().length();

        mockMvc.perform(c.firmar(post("/api/v1/turnos/" + turno + "/ausente"))).andExpect(status().isOk());
        notificaciones.procesarPendientes();
        mockMvc.perform(c.firmar(post("/api/v1/turnos/" + turno + "/revertir-ausencia"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/pacientes/" + paciente + "/ausencias").session(c.session()))
                .andExpect(status().isOk());

        String logs = salida.getAll().substring(inicioSalida);
        assertThat(logs).isNotBlank()
                .doesNotContain((String) datos.get("dni"))
                .doesNotContain((String) datos.get("email"))
                .doesNotContain((String) datos.get("telefono"));
    }
}
