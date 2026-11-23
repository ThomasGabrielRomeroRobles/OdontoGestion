package ar.edu.odontogestion.domain.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AvisoHuecoTest {

    private static final Instant INICIO = Instant.parse("2026-11-23T13:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-23T12:00:00Z");

    private static AvisoHueco aviso() {
        return AvisoHueco.enviado(null, null, 1L, 2L, INICIO, INICIO.plusSeconds(1800), VENCE);
    }

    @Test
    void nacePendienteDeRespuesta() {
        assertThat(aviso().getEstado()).isEqualTo(EstadoAviso.ENVIADO);
    }

    @Test
    void aceptarCerrarYExpirarDesdeEnviado() {
        AvisoHueco a = aviso();
        a.aceptar();
        assertThat(a.getEstado()).isEqualTo(EstadoAviso.ACEPTADO);
        AvisoHueco c = aviso();
        c.cerrar();
        assertThat(c.getEstado()).isEqualTo(EstadoAviso.CERRADO);
        AvisoHueco x = aviso();
        x.expirar();
        assertThat(x.getEstado()).isEqualTo(EstadoAviso.EXPIRADO);
    }

    @Test
    void ningunaTransicionDesdeUnEstadoFinal() {
        AvisoHueco a = aviso();
        a.aceptar();
        assertThatThrownBy(a::aceptar).isInstanceOf(TransicionListaEsperaInvalidaException.class);
        assertThatThrownBy(a::cerrar).isInstanceOf(TransicionListaEsperaInvalidaException.class);
        assertThatThrownBy(a::expirar).isInstanceOf(TransicionListaEsperaInvalidaException.class);
        AvisoHueco c = aviso();
        c.cerrar();
        assertThatThrownBy(c::aceptar).isInstanceOf(TransicionListaEsperaInvalidaException.class);
    }

    @Test
    void vencidoUsaVenceEnInclusivo() {
        AvisoHueco a = aviso();
        assertThat(a.vencido(VENCE.minusSeconds(1))).isFalse();
        assertThat(a.vencido(VENCE)).isTrue();
    }
}
