package ar.edu.odontogestion.api.consultorio;

import ar.edu.odontogestion.application.consultorio.ConsultorioVista;

/** Datos del consultorio (sin id) y sus parámetros anidados. Los opcionales viajan como {@code ""}, nunca null. */
public record ConsultorioResponse(
        String nombre,
        String direccion,
        String telefono,
        String emailContacto,
        String zonaHoraria,
        ParametrosResponse parametros) {

    public static ConsultorioResponse from(ConsultorioVista v) {
        return new ConsultorioResponse(v.nombre(), v.direccion(), v.telefono(), v.emailContacto(),
                v.zonaHoraria(), ParametrosResponse.from(v.parametros()));
    }
}
