package ar.edu.odontogestion.application.listaespera;

import ar.edu.odontogestion.domain.listaespera.AvisoVencidoException;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import org.springframework.stereotype.Service;

/**
 * Puerta de entrada de los avisos para la API (C-10 D5/D6), espejo de {@code SolicitudPublicaComandos}: <b>sin
 * {@code @Transactional}</b> a propósito. Tras el rollback de {@code aceptar} abre transacciones nuevas para dejar
 * persistido lo que sí debe quedar: el aviso vencido pasa a {@code EXPIRADO} (410) y, si la base rechazó el turno por
 * superposición, los avisos del hueco quedan {@code CERRADO} (409). Los mensajes no llevan el token.
 */
@Service
public class AvisoComandos {

    private final AvisoHuecoService avisos;

    public AvisoComandos(AvisoHuecoService avisos) {
        this.avisos = avisos;
    }

    public ResumenAvisoResponse resumen(String token) {
        try {
            return avisos.resumen(token);
        } catch (AvisoVencidoException e) {
            avisos.marcarExpirado(e.avisoId());
            throw e;
        }
    }

    public AvisoAceptadoResponse aceptar(String token) {
        try {
            return avisos.aceptar(token);
        } catch (AvisoVencidoException e) {
            avisos.marcarExpirado(e.avisoId());
            throw e;
        } catch (HuecoNoDisponibleException e) {
            avisos.cerrarAvisosDelHueco(avisos.idDeAviso(token));
            throw e;
        }
    }
}
