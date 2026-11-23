package ar.edu.odontogestion.domain.turno.evento;

/** Se marcó un turno como ausente (C-08). No libera hueco: el turno sigue ocupando su rango (RN-AS-04). */
public record TurnoAusente(long turnoId) {
}
