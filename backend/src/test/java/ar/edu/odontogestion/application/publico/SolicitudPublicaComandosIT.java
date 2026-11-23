package ar.edu.odontogestion.application.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** La fachada sin transacción con el servicio real: un 409 no deja nada y trae alternativas públicas. */
class SolicitudPublicaComandosIT extends AbstractTurnoIT {

    @Autowired
    private SolicitudPublicaComandos comandos;

    private static DatosSolicitudPublica solicitud(Long prestacion, Instant inicio, String dni, String email) {
        return new DatosSolicitudPublica(prestacion, inicio,
                new DatosPaciente("Beto", "Ficticio", dni, null, "+54 11 4444-0000", email, null));
    }

    @Test
    void huecoTomadoDa409ConAlternativasYNoDejaPacienteNiTurnoNiNotificacion() {
        Long ocupante = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(ocupante, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));
        String ip = ipNueva();

        assertThatThrownBy(() -> comandos.solicitar(ip,
                solicitud(prestacion, lunes("10:00"), "38990101", "comandos1@ejemplo.test"), false))
                .isInstanceOfSatisfying(HuecoNoDisponibleException.class, e -> assertThat(e.alternativas())
                        .containsExactly(new Hueco(lunes("10:30"), lunes("11:00")),
                                new Hueco(lunes("10:45"), lunes("11:15")),
                                new Hueco(lunes("11:00"), lunes("11:30"))));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = '38990101'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE prestacion_id = ?", Integer.class,
                prestacion)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo = 'CONFIRMAR_SOLICITUD' "
                + "AND turno_id IN (SELECT id FROM turno WHERE prestacion_id = ?)", Integer.class, prestacion))
                .isZero();
    }

    @Test
    void conSenueloNoSeCreaNadaYSeCuentaLaIp() {
        Long prestacion = crearPrestacion(30);
        String ip = ipNueva();

        comandos.solicitar(ip, solicitud(prestacion, lunes("10:00"), "38990102", "comandos2@ejemplo.test"), true);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = '38990102'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE prestacion_id = ?", Integer.class,
                prestacion)).isZero();
    }

    @Test
    void unaSolicitudValidaCreaElTurnoPendiente() {
        Long prestacion = crearPrestacion(30);

        comandos.solicitar(ipNueva(), solicitud(prestacion, lunes("10:00"), "38990103", "comandos3@ejemplo.test"),
                false);

        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE prestacion_id = ?", String.class, prestacion))
                .isEqualTo("PENDIENTE_CONFIRMACION");
    }
}
