package ar.edu.odontogestion.application.historia;

import ar.edu.odontogestion.domain.historia.EstadoPieza;
import ar.edu.odontogestion.domain.historia.PiezaDentalEstado;
import java.time.Instant;

/** Pieza del odontograma; {@code estado} nulo si nunca se registró. */
public record PiezaVista(int fdi, EstadoPieza estado, String observacion, AutorVista actualizadoPor,
        Instant actualizadoEn) {

    static PiezaVista sinRegistrar(int fdi) {
        return new PiezaVista(fdi, null, "", null, null);
    }

    static PiezaVista desde(PiezaDentalEstado p) {
        return new PiezaVista(p.getPiezaFdi(), p.getEstado(), p.getObservacion(),
                AutorVista.desde(p.getActualizadoPor()), p.getActualizadoEn());
    }
}
