package ar.edu.odontogestion.application.paciente;

import ar.edu.odontogestion.domain.paciente.Paciente;
import java.time.LocalDate;

/** Ficha administrativa del paciente que devuelven los casos de uso (nunca la entidad). */
public record PacienteVista(
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

    static PacienteVista desde(Paciente p) {
        return new PacienteVista(p.getId(), p.getNombre(), p.getApellido(), p.getDni(), p.getFechaNacimiento(),
                p.getTelefono(), p.getEmail(), p.getDireccion(), p.getCantidadAusencias(), p.isActivo());
    }
}
