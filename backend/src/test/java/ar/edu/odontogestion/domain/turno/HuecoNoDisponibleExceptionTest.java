package ar.edu.odontogestion.domain.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.error.ConflictoException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HuecoNoDisponibleExceptionTest {

    private static final Hueco H1 = new Hueco(Instant.parse("2026-11-23T13:30:00Z"),
            Instant.parse("2026-11-23T14:00:00Z"));
    private static final Hueco H2 = new Hueco(Instant.parse("2026-11-23T13:45:00Z"),
            Instant.parse("2026-11-23T14:15:00Z"));

    @Test
    void esUnConflictoConCodigoEstableYSinAlternativasPorDefecto() {
        HuecoNoDisponibleException ex = new HuecoNoDisponibleException();

        assertThat(ex).isInstanceOf(ConflictoException.class);
        assertThat(ex.codigo()).isEqualTo("hueco-no-disponible");
        assertThat(ex.alternativas()).isEmpty();
        assertThat(ex.getMessage()).isNotBlank();
    }

    @Test
    void copiaDefensivamenteLasAlternativas() {
        List<Hueco> origen = new ArrayList<>(List.of(H1, H2));
        HuecoNoDisponibleException ex = new HuecoNoDisponibleException(origen);
        origen.clear();

        assertThat(ex.alternativas()).containsExactly(H1, H2);
        assertThatThrownBy(() -> ex.alternativas().add(H1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void conAlternativasDevuelveUnaExcepcionNuevaConElMismoCodigoYMensaje() {
        HuecoNoDisponibleException original = new HuecoNoDisponibleException();

        HuecoNoDisponibleException enriquecida = original.conAlternativas(List.of(H1));

        assertThat(enriquecida).isNotSameAs(original);
        assertThat(enriquecida.alternativas()).containsExactly(H1);
        assertThat(enriquecida.getMessage()).isEqualTo(original.getMessage());
        assertThat(enriquecida.codigo()).isEqualTo("hueco-no-disponible");
        assertThat(original.alternativas()).isEmpty();
    }
}
