package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import java.util.List;

/**
 * Puerto que arma las URLs de los enlaces en el momento del envío (design D8): emite un token por enlace para la
 * referencia de la notificación. El módulo de notificaciones no conoce el de tokens; el adaptador sí.
 */
public interface EmisorEnlaces {

    /**
     * @return un enlace por cada programado, en el mismo orden, con la URL que contiene el token en claro
     */
    List<EnlaceRenderizado> emitir(ReferenciaToken referencia, List<EnlaceProgramado> enlaces);
}
