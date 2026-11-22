package ar.edu.odontogestion.api.turno;

import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/turnos/{id}/cancelar}: motivo opcional de hasta 200 caracteres (el cuerpo entero es opcional). */
public record CancelarTurnoRequest(@Size(max = 200) String motivo) {
}
