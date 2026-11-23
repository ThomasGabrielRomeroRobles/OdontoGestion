package ar.edu.odontogestion.domain.listaespera;

/** Estado de un aviso de hueco liberado (C-10 D1). Solo {@code ENVIADO} admite transiciones. */
public enum EstadoAviso {
    ENVIADO, ACEPTADO, EXPIRADO, CERRADO
}
