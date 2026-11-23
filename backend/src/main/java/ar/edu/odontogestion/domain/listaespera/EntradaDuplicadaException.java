package ar.edu.odontogestion.domain.listaespera;

import ar.edu.odontogestion.domain.error.ConflictoException;

/** El paciente ya tiene una entrada vigente de esa prestación (409); la API pública la oculta (DD-14). */
public class EntradaDuplicadaException extends ConflictoException {

    public EntradaDuplicadaException() {
        super("Ya existe una inscripción vigente para esa prestación.");
    }
}
