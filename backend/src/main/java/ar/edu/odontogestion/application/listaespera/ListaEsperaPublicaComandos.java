package ar.edu.odontogestion.application.listaespera;

import ar.edu.odontogestion.application.publico.LimitadorSolicitudesPublicas;
import ar.edu.odontogestion.domain.listaespera.EntradaDuplicadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Puerta de entrada de la inscripción pública (C-10 D2), espejo de {@code SolicitudPublicaComandos}: <b>sin
 * {@code @Transactional}</b> a propósito, para que el duplicado (violación del único parcial) aborte solo la
 * transacción del servicio. Orden: límite por IP → señuelo → límite por identidad → servicio. El duplicado se
 * silencia: la respuesta pública es la misma (sin enumeración, DD-14). Los logs no llevan IP ni datos.
 */
@Service
public class ListaEsperaPublicaComandos {

    private static final Logger log = LoggerFactory.getLogger(ListaEsperaPublicaComandos.class);

    private final LimitadorSolicitudesPublicas limitador;
    private final ListaEsperaService servicio;

    public ListaEsperaPublicaComandos(LimitadorSolicitudesPublicas limitador, ListaEsperaService servicio) {
        this.limitador = limitador;
        this.servicio = servicio;
    }

    /**
     * @param ip      dirección de origen de la conexión ({@code getRemoteAddr()}, nunca {@code X-Forwarded-For})
     * @param senuelo {@code true} si el campo trampa vino con contenido
     * @throws ar.edu.odontogestion.domain.error.DemasiadasSolicitudesException límite por IP, DNI o email (429)
     */
    public void inscribir(String ip, DatosInscripcion datos, boolean senuelo) {
        limitador.registrarIp(ip);
        if (senuelo) {
            log.info("Inscripción pública descartada por señuelo");
            return;
        }
        limitador.registrarIdentidad(datos.paciente().dni(), datos.paciente().email());
        try {
            servicio.inscribir(datos);
        } catch (EntradaDuplicadaException e) {
            log.info("Inscripción pública duplicada ignorada");
        }
    }
}
