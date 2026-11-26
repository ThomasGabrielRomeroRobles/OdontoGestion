package ar.edu.odontogestion.application.historia;

import java.util.List;

/** Ficha clínica completa: anamnesis, las 32 piezas en orden FDI y las evoluciones en orden cronológico. */
public record HistoriaVista(PacienteResumen paciente, AnamnesisVista anamnesis, List<PiezaVista> piezas,
        List<EvolucionVista> evoluciones) {
}
