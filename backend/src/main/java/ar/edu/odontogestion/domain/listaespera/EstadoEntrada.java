package ar.edu.odontogestion.domain.listaespera;

/** Estado de una entrada de la lista de espera (C-10 D1). {@code VENCIDA} solo se usa para pendientes sin confirmar. */
public enum EstadoEntrada {
    PENDIENTE_CONFIRMACION, ACTIVA, CUBIERTA, CANCELADA, VENCIDA;

    /** {@code true} si cuenta para el único parcial {@code uq_entrada_lista_espera_vigente}. */
    public boolean vigente() {
        return this == PENDIENTE_CONFIRMACION || this == ACTIVA;
    }
}
