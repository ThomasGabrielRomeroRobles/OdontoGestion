package ar.edu.odontogestion.domain.turno.evento;

/** El paciente confirmó su solicitud pública: el turno pasó de {@code PENDIENTE_CONFIRMACION} a {@code RESERVADO}. */
public record SolicitudConfirmada(long turnoId) {
}
