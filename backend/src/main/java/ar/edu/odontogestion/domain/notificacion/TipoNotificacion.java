package ar.edu.odontogestion.domain.notificacion;

import ar.edu.odontogestion.domain.token.ReferenciaToken;

/**
 * Tipos de notificación a pacientes (KB 12, RN-NO). Coincide con el {@code CHECK} de V6, incluida la
 * coherencia con la referencia: {@code AVISO_HUECO} → aviso, {@code CONFIRMAR_LISTA_ESPERA} → entrada de lista
 * de espera, el resto → turno.
 */
public enum TipoNotificacion {
    CONFIRMAR_SOLICITUD(ReferenciaToken.Turno.class),
    CONFIRMAR_LISTA_ESPERA(ReferenciaToken.EntradaListaEspera.class),
    CONFIRMACION_TURNO(ReferenciaToken.Turno.class),
    RECORDATORIO(ReferenciaToken.Turno.class),
    AVISO_HUECO(ReferenciaToken.Aviso.class),
    CANCELACION(ReferenciaToken.Turno.class),
    AUSENTE_REPROGRAMAR(ReferenciaToken.Turno.class);

    private final Class<? extends ReferenciaToken> referencia;

    TipoNotificacion(Class<? extends ReferenciaToken> referencia) {
        this.referencia = referencia;
    }

    public Class<? extends ReferenciaToken> referenciaRequerida() {
        return referencia;
    }

    public boolean admite(ReferenciaToken candidata) {
        return referencia.isInstance(candidata);
    }
}
