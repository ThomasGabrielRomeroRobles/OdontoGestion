package ar.edu.odontogestion.api.paciente;

import ar.edu.odontogestion.application.paciente.PacienteVista;
import java.time.LocalDate;

/** Ficha administrativa del paciente (design D8): exactamente estos diez campos. */
public record PacienteResponse(
        Long id,
        String nombre,
        String apellido,
        String dni,
        LocalDate fechaNacimiento,
        String telefono,
        String email,
        String direccion,
        int cantidadAusencias,
        boolean activo) {

    public static PacienteResponse from(PacienteVista v) {
        return new PacienteResponse(v.id(), v.nombre(), v.apellido(), v.dni(), v.fechaNacimiento(), v.telefono(),
                v.email(), v.direccion(), v.cantidadAusencias(), v.activo());
    }
}
