package ar.edu.odontogestion.domain.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DemasiadasSolicitudesExceptionTest {

    @Test
    void esUnExcesoDeIntentosConCodigoPropio() {
        DemasiadasSolicitudesException ex = new DemasiadasSolicitudesException(90.2);

        assertThat(ex).isInstanceOf(DemasiadosIntentosException.class);
        assertThat(ex.codigo()).isEqualTo("demasiadas-solicitudes");
        assertThat(ex.retryAfterSegundos()).isEqualTo(91);
    }

    @Test
    void elMensajeEsFijoYLaEsperaNuncaBajaDeUnSegundo() {
        DemasiadasSolicitudesException corta = new DemasiadasSolicitudesException(0.1);
        DemasiadasSolicitudesException larga = new DemasiadasSolicitudesException(3600);

        assertThat(corta.getMessage()).isEqualTo("Demasiadas solicitudes. Intente nuevamente más tarde.")
                .isEqualTo(larga.getMessage());
        assertThat(corta.retryAfterSegundos()).isEqualTo(1);
        assertThat(larga.retryAfterSegundos()).isEqualTo(3600);
    }
}
