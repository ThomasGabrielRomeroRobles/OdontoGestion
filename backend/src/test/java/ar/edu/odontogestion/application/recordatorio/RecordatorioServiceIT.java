package ar.edu.odontogestion.application.recordatorio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractRecordatorioIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** {@code encolarRecordatorios()} contra PostgreSQL (C-11 grupo 3). Reloj fijo; H = 24 del seed. */
class RecordatorioServiceIT extends AbstractRecordatorioIT {

    @Autowired
    private RecordatorioService servicio;
    @Autowired
    private ObjectMapper json;

    @AfterEach
    void restaurarParametro() {
        jdbc.update("UPDATE consultorio SET recordatorio_horas_antes = 24");
    }

    private Map<String, Object> recordatorioDe(Long turnoId) {
        return jdbc.queryForMap("SELECT estado, destinatario::text AS destinatario, datos::text AS datos, "
                + "enlaces::text AS enlaces FROM notificacion WHERE turno_id = ? AND tipo = 'RECORDATORIO'", turnoId);
    }

    @Test
    void encolaUnRecordatorioPendienteConDatosYEnlacesQueVencenEnElInicio() throws Exception {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Instant inicio = reloj.instant().plus(Duration.ofHours(10));
        Long id = reservado(p, pr, inicio);

        int encolados = servicio.encolarRecordatorios();

        assertThat(encolados).isEqualTo(1);
        Map<String, Object> fila = recordatorioDe(id);
        assertThat(fila).containsEntry("estado", "PENDIENTE");
        assertThat(fila.get("destinatario")).isEqualTo(
                jdbc.queryForObject("SELECT email::text FROM paciente WHERE id = ?", String.class, p));
        JsonNode datos = json.readTree((String) fila.get("datos"));
        assertThat(datos.size()).isEqualTo(3);
        assertThat(datos.get("inicio").asText()).isEqualTo(inicio.toString());
        assertThat(datos.get("prestacion").asText()).startsWith("IT-Turno-");
        assertThat(datos.get("consultorio").asText()).isNotBlank();
        JsonNode enlaces = json.readTree((String) fila.get("enlaces"));
        assertThat(enlaces).hasSize(2);
        assertThat(List.of(enlaces.get(0).get("proposito").asText(), enlaces.get(1).get("proposito").asText()))
                .containsExactlyInAnyOrder("CONFIRMAR_TURNO", "CANCELAR_TURNO");
        enlaces.forEach(e -> assertThat(Instant.parse(e.get("venceEn").asText())).isEqualTo(inicio));
    }

    @Test
    void usaElParametroVigenteRecordatorioHorasAntesParaLaVentana() {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Long aDosDias = reservado(p, pr, reloj.instant().plus(Duration.ofHours(40)));

        assertThat(servicio.encolarRecordatorios()).isZero();

        jdbc.update("UPDATE consultorio SET recordatorio_horas_antes = 48");
        assertThat(servicio.encolarRecordatorios()).isEqualTo(1);
        assertThat(recordatoriosDe(aDosDias)).isEqualTo(1);
    }

    @Test
    void sinCandidatosDevuelveCero() {
        assertThat(servicio.encolarRecordatorios()).isZero();
    }

    @Test
    void pacienteSinEmailSeOmiteYElOtroCandidatoSeEncola() {
        Long sinEmail = crearPaciente();
        jdbc.update("UPDATE paciente SET email = '   ' WHERE id = ?", sinEmail);
        Long conEmail = crearPaciente();
        Long pr = crearPrestacion(30);
        Instant ahora = reloj.instant();
        Long turnoSinEmail = reservado(sinEmail, pr, ahora.plus(Duration.ofHours(2)));
        Long turnoConEmail = reservado(conEmail, pr, ahora.plus(Duration.ofHours(3)));

        assertThat(servicio.encolarRecordatorios()).isEqualTo(1);

        assertThat(recordatoriosDe(turnoSinEmail)).isZero();
        assertThat(recordatoriosDe(turnoConEmail)).isEqualTo(1);
    }

    @Test
    void unTurnoQueFallaAlEncolarNoImpideLosDemas() {
        Long invalido = crearPaciente();
        jdbc.update("UPDATE paciente SET email = 'sin-arroba' WHERE id = ?", invalido);
        Long valido = crearPaciente();
        Long pr = crearPrestacion(30);
        Instant ahora = reloj.instant();
        Long turnoInvalido = reservado(invalido, pr, ahora.plus(Duration.ofHours(2)));
        Long turnoValido = reservado(valido, pr, ahora.plus(Duration.ofHours(3)));

        assertThat(servicio.encolarRecordatorios()).isEqualTo(1);

        assertThat(recordatoriosDe(turnoInvalido)).isZero();
        assertThat(recordatoriosDe(turnoValido)).isEqualTo(1);
    }

    @Test
    void dosLlamadasSeguidasDejanUnaSolaFilaPorTurnoYLaSegundaDevuelveCero() {
        Long p = crearPaciente();
        Long pr = crearPrestacion(30);
        Instant ahora = reloj.instant();
        Long a = reservado(p, pr, ahora.plus(Duration.ofHours(2)));
        Long b = reservado(p, pr, ahora.plus(Duration.ofHours(3)));

        assertThat(servicio.encolarRecordatorios()).isEqualTo(2);
        assertThat(servicio.encolarRecordatorios()).isZero();

        assertThat(recordatoriosDe(a)).isEqualTo(1);
        assertThat(recordatoriosDe(b)).isEqualTo(1);
    }
}
