package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.domain.notificacion.CanalNotificacion;

/**
 * Puerto de envío de notificaciones (KB 12, design D10). Implementaciones: {@code EmailChannel} en el MVP;
 * WhatsApp a futuro. Contrato: devuelve {@link ResultadoEnvio.Enviado} o {@link ResultadoEnvio.Fallido} con
 * motivo y MUST NOT lanzar excepciones al procesamiento.
 */
public interface NotificationChannel {

    CanalNotificacion canal();

    ResultadoEnvio enviar(NotificacionSaliente notificacion);
}
