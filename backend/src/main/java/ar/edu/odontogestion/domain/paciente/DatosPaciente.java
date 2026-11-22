package ar.edu.odontogestion.domain.paciente;

import java.time.LocalDate;

/**
 * Datos administrativos de un paciente tal como llegan (sin normalizar). Lo usan el alta, la edición y
 * {@code buscarOCrearPorDni}; {@link Paciente} los normaliza. {@code fechaNacimiento} y {@code direccion} son
 * opcionales ({@code null}).
 */
public record DatosPaciente(
        String nombre,
        String apellido,
        String dni,
        LocalDate fechaNacimiento,
        String telefono,
        String email,
        String direccion) {
}
