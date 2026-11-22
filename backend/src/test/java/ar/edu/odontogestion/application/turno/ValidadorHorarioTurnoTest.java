package ar.edu.odontogestion.application.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.domain.agenda.BloqueoAgenda;
import ar.edu.odontogestion.domain.agenda.BloqueoAgendaRepository;
import ar.edu.odontogestion.domain.agenda.HorarioTrabajo;
import ar.edu.odontogestion.domain.agenda.HorarioTrabajoRepository;
import ar.edu.odontogestion.domain.agenda.PoliticaAnticipacion;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValidadorHorarioTurnoTest {

    /** Viernes 2026-11-20 09:00 en Buenos Aires. */
    private static final Instant AHORA = Instant.parse("2026-11-20T12:00:00Z");

    @Mock
    private HorarioTrabajoRepository horarios;
    @Mock
    private BloqueoAgendaRepository bloqueos;

    private Consultorio consultorio;

    @BeforeEach
    void preparar() {
        consultorio = Consultorio.nuevo("Consultorio Demo", "America/Argentina/Buenos_Aires");
        ReflectionTestUtils.setField(consultorio, "id", 1L);
        // Lunes de 09:00 a 13:00; granularidad 15 y anticipación 120 son los valores por defecto del consultorio.
        when(horarios.findByConsultorio_IdAndDiaSemana(anyLong(), eq((short) 1))).thenReturn(
                List.of(HorarioTrabajo.nuevo(consultorio, 1, LocalTime.of(9, 0), LocalTime.of(13, 0))));
        when(bloqueos.findByConsultorio_IdAndHastaGreaterThanAndDesdeLessThanOrderByDesdeAsc(anyLong(), any(),
                any())).thenReturn(List.of());
    }

    private ValidadorHorarioTurno validador(Instant ahora) {
        return new ValidadorHorarioTurno(horarios, bloqueos, Clock.fixed(ahora, ZoneOffset.UTC));
    }

    /** Lunes 2026-11-23 a la hora local indicada (UTC-3). */
    private static Instant lunes(String horaLocal) {
        return Instant.parse("2026-11-23T" + horaLocal + ":00-03:00");
    }

    private void assertInicioInvalido(Throwable e, String fragmento) {
        assertThat(e).isInstanceOf(ValidacionDominioException.class);
        ValidacionDominioException ex = (ValidacionDominioException) e;
        assertThat(ex.errores()).hasSize(1);
        assertThat(ex.errores().get(0).field()).isEqualTo("inicio");
        assertThat(ex.errores().get(0).message()).containsIgnoringCase(fragmento);
    }

    @Test
    void inicioDentroDelHorarioAlineadoYSinBloqueoEsValido() {
        assertThatCode(() -> validador(AHORA).validar(consultorio, lunes("10:00"), 30,
                PoliticaAnticipacion.odontologo())).doesNotThrowAnyException();
        assertThatCode(() -> validador(AHORA).validar(consultorio, lunes("12:30"), 30,
                PoliticaAnticipacion.odontologo())).as("el último hueco que termina justo a las 13:00")
                .doesNotThrowAnyException();
    }

    @Test
    void fueraDelHorarioDeTrabajo() {
        assertThatThrownBy(() -> validador(AHORA).validar(consultorio, lunes("12:45"), 30,
                PoliticaAnticipacion.odontologo())).satisfies(e -> assertInicioInvalido(e, "horario de trabajo"));
        assertThatThrownBy(() -> validador(AHORA).validar(consultorio, lunes("08:30"), 30,
                PoliticaAnticipacion.odontologo())).satisfies(e -> assertInicioInvalido(e, "horario de trabajo"));
    }

    @Test
    void diaSinHorarioEsFueraDelHorarioDeTrabajo() {
        Instant martes = Instant.parse("2026-11-24T10:00:00-03:00");

        assertThatThrownBy(() -> validador(AHORA).validar(consultorio, martes, 30,
                PoliticaAnticipacion.odontologo())).satisfies(e -> assertInicioInvalido(e, "horario de trabajo"));
    }

    @Test
    void elDiaSeCalculaEnLaZonaDelConsultorioYNoEnUtc() {
        // 2026-11-23T22:00-03:00 = martes 01:00Z; en Buenos Aires sigue siendo lunes (sin horario a esa hora).
        Instant lunesNoche = Instant.parse("2026-11-24T01:00:00Z");

        assertThatThrownBy(() -> validador(AHORA).validar(consultorio, lunesNoche, 30,
                PoliticaAnticipacion.odontologo())).satisfies(e -> assertInicioInvalido(e, "horario de trabajo"));
        verifyDiaConsultado();
    }

    private void verifyDiaConsultado() {
        org.mockito.Mockito.verify(horarios).findByConsultorio_IdAndDiaSemana(1L, (short) 1);
    }

    @Test
    void dentroDeUnBloqueo() {
        when(bloqueos.findByConsultorio_IdAndHastaGreaterThanAndDesdeLessThanOrderByDesdeAsc(anyLong(), any(),
                any())).thenReturn(List.of(BloqueoAgenda.nuevo(consultorio, lunes("10:00"), lunes("11:00"), "")));

        assertThatThrownBy(() -> validador(AHORA).validar(consultorio, lunes("10:30"), 30,
                PoliticaAnticipacion.odontologo())).satisfies(e -> assertInicioInvalido(e, "bloqueo"));
    }

    @Test
    void inicioNoAlineadoALaGranularidad() {
        assertThatThrownBy(() -> validador(AHORA).validar(consultorio, lunes("10:10"), 30,
                PoliticaAnticipacion.odontologo())).satisfies(e -> assertInicioInvalido(e, "granularidad"));
        assertThatThrownBy(() -> validador(AHORA).validar(consultorio, Instant.parse("2026-11-23T10:00:30-03:00"),
                30, PoliticaAnticipacion.odontologo())).satisfies(e -> assertInicioInvalido(e, "granularidad"));
    }

    @Test
    void inicioEnElPasado() {
        Instant ahoraLunes = Instant.parse("2026-11-23T11:00:00-03:00");

        assertThatThrownBy(() -> validador(ahoraLunes).validar(consultorio, lunes("10:00"), 30,
                PoliticaAnticipacion.odontologo())).satisfies(e -> assertInicioInvalido(e, "anterior al momento actual"));
    }

    @Test
    void odontologoNoEstaSujetoALaAnticipacionMinima() {
        // Ahora = lunes 09:30; el turno de las 10:00 queda a 30 min y la anticipación del consultorio es de 120.
        Instant ahora = Instant.parse("2026-11-23T09:30:00-03:00");

        assertThatCode(() -> validador(ahora).validar(consultorio, lunes("10:00"), 30,
                PoliticaAnticipacion.odontologo())).doesNotThrowAnyException();
    }

    @Test
    void politicaPublicaExigeLaAnticipacionMinima() {
        Instant ahora = Instant.parse("2026-11-23T09:30:00-03:00");

        assertThatThrownBy(() -> validador(ahora).validar(consultorio, lunes("10:00"), 30,
                PoliticaAnticipacion.publica(120))).satisfies(e -> assertInicioInvalido(e, "anticipaci"));
        assertThatCode(() -> validador(ahora).validar(consultorio, lunes("11:30"), 30,
                PoliticaAnticipacion.publica(120))).as("exactamente a 120 min").doesNotThrowAnyException();
    }
}
