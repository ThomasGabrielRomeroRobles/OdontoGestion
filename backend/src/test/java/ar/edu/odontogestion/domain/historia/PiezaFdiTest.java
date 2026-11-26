package ar.edu.odontogestion.domain.historia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Numeración FDI de las 32 piezas permanentes (RN-HC-03, C-05 design D3). */
class PiezaFdiTest {

    @ParameterizedTest
    @ValueSource(ints = {11, 18, 21, 28, 31, 38, 41, 48, 16})
    void piezasPermanentesValidas(int numero) {
        assertThat(PiezaFdi.of(numero).numero()).isEqualTo(numero);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 19, 20, 49, 50, 51, 85, 0, -11, 110})
    void fueraDelConjuntoPermanenteEsInvalida(int numero) {
        assertThatThrownBy(() -> PiezaFdi.of(numero))
                .isInstanceOf(PiezaFdiInvalidaException.class)
                .satisfies(e -> assertThat(((PiezaFdiInvalidaException) e).codigo()).isEqualTo("pieza-fdi-invalida"));
    }

    @Test
    void mensajeFijoSinElValorRecibido() {
        assertThatThrownBy(() -> PiezaFdi.of(19)).hasMessageNotContaining("19");
    }

    @Test
    void permanentesSonTreintaYDosEnOrdenDeCuadrante() {
        assertThat(PiezaFdi.PERMANENTES).hasSize(32).doesNotHaveDuplicates();
        assertThat(PiezaFdi.PERMANENTES.get(0).numero()).isEqualTo(11);
        assertThat(PiezaFdi.PERMANENTES.get(7).numero()).isEqualTo(18);
        assertThat(PiezaFdi.PERMANENTES.get(8).numero()).isEqualTo(21);
        assertThat(PiezaFdi.PERMANENTES.get(31).numero()).isEqualTo(48);
    }

    @Test
    void nueveEstadosDelMvp() {
        assertThat(EstadoPieza.values()).extracting(Enum::name).containsExactly("SANA", "CARIES", "OBTURADA",
                "CORONA", "ENDODONCIA", "AUSENTE", "IMPLANTE", "FRACTURADA", "A_EXTRAER");
    }
}
