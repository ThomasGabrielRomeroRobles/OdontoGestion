package ar.edu.odontogestion.domain.notificacion;

/** Estado de una notificación del outbox. Coincide con el {@code CHECK} de V6. */
public enum EstadoNotificacion {
    PENDIENTE,
    ENVIADA,
    FALLIDA
}
