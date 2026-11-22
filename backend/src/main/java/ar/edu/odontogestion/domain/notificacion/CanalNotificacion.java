package ar.edu.odontogestion.domain.notificacion;

/** Canal de envío. El MVP usa solo {@code EMAIL} (RN-NO-01); {@code WHATSAPP} queda previsto. */
public enum CanalNotificacion {
    EMAIL,
    WHATSAPP
}
