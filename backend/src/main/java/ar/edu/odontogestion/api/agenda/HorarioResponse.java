package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.HorarioVista;

/** {@code diaSemana}: 1 = lunes … 7 = domingo; horas {@code HH:mm} en hora local del consultorio. */
public record HorarioResponse(Long id, int diaSemana, String horaDesde, String horaHasta) {

    static HorarioResponse from(HorarioVista v) {
        return new HorarioResponse(v.id(), v.diaSemana(), v.horaDesde(), v.horaHasta());
    }
}
