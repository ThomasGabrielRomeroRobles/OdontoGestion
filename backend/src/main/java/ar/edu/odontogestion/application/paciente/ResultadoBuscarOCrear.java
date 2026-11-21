package ar.edu.odontogestion.application.paciente;

/**
 * Resultado de {@link PacienteService#buscarOCrearPorDni}: el paciente y si fue creado en esta llamada
 * ({@code false} si ya existía y se reutilizó sin modificarlo, RN-PU-04).
 */
public record ResultadoBuscarOCrear(PacienteVista paciente, boolean creado) {
}
