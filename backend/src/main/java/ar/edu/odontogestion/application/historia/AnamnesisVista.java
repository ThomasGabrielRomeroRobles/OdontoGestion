package ar.edu.odontogestion.application.historia;

import ar.edu.odontogestion.domain.historia.Anamnesis;
import java.time.Instant;

/** Anamnesis tal como se muestra; vacía (textos '' y autor/fecha nulos) si todavía no se guardó. */
public record AnamnesisVista(String alergias, String medicacionActual, String enfermedadesPrevias,
        String antecedentesRelevantes, Boolean embarazo, String observaciones, AutorVista actualizadoPor,
        Instant actualizadoEn) {

    static final AnamnesisVista VACIA = new AnamnesisVista("", "", "", "", null, "", null, null);

    static AnamnesisVista desde(Anamnesis a) {
        return new AnamnesisVista(a.getAlergias(), a.getMedicacionActual(), a.getEnfermedadesPrevias(),
                a.getAntecedentesRelevantes(), a.getEmbarazo(), a.getObservaciones(),
                AutorVista.desde(a.getActualizadoPor()), a.getActualizadoEn());
    }
}
