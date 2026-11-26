package ar.edu.odontogestion.application.historia;

import java.time.Instant;
import java.time.LocalDate;

/** Nota de evolución; {@code editable} aplica la misma regla que la edición (autor + ventana, design D5). */
public record EvolucionVista(Long id, LocalDate fecha, String nota, Long turnoId, AutorVista autor,
        Instant creadaEn, Instant editadaEn, boolean editable) {
}
