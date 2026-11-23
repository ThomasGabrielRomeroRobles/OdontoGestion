package ar.edu.odontogestion.application.listaespera;

import ar.edu.odontogestion.domain.listaespera.EntradaListaEspera;
import ar.edu.odontogestion.domain.listaespera.EstadoEntrada;
import java.time.Instant;
import java.time.LocalDate;

/** Entrada de la lista de espera tal como la ve el odontólogo: sin DNI, email ni teléfono (regla dura #10). */
public record EntradaListaEsperaVista(Long id, String paciente, String prestacion, LocalDate fechaDesde,
        LocalDate fechaHasta, EstadoEntrada estado, Instant creadoEn) {

    static EntradaListaEsperaVista de(EntradaListaEspera e) {
        return new EntradaListaEsperaVista(e.getId(), e.getPaciente().getApellido() + ", " + e.getPaciente().getNombre(),
                e.getPrestacion().getNombre(), e.getFechaDesde(), e.getFechaHasta(), e.getEstado(), e.getCreadoEn());
    }
}
