package ar.edu.odontogestion.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Los límites de tamaño evitan someter a BCrypt o al limitador entradas gigantes. */
public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 1024) String password) {

    /** Evita que la contraseña llegue a logs si alguien imprime el request. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****]";
    }
}
