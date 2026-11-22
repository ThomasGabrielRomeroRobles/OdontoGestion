package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.domain.notificacion.CanalNotificacion;
import ar.edu.odontogestion.domain.notificacion.EstadoNotificacion;
import ar.edu.odontogestion.domain.notificacion.Notificacion;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import java.time.Instant;

/**
 * Lectura de una notificación para el registro de envíos (design D13). Sin variables de plantilla, cuerpo ni
 * enlaces: nada de eso sale de la capa de aplicación.
 */
public record NotificacionVista(Long id, TipoNotificacion tipo, CanalNotificacion canal, String destinatario,
        EstadoNotificacion estado, int intentos, int reintentosManuales, String error, ReferenciaToken referencia,
        Instant programadaPara, Instant enviadaEn, Instant creadoEn) {

    static NotificacionVista de(Notificacion n) {
        return new NotificacionVista(n.getId(), n.getTipo(), n.getCanal(), n.getDestinatario(), n.getEstado(),
                n.getIntentos(), n.getReintentosManuales(), n.getError(), n.referencia(), n.getProgramadaPara(),
                n.getEnviadaEn(), n.getCreadoEn());
    }
}
