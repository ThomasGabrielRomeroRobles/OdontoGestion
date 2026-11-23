package ar.edu.odontogestion.domain.turno;

import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.error.ConflictoException;
import java.util.List;

/**
 * El intervalo pedido ya está ocupado (409, design D5/D6). {@code alternativas} (hasta 3 huecos libres) la completa
 * {@code TurnoComandos} fuera de la transacción fallida con {@link #conAlternativas(List)}.
 */
public class HuecoNoDisponibleException extends ConflictoException {

    static final String MENSAJE = "El horario elegido ya no está disponible.";

    private final List<Hueco> alternativas;

    public HuecoNoDisponibleException() {
        this(List.of());
    }

    public HuecoNoDisponibleException(List<Hueco> alternativas) {
        super(MENSAJE);
        this.alternativas = List.copyOf(alternativas);
    }

    public List<Hueco> alternativas() {
        return alternativas;
    }

    public HuecoNoDisponibleException conAlternativas(List<Hueco> nuevas) {
        return new HuecoNoDisponibleException(nuevas);
    }

    @Override
    public String codigo() {
        return "hueco-no-disponible";
    }
}
