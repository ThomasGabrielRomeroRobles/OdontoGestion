package ar.edu.odontogestion.domain.notificacion;

/** Tipos de notificación a pacientes (KB 12, RN-NO). Coincide con el {@code CHECK} de V6. */
public enum TipoNotificacion {
    CONFIRMAR_SOLICITUD,
    CONFIRMAR_LISTA_ESPERA,
    CONFIRMACION_TURNO,
    RECORDATORIO,
    AVISO_HUECO,
    CANCELACION,
    AUSENTE_REPROGRAMAR
}
