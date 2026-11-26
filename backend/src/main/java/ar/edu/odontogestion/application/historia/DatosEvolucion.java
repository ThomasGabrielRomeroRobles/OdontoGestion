package ar.edu.odontogestion.application.historia;

import java.time.LocalDate;

/** Alta o edición de una evolución: {@code fecha} nula = hoy (alta) o sin cambio (edición); turno opcional. */
public record DatosEvolucion(String nota, LocalDate fecha, Long turnoId) {
}
