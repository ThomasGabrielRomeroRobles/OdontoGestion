package ar.edu.odontogestion.application.listaespera;

import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import java.time.LocalDate;

/** Lo que el servicio necesita de una inscripción pública validada: prestación, rango de fechas y contacto. */
public record DatosInscripcion(Long prestacionId, LocalDate fechaDesde, LocalDate fechaHasta,
        DatosPaciente paciente) {
}
