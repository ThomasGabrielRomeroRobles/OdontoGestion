package ar.edu.odontogestion.domain.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PropositoTokenTest {

    @Test
    void losTresPropositosDeTurnoExigenReferenciaDeTurno() {
        assertThat(PropositoToken.CONFIRMAR_SOLICITUD.referenciaRequerida()).isEqualTo(ReferenciaToken.Turno.class);
        assertThat(PropositoToken.CONFIRMAR_TURNO.referenciaRequerida()).isEqualTo(ReferenciaToken.Turno.class);
        assertThat(PropositoToken.CANCELAR_TURNO.referenciaRequerida()).isEqualTo(ReferenciaToken.Turno.class);
    }

    @Test
    void aceptarAvisoExigeAvisoYListaDeEsperaExigeEntrada() {
        assertThat(PropositoToken.ACEPTAR_AVISO.referenciaRequerida()).isEqualTo(ReferenciaToken.Aviso.class);
        assertThat(PropositoToken.CONFIRMAR_LISTA_ESPERA.referenciaRequerida())
                .isEqualTo(ReferenciaToken.EntradaListaEspera.class);
    }

    @Test
    void admiteSoloLaReferenciaDelTipoRequerido() {
        assertThat(PropositoToken.CANCELAR_TURNO.admite(new ReferenciaToken.Turno(900101))).isTrue();
        assertThat(PropositoToken.CANCELAR_TURNO.admite(new ReferenciaToken.Aviso(900101))).isFalse();
        assertThat(PropositoToken.ACEPTAR_AVISO.admite(new ReferenciaToken.Aviso(900007))).isTrue();
        assertThat(PropositoToken.ACEPTAR_AVISO.admite(new ReferenciaToken.Turno(900007))).isFalse();
        assertThat(PropositoToken.CONFIRMAR_LISTA_ESPERA.admite(new ReferenciaToken.EntradaListaEspera(900009)))
                .isTrue();
        assertThat(PropositoToken.CONFIRMAR_LISTA_ESPERA.admite(new ReferenciaToken.Turno(900009))).isFalse();
    }

    @Test
    void idCeroONegativoEsRechazado() {
        assertThatThrownBy(() -> new ReferenciaToken.Turno(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReferenciaToken.Aviso(-5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReferenciaToken.EntradaListaEspera(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ReferenciaToken.Turno(1).id()).isEqualTo(1);
    }
}
