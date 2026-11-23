package ar.edu.odontogestion.api.paciente;

import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Alta y edición de paciente. El formato se valida acá ({@code 400} con campo, design D8). {@code cantidadAusencias}
 * y {@code activo} no se aceptan: las propiedades desconocidas se ignoran.
 */
public record PacienteRequest(
        @NotBlank(message = "Es obligatorio.") @Size(max = 80, message = "Máximo 80 caracteres.") String nombre,
        @NotBlank(message = "Es obligatorio.") @Size(max = 80, message = "Máximo 80 caracteres.") String apellido,
        @NotBlank(message = "Es obligatorio.")
        @Pattern(regexp = PacienteRequest.PATRON_DNI, message = "Debe tener 7 u 8 dígitos.") String dni,
        @PastOrPresent(message = "No puede ser una fecha futura.") LocalDate fechaNacimiento,
        @NotBlank(message = "Es obligatorio.") @Size(max = 40, message = "Máximo 40 caracteres.") String telefono,
        @NotBlank(message = "Es obligatorio.") @Email(message = "No es un email válido.")
        @Size(max = 254, message = "Máximo 254 caracteres.") String email,
        @Size(max = 200, message = "Máximo 200 caracteres.") String direccion) {

    /** {@code @Email} rechaza los espacios alrededor: se recortan antes de validar (el dominio normaliza el resto). */
    public PacienteRequest {
        email = email == null ? null : email.trim();
    }

    /** 7 u 8 dígitos, con puntos de miles opcionales (30.123.456 / 7.123.456). */
    public static final String PATRON_DNI = "^\\s*[0-9]{1,2}\\.?[0-9]{3}\\.?[0-9]{3}\\s*$";

    public DatosPaciente aDatos() {
        return new DatosPaciente(nombre, apellido, dni, fechaNacimiento, telefono, email, direccion);
    }
}
