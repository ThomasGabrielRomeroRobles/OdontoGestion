package ar.edu.odontogestion.domain.listaespera;

import java.time.Instant;

/** Clave del hueco de un aviso: {@code (turno_origen_id, inicio)}. Proyección que no carga la entidad. */
public record HuecoDeAviso(Long turnoOrigenId, Instant inicio) {
}
