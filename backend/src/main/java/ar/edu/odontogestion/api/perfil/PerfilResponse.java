package ar.edu.odontogestion.api.perfil;

import ar.edu.odontogestion.application.perfil.PerfilVista;

public record PerfilResponse(String email, String nombre, String apellido, String matricula) {

    public static PerfilResponse from(PerfilVista v) {
        return new PerfilResponse(v.email(), v.nombre(), v.apellido(), v.matricula());
    }
}
