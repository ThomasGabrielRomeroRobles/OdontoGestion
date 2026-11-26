package ar.edu.odontogestion.application.historia;

import ar.edu.odontogestion.domain.usuario.Usuario;

/** Autor de un dato clínico: id y nombre visible. Nunca el email (C-05 design D4). */
public record AutorVista(Long id, String nombre) {

    static AutorVista desde(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return new AutorVista(usuario.getId(), usuario.getNombre() + " " + usuario.getApellido());
    }
}
