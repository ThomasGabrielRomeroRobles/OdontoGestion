package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.domain.agenda.Prestacion;

/** Lectura de una prestación para la capa API (nunca se expone la entidad). */
public record PrestacionVista(Long id, String nombre, int duracionMin, boolean activa) {

    static PrestacionVista de(Prestacion p) {
        return new PrestacionVista(p.getId(), p.getNombre(), p.getDuracionMin(), p.isActiva());
    }
}
