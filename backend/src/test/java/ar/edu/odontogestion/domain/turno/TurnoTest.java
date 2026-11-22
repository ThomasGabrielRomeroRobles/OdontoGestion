package ar.edu.odontogestion.domain.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.paciente.Paciente;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Dominio puro (sin Spring): fábricas, cálculo de {@code fin} y transiciones (design D4). */
class TurnoTest {

    private static final Instant INICIO = Instant.parse("2026-11-23T13:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-20T12:30:00Z");

    private final Consultorio consultorio = Consultorio.nuevo("Consultorio Demo", "America/Argentina/Buenos_Aires");
    private final Paciente paciente = Paciente.nuevo(consultorio,
            new DatosPaciente("Ana", "Ficticia", "30111222", null, "+54 11 5555-0000", "ana@ejemplo.test", null));

    private Prestacion prestacion(int minutos) {
        return Prestacion.nueva(consultorio, "Consulta", minutos);
    }

    private Turno reservado() {
        return Turno.reservado(consultorio, paciente, prestacion(30), INICIO, OrigenTurno.ODONTOLOGO);
    }

    private Turno pendiente() {
        return Turno.pendiente(consultorio, paciente, prestacion(30), INICIO, OrigenTurno.PUBLICA, VENCE);
    }

    private Turno enEstado(EstadoTurno estado) {
        Turno t = reservado();
        switch (estado) {
            case RESERVADO -> { }
            case CONFIRMADO -> t.confirmar();
            case ATENDIDO -> t.marcarAtendido();
            case AUSENTE -> t.marcarAusente();
            case CANCELADO -> t.cancelar(CanceladoPor.ODONTOLOGO, null);
            case PENDIENTE_CONFIRMACION -> t = pendiente();
            case EXPIRADO -> {
                t = pendiente();
                t.expirar();
            }
        }
        return t;
    }

    @Test
    void reservadoCalculaFinConLaDuracionYNaceReservado() {
        Turno t = Turno.reservado(consultorio, paciente, prestacion(45), INICIO, OrigenTurno.ODONTOLOGO);

        assertThat(t.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(t.getInicio()).isEqualTo(INICIO);
        assertThat(t.getFin()).isEqualTo(INICIO.plus(Duration.ofMinutes(45)));
        assertThat(t.getOrigen()).isEqualTo(OrigenTurno.ODONTOLOGO);
        assertThat(t.getPendienteHasta()).isNull();
        assertThat(t.getCanceladoPor()).isNull();
    }

    @Test
    void reservadoConOtraDuracionYOrigenCalculaOtroFin() {
        Turno t = Turno.reservado(consultorio, paciente, prestacion(15), INICIO, OrigenTurno.LISTA_ESPERA);

        assertThat(t.getFin()).isEqualTo(INICIO.plusSeconds(15 * 60));
        assertThat(t.getOrigen()).isEqualTo(OrigenTurno.LISTA_ESPERA);
    }

    @Test
    void pendienteNaceConVencimiento() {
        Turno t = pendiente();

        assertThat(t.getEstado()).isEqualTo(EstadoTurno.PENDIENTE_CONFIRMACION);
        assertThat(t.getPendienteHasta()).isEqualTo(VENCE);
        assertThat(t.getFin()).isEqualTo(INICIO.plus(Duration.ofMinutes(30)));
        assertThat(t.getOrigen()).isEqualTo(OrigenTurno.PUBLICA);
    }

    @Test
    void marcarAtendidoDesdeReservadoYConfirmado() {
        Turno r = enEstado(EstadoTurno.RESERVADO);
        r.marcarAtendido();
        Turno c = enEstado(EstadoTurno.CONFIRMADO);
        c.marcarAtendido();

        assertThat(r.getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
        assertThat(c.getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
    }

    @Test
    void marcarAtendidoDesdeCanceladoOPendienteLanzaYNoCambiaElEstado() {
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.CANCELADO, EstadoTurno.PENDIENTE_CONFIRMACION,
                EstadoTurno.EXPIRADO, EstadoTurno.ATENDIDO, EstadoTurno.AUSENTE}) {
            Turno t = enEstado(origen);

            assertThatThrownBy(t::marcarAtendido)
                    .as(origen.name())
                    .isInstanceOf(TransicionInvalidaException.class)
                    .extracting(e -> ((TransicionInvalidaException) e).codigo())
                    .isEqualTo("transicion-invalida");
            assertThat(t.getEstado()).as(origen.name()).isEqualTo(origen);
        }
    }

    @Test
    void transicionInvalidaEsUnaReglaDeNegocioConMensajeSinDatosPersonales() {
        Turno t = enEstado(EstadoTurno.CANCELADO);

        assertThatThrownBy(t::marcarAtendido)
                .isInstanceOf(ar.edu.odontogestion.domain.error.ReglaNegocioException.class)
                .hasMessageContaining("CANCELADO")
                .hasMessageContaining("ATENDIDO")
                .hasMessageNotContaining("Ficticia")
                .hasMessageNotContaining("ejemplo.test");
    }

    @Test
    void expirarSoloDesdePendiente() {
        Turno p = pendiente();
        p.expirar();

        assertThat(p.getEstado()).isEqualTo(EstadoTurno.EXPIRADO);
        for (EstadoTurno origen : new EstadoTurno[] {EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO,
                EstadoTurno.CANCELADO, EstadoTurno.EXPIRADO, EstadoTurno.ATENDIDO, EstadoTurno.AUSENTE}) {
            Turno t = enEstado(origen);
            assertThatThrownBy(t::expirar).as(origen.name()).isInstanceOf(TransicionInvalidaException.class);
            assertThat(t.getEstado()).isEqualTo(origen);
        }
    }

    @Test
    void confirmarSolicitudPasaDePendienteAReservado() {
        Turno p = pendiente();
        p.confirmarSolicitud();

        assertThat(p.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThatThrownBy(p::confirmarSolicitud).isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void confirmarSoloDesdeReservado() {
        Turno r = reservado();
        r.confirmar();

        assertThat(r.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThatThrownBy(r::confirmar).isInstanceOf(TransicionInvalidaException.class);
        assertThatThrownBy(pendiente()::confirmar).isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void marcarAusenteDesdeReservadoYConfirmadoPeroNoDesdePendiente() {
        Turno r = enEstado(EstadoTurno.RESERVADO);
        r.marcarAusente();
        Turno c = enEstado(EstadoTurno.CONFIRMADO);
        c.marcarAusente();

        assertThat(r.getEstado()).isEqualTo(EstadoTurno.AUSENTE);
        assertThat(c.getEstado()).isEqualTo(EstadoTurno.AUSENTE);
        assertThatThrownBy(pendiente()::marcarAusente).isInstanceOf(TransicionInvalidaException.class);
    }
}
