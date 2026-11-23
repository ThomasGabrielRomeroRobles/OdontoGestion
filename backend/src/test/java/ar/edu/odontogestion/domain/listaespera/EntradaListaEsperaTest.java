package ar.edu.odontogestion.domain.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EntradaListaEsperaTest {

    private static final Instant LIMITE = Instant.parse("2026-11-21T12:00:00Z");

    private static EntradaListaEspera pendiente() {
        return EntradaListaEspera.pendiente(null, null, null, LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 30),
                OrigenEntrada.PUBLICA, LIMITE);
    }

    private static EntradaListaEspera activa() {
        EntradaListaEspera e = pendiente();
        e.confirmar();
        return e;
    }

    @Test
    void nacePendienteYConfirmarLaActiva() {
        EntradaListaEspera e = pendiente();
        assertThat(e.getEstado()).isEqualTo(EstadoEntrada.PENDIENTE_CONFIRMACION);
        e.confirmar();
        assertThat(e.getEstado()).isEqualTo(EstadoEntrada.ACTIVA);
        assertThat(e.getPendienteHasta()).isNull();
    }

    @Test
    void confirmarUnaEntradaQueNoEstaPendienteEsInvalido() {
        EntradaListaEspera e = activa();
        assertThatThrownBy(e::confirmar).isInstanceOf(TransicionListaEsperaInvalidaException.class);
        EntradaListaEspera cancelada = pendiente();
        cancelada.cancelar();
        assertThatThrownBy(cancelada::confirmar).isInstanceOf(TransicionListaEsperaInvalidaException.class);
    }

    @Test
    void cancelarDesdePendienteOActiva() {
        EntradaListaEspera p = pendiente();
        p.cancelar();
        assertThat(p.getEstado()).isEqualTo(EstadoEntrada.CANCELADA);
        EntradaListaEspera a = activa();
        a.cancelar();
        assertThat(a.getEstado()).isEqualTo(EstadoEntrada.CANCELADA);
    }

    @Test
    void cancelarUnaEntradaCubiertaOCanceladaEsInvalido() {
        EntradaListaEspera cubierta = activa();
        cubierta.cubrir(7L);
        assertThatThrownBy(cubierta::cancelar).isInstanceOf(TransicionListaEsperaInvalidaException.class);
        EntradaListaEspera cancelada = activa();
        cancelada.cancelar();
        assertThatThrownBy(cancelada::cancelar).isInstanceOf(TransicionListaEsperaInvalidaException.class);
    }

    @Test
    void cubrirGuardaElTurnoResultanteSoloDesdeActiva() {
        EntradaListaEspera a = activa();
        a.cubrir(42L);
        assertThat(a.getEstado()).isEqualTo(EstadoEntrada.CUBIERTA);
        assertThat(a.getTurnoResultanteId()).isEqualTo(42L);
        assertThatThrownBy(() -> pendiente().cubrir(1L)).isInstanceOf(TransicionListaEsperaInvalidaException.class);
        assertThatThrownBy(() -> a.cubrir(2L)).isInstanceOf(TransicionListaEsperaInvalidaException.class);
    }

    @Test
    void vencerPendienteSoloDesdePendiente() {
        EntradaListaEspera p = pendiente();
        p.vencerPendiente();
        assertThat(p.getEstado()).isEqualTo(EstadoEntrada.VENCIDA);
        assertThatThrownBy(() -> activa().vencerPendiente())
                .isInstanceOf(TransicionListaEsperaInvalidaException.class);
    }

    @Test
    void pendienteVencidaCompararContraElPlazo() {
        EntradaListaEspera p = pendiente();
        assertThat(p.pendienteVencida(LIMITE.minusSeconds(1))).isFalse();
        assertThat(p.pendienteVencida(LIMITE)).isTrue();
        assertThat(activa().pendienteVencida(LIMITE.plusSeconds(1))).isFalse();
    }

    @Test
    void rangoInvertidoSeRechaza() {
        assertThatThrownBy(() -> EntradaListaEspera.pendiente(null, null, null, LocalDate.of(2026, 12, 2),
                LocalDate.of(2026, 12, 1), OrigenEntrada.PUBLICA, LIMITE)).isInstanceOf(IllegalArgumentException.class);
    }
}
