package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.agenda.BloqueoAgenda;
import ar.edu.odontogestion.domain.agenda.BloqueoAgendaRepository;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueos de agenda (US-012). Los instantes llegan como texto ISO-8601 con zona y se validan acá (422 por campo,
 * D11); se guardan en UTC. El listado sigue la tabla de filtros de design D12.
 */
@Service
public class BloqueoService {

    static final int MOTIVO_MAX = 200;

    private static final String OBLIGATORIO = "Es obligatorio.";
    private static final String FECHA_INVALIDA =
            "Debe ser una fecha y hora ISO-8601 con zona horaria (p. ej. 2026-12-24T03:00:00Z).";
    private static final String RANGO_INVERTIDO = "Debe ser posterior a desde.";
    private static final String MOTIVO_LARGO = "Máximo " + MOTIVO_MAX + " caracteres.";

    private final ConsultorioActual consultorioActual;
    private final ConsultorioRepository consultorios;
    private final BloqueoAgendaRepository bloqueos;
    private final Clock clock;

    public BloqueoService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            BloqueoAgendaRepository bloqueos, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.bloqueos = bloqueos;
        this.clock = clock;
    }

    /**
     * Bloqueos que se intersecan con {@code [inicio, fin)}, ordenados por {@code desde}. Filtros opcionales e
     * independientes (vacío = ausente): sin {@code desde} el inicio es ahora; sin {@code hasta} no hay límite.
     */
    @Transactional(readOnly = true)
    public List<BloqueoVista> listar(String desde, String hasta) {
        List<ErrorCampo> errores = new ArrayList<>();
        Instant inicio = vacio(desde) ? clock.instant() : instante("desde", desde, errores);
        Instant fin = vacio(hasta) ? null : instante("hasta", hasta, errores);
        if (!vacio(desde) && inicio != null && fin != null && !fin.isAfter(inicio)) {
            errores.add(new ErrorCampo("hasta", RANGO_INVERTIDO));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionDominioException(errores);
        }
        Long cid = consultorioActual.id();
        List<BloqueoAgenda> encontrados = fin == null
                ? bloqueos.findByConsultorio_IdAndHastaGreaterThanOrderByDesdeAsc(cid, inicio)
                : bloqueos.findByConsultorio_IdAndHastaGreaterThanAndDesdeLessThanOrderByDesdeAsc(cid, inicio, fin);
        return encontrados.stream().map(BloqueoVista::de).toList();
    }

    @Transactional
    public BloqueoVista crear(DatosBloqueo datos) {
        Rango rango = validar(datos);
        BloqueoAgenda nuevo = BloqueoAgenda.nuevo(consultorios.getReferenceById(consultorioActual.id()),
                rango.desde(), rango.hasta(), rango.motivo());
        return BloqueoVista.de(bloqueos.saveAndFlush(nuevo));
    }

    /** Valida todo antes de tocar la entidad: si falla, el bloqueo queda como estaba. */
    @Transactional
    public BloqueoVista actualizar(Long id, DatosBloqueo datos) {
        BloqueoAgenda bloqueo = cargar(id);
        Rango rango = validar(datos);
        bloqueo.actualizar(rango.desde(), rango.hasta(), rango.motivo());
        return BloqueoVista.de(bloqueos.saveAndFlush(bloqueo));
    }

    @Transactional
    public void eliminar(Long id) {
        bloqueos.delete(cargar(id));
    }

    private record Rango(Instant desde, Instant hasta, String motivo) {
    }

    private Rango validar(DatosBloqueo datos) {
        List<ErrorCampo> errores = new ArrayList<>();
        Instant desde = obligatorio("desde", datos.desde(), errores);
        Instant hasta = obligatorio("hasta", datos.hasta(), errores);
        if (desde != null && hasta != null && !hasta.isAfter(desde)) {
            errores.add(new ErrorCampo("hasta", RANGO_INVERTIDO));
        }
        String motivo = datos.motivo() == null ? "" : datos.motivo().strip();
        if (motivo.length() > MOTIVO_MAX) {
            errores.add(new ErrorCampo("motivo", MOTIVO_LARGO));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionDominioException(errores);
        }
        return new Rango(desde, hasta, motivo);
    }

    private static Instant obligatorio(String campo, String valor, List<ErrorCampo> errores) {
        if (vacio(valor)) {
            errores.add(new ErrorCampo(campo, OBLIGATORIO));
            return null;
        }
        return instante(campo, valor, errores);
    }

    /** ISO-8601 con zona u offset explícitos ({@code Z}, {@code -03:00}); sin zona es inválido. */
    private static Instant instante(String campo, String valor, List<ErrorCampo> errores) {
        try {
            return OffsetDateTime.parse(valor.strip()).toInstant();
        } catch (DateTimeParseException ex) {
            errores.add(new ErrorCampo(campo, FECHA_INVALIDA));
            return null;
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private BloqueoAgenda cargar(Long id) {
        return bloqueos.findByIdAndConsultorio_Id(id, consultorioActual.id())
                .orElseThrow(() -> new RecursoNoEncontradoException("Bloqueo no encontrado"));
    }
}
