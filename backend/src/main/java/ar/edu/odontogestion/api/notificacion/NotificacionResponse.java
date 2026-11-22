package ar.edu.odontogestion.api.notificacion;

import ar.edu.odontogestion.application.notificacion.NotificacionVista;
import ar.edu.odontogestion.domain.notificacion.CanalNotificacion;
import ar.edu.odontogestion.domain.notificacion.EstadoNotificacion;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import java.time.Instant;

/**
 * Fila del registro de envíos (design D13). Instantes ISO-8601 en UTC; {@code enviadaEn} y {@code error} pueden
 * ser {@code null}. Nunca incluye el cuerpo, las variables ni los tokens de los enlaces.
 */
public record NotificacionResponse(Long id, TipoNotificacion tipo, CanalNotificacion canal, String destinatario,
        EstadoNotificacion estado, int intentos, int reintentosManuales, String error, Referencia referencia,
        Instant programadaPara, Instant enviadaEn, Instant creadoEn) {

    /** @param tipo {@code TURNO | AVISO | ENTRADA_LISTA_ESPERA} */
    public record Referencia(String tipo, long id) {

        static Referencia de(ReferenciaToken referencia) {
            String tipo = switch (referencia) {
                case ReferenciaToken.Turno t -> "TURNO";
                case ReferenciaToken.Aviso a -> "AVISO";
                case ReferenciaToken.EntradaListaEspera e -> "ENTRADA_LISTA_ESPERA";
            };
            return new Referencia(tipo, referencia.id());
        }
    }

    static NotificacionResponse from(NotificacionVista v) {
        return new NotificacionResponse(v.id(), v.tipo(), v.canal(), v.destinatario(), v.estado(), v.intentos(),
                v.reintentosManuales(), v.error(), Referencia.de(v.referencia()), v.programadaPara(), v.enviadaEn(),
                v.creadoEn());
    }
}
