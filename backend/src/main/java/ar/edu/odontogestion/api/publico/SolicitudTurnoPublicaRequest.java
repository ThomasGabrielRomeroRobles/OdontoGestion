package ar.edu.odontogestion.api.publico;

import ar.edu.odontogestion.api.paciente.PacienteRequest;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Solicitud pública de turno (C-09 D1). Mismos formatos que {@link PacienteRequest}. {@code sitioWeb} es el campo
 * señuelo (honeypot, D5): una persona no lo completa; es opcional.
 */
public record SolicitudTurnoPublicaRequest(
        @NotNull(message = "Es obligatorio.") Long prestacionId,
        @NotNull(message = "Es obligatorio.") Instant inicio,
        @NotBlank(message = "Es obligatorio.") @Size(max = 80, message = "Máximo 80 caracteres.") String nombre,
        @NotBlank(message = "Es obligatorio.") @Size(max = 80, message = "Máximo 80 caracteres.") String apellido,
        @NotBlank(message = "Es obligatorio.")
        @Pattern(regexp = PacienteRequest.PATRON_DNI, message = "Debe tener 7 u 8 dígitos.") String dni,
        @NotBlank(message = "Es obligatorio.") @Email(message = "No es un email válido.")
        @Size(max = 254, message = "Máximo 254 caracteres.") String email,
        @NotBlank(message = "Es obligatorio.") @Size(max = 40, message = "Máximo 40 caracteres.") String telefono,
        @Size(max = 200, message = "Máximo 200 caracteres.") String sitioWeb) {

    /** {@code @Email} rechaza los espacios alrededor: se recortan antes de validar. */
    public SolicitudTurnoPublicaRequest {
        email = email == null ? null : email.trim();
    }

    /** {@code true} si el señuelo trae contenido: la solicitud la envió un programa, no una persona. */
    public boolean senuelo() {
        return sitioWeb != null && !sitioWeb.isBlank();
    }

    public DatosPaciente aDatos() {
        return new DatosPaciente(nombre, apellido, dni, null, telefono, email, null);
    }
}
