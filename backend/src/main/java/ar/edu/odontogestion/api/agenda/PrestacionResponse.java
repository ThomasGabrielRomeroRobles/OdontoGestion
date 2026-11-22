package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.PrestacionVista;

public record PrestacionResponse(Long id, String nombre, int duracionMin, boolean activa) {

    static PrestacionResponse from(PrestacionVista v) {
        return new PrestacionResponse(v.id(), v.nombre(), v.duracionMin(), v.activa());
    }
}
