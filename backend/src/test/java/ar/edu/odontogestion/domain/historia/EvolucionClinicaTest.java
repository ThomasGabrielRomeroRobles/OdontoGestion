package ar.edu.odontogestion.domain.historia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Ventana de edición de una evolución por su autor (C-05 design D5): límite inclusivo. */
class EvolucionClinicaTest {

    private static final Duration VENTANA = Duration.ofHours(24);
    private static final Instant ALTA = Instant.parse("2026-11-25T10:00:00Z");

    private Usuario autor;
    private Usuario otro;
    private EvolucionClinica evolucion;

    @BeforeEach
    void datos() {
        autor = usuarioConId(1L);
        otro = usuarioConId(2L);
        evolucion = EvolucionClinica.nueva(new HistoriaClinica(), autor, null, LocalDate.of(2026, 11, 25),
                "Nota ficticia");
        ReflectionTestUtils.setField(evolucion, "creadoEn", ALTA);
    }

    private static Usuario usuarioConId(Long id) {
        Usuario u = org.mockito.Mockito.mock(Usuario.class);
        org.mockito.Mockito.when(u.getId()).thenReturn(id);
        return u;
    }

    @Test
    void autorDentroDeLaVentanaPuedeEditar() {
        assertThat(evolucion.esEditablePor(1L, ALTA.plus(Duration.ofHours(10)), VENTANA)).isTrue();
    }

    @Test
    void justoEnElLimiteTodaviaEsEditable() {
        assertThat(evolucion.esEditablePor(1L, ALTA.plus(VENTANA), VENTANA)).isTrue();
    }

    @Test
    void unSegundoDespuesDelLimiteYaNoEsEditable() {
        assertThat(evolucion.esEditablePor(1L, ALTA.plus(VENTANA).plusSeconds(1), VENTANA)).isFalse();
        assertThatThrownBy(() -> evolucion.editar(1L, LocalDate.of(2026, 11, 25), "Nueva",
                ALTA.plus(VENTANA).plusSeconds(1), VENTANA))
                .isInstanceOf(EvolucionFueraDeVentanaException.class);
        assertThat(evolucion.getNota()).isEqualTo("Nota ficticia");
    }

    @Test
    void otroUsuarioNoPuedeEditar() {
        assertThat(evolucion.esEditablePor(2L, ALTA, VENTANA)).isFalse();
        assertThatThrownBy(() -> evolucion.editar(otro.getId(), LocalDate.of(2026, 11, 25), "Nueva", ALTA, VENTANA))
                .isInstanceOf(NoEsAutorException.class);
    }

    @Test
    void edicionConservaElAltaYMarcaLaEdicion() {
        Instant ahora = ALTA.plus(Duration.ofHours(10));
        evolucion.editar(1L, LocalDate.of(2026, 11, 24), "  Nota corregida  ", ahora, VENTANA);
        assertThat(evolucion.getNota()).isEqualTo("Nota corregida");
        assertThat(evolucion.getFecha()).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(evolucion.getEditadaEn()).isEqualTo(ahora);
        assertThat(evolucion.getCreadoEn()).isEqualTo(ALTA);
    }
}
