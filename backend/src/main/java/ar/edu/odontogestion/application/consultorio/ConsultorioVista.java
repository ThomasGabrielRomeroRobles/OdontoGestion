package ar.edu.odontogestion.application.consultorio;

import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;

/** Vista de aplicación del consultorio: ninguna entidad JPA sale del service. */
public record ConsultorioVista(
        String nombre,
        String direccion,
        String telefono,
        String emailContacto,
        String zonaHoraria,
        ParametrosConsultorio parametros) {
}
