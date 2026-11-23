package ar.edu.odontogestion.api.paciente;

import ar.edu.odontogestion.application.paciente.HistorialAusencias;
import java.time.Instant;
import java.util.List;

/** {@code {cantidad, ausencias: [{turnoId, inicio, prestacionNombre}]}}, la más reciente primero (US-061). */
public record AusenciasPacienteResponse(int cantidad, List<Ausencia> ausencias) {

    public record Ausencia(long turnoId, Instant inicio, String prestacionNombre) {
    }

    static AusenciasPacienteResponse from(HistorialAusencias h) {
        return new AusenciasPacienteResponse(h.cantidad(), h.ausencias().stream()
                .map(a -> new Ausencia(a.turnoId(), a.inicio(), a.prestacionNombre())).toList());
    }
}
