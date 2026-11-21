package ar.edu.odontogestion.api.consultorio;

import ar.edu.odontogestion.application.consultorio.DatosConsultorio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Edición del consultorio. El formato se valida acá (400); la positividad de los parámetros, en el servicio
 * (422 con campo). {@code zonaHoraria} no se acepta: las propiedades desconocidas se ignoran.
 */
public record ConsultorioRequest(
        @NotBlank(message = "Es obligatorio.") @Size(max = 120, message = "Máximo 120 caracteres.") String nombre,
        @NotNull(message = "Es obligatorio.") @Size(max = 200, message = "Máximo 200 caracteres.") String direccion,
        @NotNull(message = "Es obligatorio.") @Size(max = 40, message = "Máximo 40 caracteres.") String telefono,
        @NotNull(message = "Es obligatorio.") @Email(message = "No es un email válido.")
        @Size(max = 254, message = "Máximo 254 caracteres.") String emailContacto,
        @NotNull(message = "Es obligatorio.") @Valid ParametrosRequest parametros) {

    public DatosConsultorio aDatos() {
        return new DatosConsultorio(nombre, direccion, telefono, emailContacto, parametros.aParametros());
    }
}
