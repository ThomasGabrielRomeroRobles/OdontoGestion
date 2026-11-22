package ar.edu.odontogestion.application.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Usa el horario del seed: lunes a viernes 09:00-13:00 y 14:00-18:00, granularidad de 15 minutos. */
class DisponibilidadServiceIT extends AbstractTurnoIT {

    @Autowired
    private DisponibilidadService disponibilidad;

    private List<Hueco> lunes(Long prestacion) {
        return disponibilidad.huecosLibres(prestacion, LUNES, LUNES);
    }

    private static List<Instant> inicios(List<Hueco> huecos) {
        return huecos.stream().map(Hueco::inicio).toList();
    }

    @Test
    void unDiaSinTurnosTieneLosHuecosDeLasDosFranjas() {
        List<Hueco> huecos = lunes(crearPrestacion(30));

        // 09:00-13:00 con huecos de 30 cada 15 = 15; 14:00-18:00 = 15.
        assertThat(huecos).hasSize(30);
        assertThat(huecos.get(0)).isEqualTo(new Hueco(lunes("09:00"), lunes("09:30")));
        assertThat(inicios(huecos)).contains(lunes("12:30"), lunes("14:00")).doesNotContain(lunes("12:45"),
                lunes("13:00"), lunes("13:45"));
        assertThat(huecos).isSortedAccordingTo(java.util.Comparator.comparing(Hueco::inicio));
    }

    @Test
    void turnosQueOcupanAgendaYBloqueosDescuentanHuecos() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.RESERVADO, lunes("09:00"), lunes("09:30"));
        jdbc.update("INSERT INTO bloqueo_agenda (consultorio_id, desde, hasta, motivo) VALUES (?, ?::timestamptz, "
                + "?::timestamptz, 'IT-Turno bloqueo')", consultorioId(), lunes("10:00").toString(),
                lunes("13:00").toString());

        List<Instant> manana = inicios(lunes(prestacion)).stream().filter(i -> i.isBefore(lunes("13:00"))).toList();

        assertThat(manana).containsExactly(lunes("09:30"));
    }

    @Test
    void atendidoYAusenteTambienOcupanElRango() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.ATENDIDO, lunes("09:00"), lunes("09:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.AUSENTE, lunes("09:30"), lunes("10:00"));

        assertThat(inicios(lunes(prestacion))).doesNotContain(lunes("09:00"), lunes("09:15"), lunes("09:30"),
                lunes("09:45")).contains(lunes("10:00"));
    }

    @Test
    void canceladosYPendientesVencidasNoOcupan() {
        Long paciente = crearPaciente();
        Long prestacion = crearPrestacion(30);
        insertarTurno(paciente, prestacion, EstadoTurno.CANCELADO, lunes("09:00"), lunes("09:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("09:30"), lunes("10:00"),
                reloj.instant().minusSeconds(60));
        insertarTurno(paciente, prestacion, EstadoTurno.EXPIRADO, lunes("10:00"), lunes("10:30"));
        insertarTurno(paciente, prestacion, EstadoTurno.PENDIENTE_CONFIRMACION, lunes("11:00"), lunes("11:30"),
                reloj.instant().plusSeconds(600));

        List<Instant> inicios = inicios(lunes(prestacion));

        assertThat(inicios).contains(lunes("09:00"), lunes("09:30"), lunes("10:00"));
        assertThat(inicios).as("la pendiente vigente retiene el hueco").doesNotContain(lunes("11:00"),
                lunes("11:15"));
    }

    @Test
    void unDiaSinHorarioDeTrabajoDevuelveVacio() {
        // Sábado 2026-11-28 y domingo.
        assertThat(disponibilidad.huecosLibres(crearPrestacion(30), LUNES.plusDays(5), LUNES.plusDays(6))).isEmpty();
    }

    @Test
    void losHuecosPasadosSeExcluyenConLaPoliticaDelOdontologo() {
        reloj.set(lunes("11:07"));

        List<Instant> inicios = inicios(lunes(crearPrestacion(30)));

        assertThat(inicios).doesNotContain(lunes("09:00"), lunes("11:00")).contains(lunes("11:15"));
        assertThat(inicios).allSatisfy(i -> assertThat(i).isAfterOrEqualTo(reloj.instant()));
    }

    @Test
    void elOdontologoVeHuecosDentroDeLaAnticipacionMinimaDeLosPacientes() {
        reloj.set(lunes("09:30"));

        // El consultorio exige 120 min de anticipación a los pacientes (no al odontólogo).
        assertThat(inicios(lunes(crearPrestacion(30)))).contains(lunes("09:30"), lunes("10:00"));
    }

    @Test
    void ventanaDeVariosDiasOrdenadaPorInicio() {
        List<Hueco> huecos = disponibilidad.huecosLibres(crearPrestacion(60), LUNES, LUNES.plusDays(1));

        assertThat(huecos.get(0).inicio()).isEqualTo(lunes("09:00"));
        assertThat(huecos.get(huecos.size() - 1).inicio()).isEqualTo(dia(1, "17:00"));
        assertThat(huecos).isSortedAccordingTo(java.util.Comparator.comparing(Hueco::inicio));
    }

    @Test
    void prestacionInactivaDa422YInexistenteODeOtroConsultorioDa404() {
        Long inactiva = crearPrestacionInactiva(30);
        Long ajena = crearPrestacion(crearOtroConsultorio(), 30, true);

        assertThatThrownBy(() -> lunes(inactiva)).isInstanceOfSatisfying(ValidacionDominioException.class,
                e -> assertThat(e.errores().get(0).field()).isEqualTo("prestacionId"));
        assertThatThrownBy(() -> lunes(inactiva + 100_000)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> lunes(ajena)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void rangoInvertidoOMayorA31DiasDa422PorCampo() {
        Long prestacion = crearPrestacion(30);

        assertThatThrownBy(() -> disponibilidad.huecosLibres(prestacion, LUNES.plusDays(1), LUNES))
                .isInstanceOfSatisfying(ValidacionDominioException.class,
                        e -> assertThat(e.errores().get(0).field()).isEqualTo("hasta"));
        assertThatThrownBy(() -> disponibilidad.huecosLibres(prestacion, LUNES, LUNES.plusDays(31)))
                .isInstanceOfSatisfying(ValidacionDominioException.class,
                        e -> assertThat(e.errores().get(0).field()).isEqualTo("hasta"));
        assertThat(disponibilidad.huecosLibres(prestacion, LUNES, LUNES.plusDays(30))).isNotEmpty();
    }
}
