package ar.edu.odontogestion.domain.listaespera;

import ar.edu.odontogestion.domain.error.TokenInvalidoException;

/**
 * El aviso venció ({@code vence_en <= ahora}). Responde {@code 410} como cualquier enlace inválido; lleva el id para
 * que {@code AvisoComandos} lo marque {@code EXPIRADO} en una transacción nueva. No contiene el token.
 */
public class AvisoVencidoException extends TokenInvalidoException {

    private final long avisoId;

    public AvisoVencidoException(long avisoId) {
        this.avisoId = avisoId;
    }

    public long avisoId() {
        return avisoId;
    }
}
