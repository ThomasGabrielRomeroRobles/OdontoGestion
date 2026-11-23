package ar.edu.odontogestion.application.listaespera;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import java.time.Instant;

/** Lo que ve el paciente que se quedó con el turno (C-10 D5): sin ids ni datos personales. */
public record AvisoAceptadoResponse(String prestacion, Instant inicio, Instant fin, EstadoTurno estado) {
}
