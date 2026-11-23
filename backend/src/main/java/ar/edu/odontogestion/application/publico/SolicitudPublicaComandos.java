package ar.edu.odontogestion.application.publico;

import ar.edu.odontogestion.application.agenda.DisponibilidadService;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Puerta de entrada de la solicitud pública (C-09 D2), espejo de {@code TurnoComandos}: <b>sin
 * {@code @Transactional}</b> a propósito, porque tras un {@code 23P01} la transacción de escritura queda abortada
 * y las alternativas del {@code 409} se calculan acá, en una transacción de solo lectura nueva.
 *
 * <p>Orden de chequeos: límite por IP → señuelo → límite por identidad (DNI y email) → servicio. El señuelo
 * cuenta para el límite de la IP pero no crea nada; su log no lleva IP ni datos.
 */
@Service
public class SolicitudPublicaComandos {

    /** Máximo de alternativas que acompañan a un conflicto (igual que el odontólogo, C-07 D1). */
    static final int MAX_ALTERNATIVAS = 3;

    private static final Logger log = LoggerFactory.getLogger(SolicitudPublicaComandos.class);

    private final LimitadorSolicitudesPublicas limitador;
    private final SolicitudPublicaService servicio;
    private final DisponibilidadService disponibilidad;

    public SolicitudPublicaComandos(LimitadorSolicitudesPublicas limitador, SolicitudPublicaService servicio,
            DisponibilidadService disponibilidad) {
        this.limitador = limitador;
        this.servicio = servicio;
        this.disponibilidad = disponibilidad;
    }

    /**
     * @param ip      dirección de origen de la conexión ({@code getRemoteAddr()}, nunca {@code X-Forwarded-For})
     * @param senuelo {@code true} si el campo trampa vino con contenido
     * @throws ar.edu.odontogestion.domain.error.DemasiadasSolicitudesException límite por IP, DNI, email o
     *                                                                           pendientes (429)
     * @throws HuecoNoDisponibleException el intervalo ya está ocupado, con las alternativas públicas (409)
     */
    public void solicitar(String ip, DatosSolicitudPublica datos, boolean senuelo) {
        limitador.registrarIp(ip);
        if (senuelo) {
            log.info("Solicitud pública descartada por señuelo");
            return;
        }
        limitador.registrarIdentidad(datos.paciente().dni(), datos.paciente().email());
        try {
            servicio.solicitar(datos);
        } catch (HuecoNoDisponibleException e) {
            throw e.conAlternativas(
                    disponibilidad.alternativasPublicas(datos.prestacionId(), datos.inicio(), MAX_ALTERNATIVAS));
        }
    }
}
