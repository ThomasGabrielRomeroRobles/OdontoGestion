package ar.edu.odontogestion.application.historia;

/** Identificación mínima del paciente en el encabezado de la ficha (sin DNI ni contacto). */
public record PacienteResumen(Long id, String nombre, String apellido) {
}
