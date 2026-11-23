package ar.edu.odontogestion.application.publico;

import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import java.time.Instant;

/** Lo que el servicio necesita de una solicitud pública validada: prestación, inicio y datos de contacto. */
public record DatosSolicitudPublica(Long prestacionId, Instant inicio, DatosPaciente paciente) {
}
