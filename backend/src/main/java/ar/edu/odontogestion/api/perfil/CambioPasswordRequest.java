package ar.edu.odontogestion.api.perfil;

import jakarta.validation.constraints.NotBlank;

/** La política de la contraseña nueva se valida en el servicio (422 con campo). */
public record CambioPasswordRequest(
        @NotBlank(message = "Es obligatoria.") String passwordActual,
        @NotBlank(message = "Es obligatoria.") String passwordNueva) {

    @Override
    public String toString() {
        return "CambioPasswordRequest[passwordActual=****, passwordNueva=****]";
    }
}
