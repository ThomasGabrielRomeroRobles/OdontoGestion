package ar.edu.odontogestion.domain.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;
import org.junit.jupiter.api.Test;

class LimiteCancelacionExceptionTest {

    @Test
    void esReglaDeNegocioConCodigoEstable() {
        LimiteCancelacionException ex = new LimiteCancelacionException();

        assertThat(ex).isInstanceOf(ReglaNegocioException.class);
        assertThat(ex.codigo()).isEqualTo("limite-cancelacion");
    }

    @Test
    void elMensajeEsFijoYNoLlevaDatos() {
        assertThat(new LimiteCancelacionException().getMessage())
                .isEqualTo("Ya pasó el plazo para cancelar este turno por el enlace.")
                .isEqualTo(new LimiteCancelacionException().getMessage());
    }
}
