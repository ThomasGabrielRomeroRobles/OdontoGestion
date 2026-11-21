package ar.edu.odontogestion.application.consultorio;

import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;

/** Comando de actualización del consultorio. La zona horaria no se puede modificar. */
public record DatosConsultorio(
        String nombre,
        String direccion,
        String telefono,
        String emailContacto,
        ParametrosConsultorio parametros) {
}
