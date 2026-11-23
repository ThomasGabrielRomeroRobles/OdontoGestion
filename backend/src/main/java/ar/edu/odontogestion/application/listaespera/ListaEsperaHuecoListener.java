package ar.edu.odontogestion.application.listaespera;

import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escucha {@link HuecoLiberado} <b>después del commit</b> de la operación que liberó el hueco (C-10 D4): si esa
 * operación se revierte no hay evento. Delega en {@link AvisoHuecoService#generarAvisos}, que abre su propia
 * transacción. Nunca propaga: un fallo se loguea (solo el id del turno) y el hueco sigue libre para reserva normal
 * (RN-LE-07); no hay reintento en el MVP.
 */
@Component
public class ListaEsperaHuecoListener {

    private static final Logger log = LoggerFactory.getLogger(ListaEsperaHuecoListener.class);

    private final AvisoHuecoService avisos;

    public ListaEsperaHuecoListener(AvisoHuecoService avisos) {
        this.avisos = avisos;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alLiberarseUnHueco(HuecoLiberado evento) {
        try {
            int creados = avisos.generarAvisos(evento);
            if (creados > 0) {
                log.info("Avisos de lista de espera creados para el turno {}: {}", evento.turnoOrigenId(), creados);
            }
        } catch (RuntimeException e) {
            log.error("No se generaron avisos de lista de espera para el turno {}: {}", evento.turnoOrigenId(),
                    e.getClass().getSimpleName());
        }
    }
}
