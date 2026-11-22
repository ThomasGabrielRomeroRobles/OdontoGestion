package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import ar.edu.odontogestion.domain.turno.OrigenTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReservaTurnoServiceIT extends AbstractTurnoIT {

    @Autowired
    private ReservaTurnoService reservas;

    private TurnoDto reservar(Long pacienteId, Long prestacionId, Instant inicio) {
        return reservas.reservar(SolicitudReserva.delOdontologo(pacienteId, prestacionId, inicio));
    }

    @Test
    void creaUnTurnoReservadoDelOdontologoConFinCalculadoDeLaPrestacion() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);

        TurnoDto dto = reservar(paciente, prestacion, lunes("10:00"));

        assertThat(dto.id()).isPositive();
        assertThat(dto.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(dto.origen()).isEqualTo(OrigenTurno.ODONTOLOGO);
        assertThat(dto.inicio()).isEqualTo(lunes("10:00"));
        assertThat(dto.fin()).isEqualTo(lunes("10:30"));
        assertThat(dto.paciente().id()).isEqualTo(paciente);
        assertThat(dto.paciente().nombre()).isEqualTo("Ana");
        assertThat(dto.prestacion().id()).isEqualTo(prestacion);
        assertThat(dto.pendienteHasta()).isNull();
        assertThat(dto.version()).isZero();
        assertThat(estadoDe(dto.id())).isEqualTo("RESERVADO");
    }

    @Test
    void laDuracionDeLaPrestacionDeterminaElFin() {
        Long paciente = crearPaciente();

        TurnoDto dto = reservar(paciente, crearPrestacion(60), lunes("11:00"));

        assertThat(dto.fin()).isEqualTo(lunes("12:00"));
    }

    @Test
    void pacienteOPrestacionInexistentesDan404() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);

        assertThatThrownBy(() -> reservar(paciente + 100_000, prestacion, lunes("10:00")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> reservar(paciente, prestacion + 100_000, lunes("10:00")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(contarTurnos()).isZero();
    }

    @Test
    void pacienteOPrestacionDeOtroConsultorioSeTratanComoInexistentes() {
        Long otro = crearOtroConsultorio();
        Long pacienteAjeno = crearPaciente(otro, true);
        Long prestacionAjena = crearPrestacion(otro, 30, true);

        assertThatThrownBy(() -> reservar(pacienteAjeno, crearPrestacion(30), lunes("10:00")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> reservar(crearPaciente(), prestacionAjena, lunes("10:00")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void prestacionInactivaDa422ConCampoPrestacionId() {
        Long paciente = crearPaciente();
        Long inactiva = crearPrestacionInactiva(30);

        assertThatThrownBy(() -> reservar(paciente, inactiva, lunes("10:00")))
                .isInstanceOfSatisfying(ValidacionDominioException.class,
                        e -> assertThat(e.errores().get(0).field()).isEqualTo("prestacionId"));
        assertThat(contarTurnos()).isZero();
    }

    @Test
    void pacienteDadoDeBajaDa422ConCampoPacienteId() {
        Long baja = crearPacienteDeBaja();
        Long prestacion = crearPrestacion(30);

        assertThatThrownBy(() -> reservar(baja, prestacion, lunes("10:00")))
                .isInstanceOfSatisfying(ValidacionDominioException.class,
                        e -> assertThat(e.errores().get(0).field()).isEqualTo("pacienteId"));
        assertThat(contarTurnos()).isZero();
    }

    @Test
    void unSolapeLoRechazaLaBaseConHuecoNoDisponibleYNoDejaFilasNuevas() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        reservar(paciente, prestacion, lunes("10:00"));

        assertThatThrownBy(() -> reservar(paciente, prestacion, lunes("10:15")))
                .isInstanceOf(HuecoNoDisponibleException.class);
        assertThatThrownBy(() -> reservar(paciente, prestacion, lunes("10:00")))
                .isInstanceOf(HuecoNoDisponibleException.class);

        assertThat(contarTurnos()).isEqualTo(1);
    }

    @Test
    void turnoContiguoEsAceptado() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        reservar(paciente, prestacion, lunes("10:00"));

        TurnoDto contiguo = reservar(paciente, prestacion, lunes("10:30"));

        assertThat(contiguo.inicio()).isEqualTo(lunes("10:30"));
        assertThat(contarTurnos()).isEqualTo(2);
    }

    @Test
    void unTurnoCanceladoNoBloqueaElMismoRango() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.CANCELADO, lunes("10:00"), lunes("10:30"));

        assertThat(reservar(paciente, prestacion, lunes("10:00")).estado()).isEqualTo(EstadoTurno.RESERVADO);
    }

    @Test
    void unaPendienteVigenteRetieneElHuecoYDa409() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long pendiente = insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("10:00"),
                lunes("10:30"), reloj.instant().plusSeconds(600));

        assertThatThrownBy(() -> reservar(paciente, prestacion, lunes("10:00")))
                .isInstanceOf(HuecoNoDisponibleException.class);

        assertThat(estadoDe(pendiente)).isEqualTo("PENDIENTE_CONFIRMACION");
        assertThat(contarTurnos()).isEqualTo(1);
    }

    @Test
    void unaPendienteVencidaSeExpiraAntesDeReservarYLiberaElHueco() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long pendiente = insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("10:00"),
                lunes("10:30"), reloj.instant().minusSeconds(1));

        TurnoDto nuevo = reservar(paciente, prestacion, lunes("10:00"));

        assertThat(nuevo.estado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(estadoDe(pendiente)).isEqualTo("EXPIRADO");
    }

    @Test
    void siLaReservaFallaLaExpiracionDeOtrasPendientesTambienSeRevierteConLaTransaccion() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));
        Long vencida = insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("11:00"),
                lunes("11:30"), reloj.instant().minusSeconds(1));

        assertThatThrownBy(() -> reservar(paciente, prestacion, lunes("10:00")))
                .isInstanceOf(HuecoNoDisponibleException.class);

        // El 409 deja la transacción sin efectos; la pendiente vencida se expirará en la próxima operación o job.
        assertThat(estadoDe(vencida)).isEqualTo("PENDIENTE_CONFIRMACION");
    }
}
