package ar.edu.odontogestion.application.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.application.turno.ReservaTurnoService;
import ar.edu.odontogestion.application.turno.SolicitudReserva;
import ar.edu.odontogestion.domain.agenda.PoliticaAnticipacion;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.OrigenTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** Avisos del turno por la API completa: sesión, CSRF, PostgreSQL y el canal falso en memoria de C-03. */
class NotificacionesTurnoIT extends AbstractTurnoIT {

    private static final String URL = "/api/v1/turnos";

    @Autowired
    private CanalEnMemoria canal;
    @Autowired
    private NotificationService notificaciones;
    @Autowired
    private ReservaTurnoService reservas;
    @Autowired
    private ObjectMapper json;

    private Cliente c;
    private Long paciente;
    private Long prestacion;
    private String emailPaciente;

    @BeforeEach
    void iniciar() throws Exception {
        canal.reiniciar();
        c = iniciarSesionSeed();
        paciente = crearPaciente();
        prestacion = crearPrestacion(30);
        emailPaciente = jdbc.queryForObject("SELECT email::text FROM paciente WHERE id = ?", String.class, paciente);
    }

    @AfterEach
    void reiniciarCanal() {
        canal.reiniciar();
    }

    private ResultActions crearPorApi(String inicio) throws Exception {
        return mockMvc.perform(c.firmar(post(URL)).contentType("application/json").content(
                "{\"pacienteId\":" + paciente + ",\"prestacionId\":" + prestacion + ",\"inicio\":\""
                        + lunes(inicio) + "\"}"));
    }

    private Long crearYObtenerId(String inicio) throws Exception {
        String cuerpo = crearPorApi(inicio).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return json.readTree(cuerpo).get("id").asLong();
    }

    private List<Map<String, Object>> notificacionesDe(Long turnoId) {
        return jdbc.queryForList("SELECT tipo, estado, destinatario::text AS destinatario, datos::text AS datos, "
                + "intentos FROM notificacion WHERE turno_id = ? ORDER BY id", turnoId);
    }

    @Test
    void crearEncolaLaConfirmacionParaElEmailDelPacienteConSoloLosTresDatos() throws Exception {
        Long id = crearYObtenerId("10:00");

        assertThat(notificacionesDe(id)).singleElement().satisfies(n -> {
            assertThat(n).containsEntry("tipo", "CONFIRMACION_TURNO").containsEntry("estado", "PENDIENTE")
                    .containsEntry("destinatario", emailPaciente);
            JsonNode datos = leer((String) n.get("datos"));
            assertThat(datos.size()).isEqualTo(3);
            assertThat(datos.get("inicio").asText()).isEqualTo(lunes("10:00").toString());
            assertThat(datos.get("prestacion").asText()).startsWith("IT-Turno-");
            assertThat(datos.get("consultorio").asText()).isNotBlank();
            assertThat((String) n.get("datos")).doesNotContainPattern("38\\d{6}").doesNotContain("@");
        });
        assertThat(canal.enviados()).as("el envío lo hace el scheduler, no la operación").isEmpty();
    }

    @Test
    void moverReEncolaOtraConfirmacionConElHorarioNuevo() throws Exception {
        Long id = crearYObtenerId("10:00");

        mockMvc.perform(c.firmar(put(URL + "/" + id + "/mover")).contentType("application/json")
                .content("{\"inicio\":\"" + lunes("11:00") + "\"}")).andExpect(status().isOk());

        List<Map<String, Object>> filas = notificacionesDe(id);
        assertThat(filas).hasSize(2).allSatisfy(n -> assertThat(n).containsEntry("tipo", "CONFIRMACION_TURNO")
                .containsEntry("estado", "PENDIENTE"));
        assertThat(leer((String) filas.get(0).get("datos")).get("inicio").asText()).isEqualTo(lunes("10:00").toString());
        assertThat(leer((String) filas.get(1).get("datos")).get("inicio").asText()).isEqualTo(lunes("11:00").toString());
    }

    @Test
    void cancelarEncolaUnaCancelacionYUnaSegundaCancelacionNoDuplica() throws Exception {
        Long id = crearYObtenerId("10:00");

        mockMvc.perform(c.firmar(post(URL + "/" + id + "/cancelar"))).andExpect(status().isOk());
        mockMvc.perform(c.firmar(post(URL + "/" + id + "/cancelar"))).andExpect(status().isUnprocessableEntity());

        assertThat(notificacionesDe(id)).extracting(n -> n.get("tipo"))
                .containsExactly("CONFIRMACION_TURNO", "CANCELACION");
    }

    @Test
    void siElCanalFallaElTurnoQuedaCanceladoYLaNotificacionQuedaParaReintento() throws Exception {
        Long id = crearYObtenerId("10:00");
        canal.fallarSiempre();

        mockMvc.perform(c.firmar(post(URL + "/" + id + "/cancelar"))).andExpect(status().isOk());
        notificaciones.procesarPendientes();

        assertThat(estadoDe(id)).isEqualTo("CANCELADO");
        List<Map<String, Object>> filas = notificacionesDe(id);
        assertThat(filas).hasSize(2);
        Map<String, Object> cancelacion = filas.get(1);
        assertThat(cancelacion).containsEntry("tipo", "CANCELACION").containsEntry("estado", "PENDIENTE")
                .containsEntry("intentos", 1);
    }

    @Test
    void unConflictoNoDejaNotificacionNiCambiaLasExistentes() throws Exception {
        Long id = crearYObtenerId("10:00");
        Long otro = insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("11:00"), lunes("11:30"));

        crearPorApi("10:15").andExpect(status().isConflict());
        mockMvc.perform(c.firmar(put(URL + "/" + id + "/mover")).contentType("application/json")
                .content("{\"inicio\":\"" + lunes("11:15") + "\"}")).andExpect(status().isConflict());

        assertThat(notificacionesDe(id)).hasSize(1);
        assertThat(notificacionesDe(otro)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE turno_id IN (SELECT id FROM turno "
                + "WHERE paciente_id = ?)", Integer.class, paciente)).isEqualTo(1);
    }

    @Test
    void unaSolicitudPendienteNoEncolaConfirmacionDeTurno() {
        Instant inicio = lunes("12:00");

        var dto = reservas.reservar(new SolicitudReserva(paciente, prestacion, inicio, OrigenTurno.PUBLICA,
                EstadoTurno.PENDIENTE_CONFIRMACION, PoliticaAnticipacion.publica(120)));

        assertThat(dto.estado()).isEqualTo(EstadoTurno.PENDIENTE_CONFIRMACION);
        assertThat(dto.pendienteHasta()).isEqualTo(reloj.instant().plusSeconds(30 * 60));
        assertThat(notificacionesDe(dto.id())).isEmpty();
    }

    private JsonNode leer(String texto) {
        try {
            return json.readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
