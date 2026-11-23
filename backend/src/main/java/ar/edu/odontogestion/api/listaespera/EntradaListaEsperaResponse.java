package ar.edu.odontogestion.api.listaespera;

import ar.edu.odontogestion.application.listaespera.EntradaListaEsperaVista;
import ar.edu.odontogestion.domain.listaespera.EstadoEntrada;
import java.time.Instant;
import java.time.LocalDate;

/** Fila del listado del odontólogo (C-10 D7): paciente como "Apellido, Nombre"; nunca DNI ni contacto. */
public record EntradaListaEsperaResponse(Long id, String paciente, String prestacion, LocalDate fechaDesde,
        LocalDate fechaHasta, EstadoEntrada estado, Instant creadoEn) {

    static EntradaListaEsperaResponse de(EntradaListaEsperaVista v) {
        return new EntradaListaEsperaResponse(v.id(), v.paciente(), v.prestacion(), v.fechaDesde(), v.fechaHasta(),
                v.estado(), v.creadoEn());
    }
}
