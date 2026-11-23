package ar.edu.odontogestion.application.paciente;

import java.time.Instant;
import java.util.List;

/**
 * Historial de ausencias de un paciente (US-061, C-08 design D6).
 *
 * @param cantidad  contador oficial del paciente (RN-AS-02)
 * @param ausencias turnos {@code AUSENTE}, del más reciente al más antiguo
 */
public record HistorialAusencias(int cantidad, List<Ausencia> ausencias) {

    public HistorialAusencias {
        ausencias = List.copyOf(ausencias);
    }

    public record Ausencia(long turnoId, Instant inicio, String prestacionNombre) {
    }
}
