package ar.edu.odontogestion.api.perfil;

import ar.edu.odontogestion.application.perfil.DatosPerfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** El email no se edita: si viene en el cuerpo, se ignora. */
public record PerfilRequest(
        @NotBlank(message = "Es obligatorio.") @Size(max = 80, message = "Máximo 80 caracteres.") String nombre,
        @NotBlank(message = "Es obligatorio.") @Size(max = 80, message = "Máximo 80 caracteres.") String apellido,
        @NotNull(message = "Es obligatorio.") @Size(max = 40, message = "Máximo 40 caracteres.") String matricula) {

    public DatosPerfil aDatos() {
        return new DatosPerfil(nombre, apellido, matricula);
    }
}
