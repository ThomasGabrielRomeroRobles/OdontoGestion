package ar.edu.odontogestion.application.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.agenda.PoliticaAnticipacion;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Disponibilidad pública (C-09 D3). "Ahora" = viernes 2026-11-20 09:00 BA y la anticipación del seed es de 120
 * minutos, así que ese viernes el primer hueco público es el de las 11:00.
 */
class DisponibilidadPublicaIT extends AbstractTurnoIT {

    private static final LocalDate VIERNES = LUNES.minusDays(3);

    @Autowired
    private DisponibilidadService disponibilidad;

    private static List<Instant> inicios(List<Hueco> huecos) {
        return huecos.stream().map(Hueco::inicio).toList();
    }

    private List<Hueco> viernesPublico(Long prestacion) {
        return disponibilidad.huecosLibresPublicos(prestacion, VIERNES, VIERNES);
    }

    @Test
    void laAnticipacionPublicaExcluyeLosHuecosCercanosEIncluyeElLimiteExacto() {
        Long prestacion = crearPrestacion(30);

        List<Instant> publicos = inicios(viernesPublico(prestacion));
        List<Instant> delOdontologo = inicios(disponibilidad.huecosLibres(prestacion, VIERNES, VIERNES));

        assertThat(publicos).doesNotContain(dia(-3, "10:00"), dia(-3, "10:30"), dia(-3, "10:45"))
                .contains(dia(-3, "11:00"));
        assertThat(delOdontologo).as("el odontólogo no tiene anticipación").contains(dia(-3, "10:00"));
    }

    @Test
    void sinCercaniaDeHorarioLaDisponibilidadPublicaEsLaMismaQueLaDelOdontologo() {
        Long prestacion = crearPrestacion(30);

        assertThat(disponibilidad.huecosLibresPublicos(prestacion, LUNES, LUNES))
                .isEqualTo(disponibilidad.huecosLibres(prestacion, LUNES, LUNES)).hasSize(30);
    }

    @Test
    void pendienteVigenteOcupaYVencidaNo() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("11:00"), lunes("11:30"),
                reloj.instant().plusSeconds(600));
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("12:00"), lunes("12:30"),
                reloj.instant().minusSeconds(60));
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("10:00"), lunes("10:30"));

        List<Instant> inicios = inicios(disponibilidad.huecosLibresPublicos(prestacion, LUNES, LUNES));

        assertThat(inicios).doesNotContain(lunes("10:00"), lunes("11:00"), lunes("11:15"))
                .contains(lunes("12:00"), lunes("10:30"));
    }

    @Test
    void prestacionInactivaYRangoInvalidoDan422() {
        Long inactiva = crearPrestacionInactiva(30);
        Long activa = crearPrestacion(30);

        assertThatThrownBy(() -> disponibilidad.huecosLibresPublicos(inactiva, LUNES, LUNES))
                .isInstanceOf(ValidacionDominioException.class);
        assertThatThrownBy(() -> disponibilidad.huecosLibresPublicos(activa, LUNES, LUNES.plusDays(40)))
                .isInstanceOf(ValidacionDominioException.class);
        assertThatThrownBy(() -> disponibilidad.huecosLibresPublicos(activa, LUNES.plusDays(2), LUNES))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void prestacionInexistenteOdeOtroConsultorioDa404() {
        Long ajena = crearPrestacion(crearOtroConsultorio(), 30, true);

        assertThatThrownBy(() -> disponibilidad.huecosLibresPublicos(999_999_999L, LUNES, LUNES))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> disponibilidad.huecosLibresPublicos(ajena, LUNES, LUNES))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void alternativasConPoliticaPublicaExcluyenHuecosAntesDeLaAnticipacion() {
        Long prestacion = crearPrestacion(30);
        Instant desde = dia(-3, "09:00");

        List<Instant> publicas = inicios(disponibilidad.alternativas(prestacion, desde, 3,
                PoliticaAnticipacion.publica(120)));
        List<Instant> delOdontologo = inicios(disponibilidad.alternativas(prestacion, desde, 3));

        assertThat(publicas).containsExactly(dia(-3, "11:00"), dia(-3, "11:15"), dia(-3, "11:30"));
        assertThat(delOdontologo).as("la sobrecarga existente no cambia").containsExactly(dia(-3, "09:00"),
                dia(-3, "09:15"), dia(-3, "09:30"));
    }
}
