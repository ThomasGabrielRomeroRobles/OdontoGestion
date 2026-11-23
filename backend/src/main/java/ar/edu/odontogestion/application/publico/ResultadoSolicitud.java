package ar.edu.odontogestion.application.publico;

/**
 * Qué pasó con una solicitud pública aceptada. La API responde lo mismo en ambos casos (RN-PU-07, sin
 * enumeración); la distinción es solo para pruebas y para el servicio.
 */
public enum ResultadoSolicitud {
    /** Se creó el turno {@code PENDIENTE_CONFIRMACION} y se encoló el mail de confirmación. */
    PENDIENTE_CREADA,
    /** El paciente con ese DNI está dado de baja: no se reservó nada ni se notificó. */
    PACIENTE_DE_BAJA
}
