package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.domain.agenda.HorarioTrabajo;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Lectura de una franja para la capa API; las horas viajan como {@code HH:mm} en hora local del consultorio. */
public record HorarioVista(Long id, int diaSemana, String horaDesde, String horaHasta) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    static HorarioVista de(HorarioTrabajo h) {
        return new HorarioVista(h.getId(), h.getDiaSemana(), formatear(h.getHoraDesde()), formatear(h.getHoraHasta()));
    }

    static String formatear(LocalTime hora) {
        return hora.format(HH_MM);
    }
}
