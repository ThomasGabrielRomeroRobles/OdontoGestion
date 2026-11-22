package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.turno.CanceladoPor;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import ar.edu.odontogestion.domain.turno.OrigenTurno;
import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import ar.edu.odontogestion.domain.turno.evento.TurnoCancelado;
import ar.edu.odontogestion.domain.turno.evento.TurnoCreado;
import ar.edu.odontogestion.domain.turno.evento.TurnoMovido;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Eventos de agenda (design D4/D10). El listener de prueba {@code BEFORE_COMMIT} deja una marca en la base: si el
 * cambio se revierte o hay un 409, la marca no persiste, igual que lo haría una notificación del outbox.
 */
@RecordApplicationEvents
@Import(TurnoEventosIT.MarcaTestConfig.class)
class TurnoEventosIT extends AbstractTurnoIT {

    /** Escribe una fila marcadora en la misma transacción que publica el evento. */
    static class MarcaListener {

        private final JdbcTemplate jdbc;

        MarcaListener(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        void alCrear(TurnoCreado evento) {
            marcar("creado " + evento.turnoId(), evento.consultorioId());
        }

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        void alMover(TurnoMovido evento) {
            marcar("movido " + evento.turnoId(), jdbc.queryForObject("SELECT min(id) FROM consultorio", Long.class));
        }

        private void marcar(String texto, Long consultorioId) {
            jdbc.update("INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta, motivo) "
                    + "VALUES (?, now(), now() + interval '1 hour', ?)", consultorioId, "IT-Turno marca " + texto);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MarcaTestConfig {

        @Bean
        MarcaListener marcaListener(JdbcTemplate jdbc) {
            return new MarcaListener(jdbc);
        }
    }

    @Autowired
    private ApplicationEvents eventos;
    @Autowired
    private ReservaTurnoService reservas;
    @Autowired
    private GestionTurnoService gestion;
    @Autowired
    private TransactionTemplate tx;

    private int marcas(String patron) {
        return jdbc.queryForObject("SELECT count(*) FROM bloqueo_agenda WHERE motivo LIKE ?", Integer.class,
                "IT-Turno marca " + patron);
    }

    private TurnoDto reservar(Long paciente, Long prestacion, String inicio) {
        return reservas.reservar(SolicitudReserva.delOdontologo(paciente, prestacion, lunes(inicio)));
    }

    @Test
    void crearPublicaTurnoCreadoConIdsEstadoYOrigen() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);

        TurnoDto dto = reservar(paciente, prestacion, "10:00");

        assertThat(eventos.stream(TurnoCreado.class)).containsExactly(
                new TurnoCreado(dto.id(), consultorioId(), EstadoTurno.RESERVADO, OrigenTurno.ODONTOLOGO));
        assertThat(eventos.stream(HuecoLiberado.class)).isEmpty();
        assertThat(marcas("creado " + dto.id())).as("el listener BEFORE_COMMIT corrió con el commit").isEqualTo(1);
    }

    @Test
    void moverPublicaTurnoMovidoConAmbosRangosYHuecoLiberadoDelRangoAnterior() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long id = insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        gestion.mover(id, lunes("11:00"));

        assertThat(eventos.stream(TurnoMovido.class)).containsExactly(
                new TurnoMovido(id, lunes("10:00"), lunes("10:30"), lunes("11:00"), lunes("11:30")));
        assertThat(eventos.stream(HuecoLiberado.class)).containsExactly(
                new HuecoLiberado(consultorioId(), id, prestacion, lunes("10:00"), lunes("10:30")));
        assertThat(eventos.stream(TurnoCreado.class)).isEmpty();
        assertThat(marcas("movido " + id)).isEqualTo(1);
    }

    @Test
    void moverSolapadoConSuRangoPublicaElRangoAnteriorCompleto() {
        Long id = insertarTurno(crearPaciente(), crearPrestacion(30), EstadoTurno.CONFIRMADO, lunes("10:00"),
                lunes("10:30"));

        gestion.mover(id, lunes("10:15"));

        assertThat(eventos.stream(HuecoLiberado.class)).singleElement().satisfies(e -> {
            assertThat(e.inicio()).isEqualTo(lunes("10:00"));
            assertThat(e.fin()).isEqualTo(lunes("10:30"));
        });
    }

    @Test
    void cancelarPublicaTurnoCanceladoYHuecoLiberado() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long id = insertarTurno(paciente, prestacion, EstadoTurno.CONFIRMADO, lunes("10:00"), lunes("10:30"));

        gestion.cancelar(id, "Imprevisto");

        assertThat(eventos.stream(TurnoCancelado.class)).containsExactly(
                new TurnoCancelado(id, CanceladoPor.ODONTOLOGO));
        assertThat(eventos.stream(HuecoLiberado.class)).containsExactly(
                new HuecoLiberado(consultorioId(), id, prestacion, lunes("10:00"), lunes("10:30")));
    }

    @Test
    void marcarAtendidoNoPublicaEventosDeAgenda() {
        Long id = insertarTurno(crearPaciente(), crearPrestacion(30), EstadoTurno.RESERVADO, lunes("10:00"),
                lunes("10:30"));

        gestion.marcarAtendido(id);

        assertThat(eventos.stream(TurnoCreado.class)).isEmpty();
        assertThat(eventos.stream(TurnoMovido.class)).isEmpty();
        assertThat(eventos.stream(TurnoCancelado.class)).isEmpty();
        assertThat(eventos.stream(HuecoLiberado.class)).isEmpty();
    }

    @Test
    void unConflictoDeCreacionNoPublicaNingunEventoNiDejaMarca() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        assertThatThrownBy(() -> reservar(paciente, prestacion, "10:15")).isInstanceOf(HuecoNoDisponibleException.class);

        assertThat(eventos.stream(TurnoCreado.class)).isEmpty();
        assertThat(marcas("%")).isZero();
    }

    @Test
    void unConflictoAlMoverNoPublicaEventosNiDejaMarca() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        Long id = insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("11:00"), lunes("11:30"));

        assertThatThrownBy(() -> gestion.mover(id, lunes("11:15"))).isInstanceOf(HuecoNoDisponibleException.class);

        assertThat(eventos.stream(TurnoMovido.class)).isEmpty();
        assertThat(eventos.stream(HuecoLiberado.class)).isEmpty();
        assertThat(marcas("%")).isZero();
    }

    @Test
    void siLaTransaccionSeRevierteDespuesDePublicarLaMarcaNoPersiste() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);

        tx.executeWithoutResult(status -> {
            reservar(paciente, prestacion, "10:00");
            status.setRollbackOnly();
        });

        assertThat(eventos.stream(TurnoCreado.class)).hasSize(1);
        assertThat(marcas("%")).as("BEFORE_COMMIT nunca corrió: no hubo commit").isZero();
        assertThat(contarTurnos()).isZero();
    }
}
