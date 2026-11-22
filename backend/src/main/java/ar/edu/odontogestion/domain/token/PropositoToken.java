package ar.edu.odontogestion.domain.token;

/** Propósito único de un token de acceso; define a qué tipo de referencia puede apuntar. */
public enum PropositoToken {
    CONFIRMAR_SOLICITUD(ReferenciaToken.Turno.class),
    CONFIRMAR_TURNO(ReferenciaToken.Turno.class),
    CANCELAR_TURNO(ReferenciaToken.Turno.class),
    ACEPTAR_AVISO(ReferenciaToken.Aviso.class),
    CONFIRMAR_LISTA_ESPERA(ReferenciaToken.EntradaListaEspera.class);

    private final Class<? extends ReferenciaToken> referencia;

    PropositoToken(Class<? extends ReferenciaToken> referencia) {
        this.referencia = referencia;
    }

    public Class<? extends ReferenciaToken> referenciaRequerida() {
        return referencia;
    }

    public boolean admite(ReferenciaToken candidata) {
        return referencia.isInstance(candidata);
    }
}
