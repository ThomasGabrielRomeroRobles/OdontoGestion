package ar.edu.odontogestion.api.historia;

import ar.edu.odontogestion.domain.historia.DatosAnamnesis;
import jakarta.validation.constraints.Size;

/** Reemplazo completo de la anamnesis: los campos omitidos quedan vacíos; embarazo es opcional. */
public record AnamnesisRequest(
        @Size(max = 2000) String alergias,
        @Size(max = 2000) String medicacionActual,
        @Size(max = 2000) String enfermedadesPrevias,
        @Size(max = 2000) String antecedentesRelevantes,
        Boolean embarazo,
        @Size(max = 2000) String observaciones) {

    DatosAnamnesis aDatos() {
        return new DatosAnamnesis(alergias, medicacionActual, enfermedadesPrevias, antecedentesRelevantes, embarazo,
                observaciones);
    }
}
