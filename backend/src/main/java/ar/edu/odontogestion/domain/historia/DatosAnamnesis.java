package ar.edu.odontogestion.domain.historia;

/** Contenido de la anamnesis (texto simple, KB 04). Los nulos se guardan como texto vacío; embarazo es opcional. */
public record DatosAnamnesis(String alergias, String medicacionActual, String enfermedadesPrevias,
        String antecedentesRelevantes, Boolean embarazo, String observaciones) {
}
