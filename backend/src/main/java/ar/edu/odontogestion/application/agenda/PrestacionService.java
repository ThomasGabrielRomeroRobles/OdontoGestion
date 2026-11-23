package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import ar.edu.odontogestion.application.comun.RestriccionesDb;
import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.agenda.PrestacionRepository;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prestaciones del consultorio (US-011). Toda regla de negocio vive acá y responde 422 por campo (design.md D11);
 * la unicidad del nombre la garantiza la base y se traduce por nombre de restricción (D3).
 */
@Service
public class PrestacionService {

    static final String UQ_NOMBRE = "uq_prestacion_consultorio_nombre";
    static final int NOMBRE_MAX = 80;

    private static final String OBLIGATORIO = "Es obligatorio.";
    private static final String NOMBRE_LARGO = "Máximo " + NOMBRE_MAX + " caracteres.";
    private static final String NOMBRE_DUPLICADO = "Ya existe una prestación con ese nombre.";
    private static final String POSITIVA = "Debe ser un entero mayor que cero.";

    private final ConsultorioActual consultorioActual;
    private final ConsultorioService consultorioService;
    private final ConsultorioRepository consultorios;
    private final PrestacionRepository prestaciones;

    public PrestacionService(ConsultorioActual consultorioActual, ConsultorioService consultorioService,
            ConsultorioRepository consultorios, PrestacionRepository prestaciones) {
        this.consultorioActual = consultorioActual;
        this.consultorioService = consultorioService;
        this.consultorios = consultorios;
        this.prestaciones = prestaciones;
    }

    /** Todas, activas e inactivas, ordenadas por nombre. */
    @Transactional(readOnly = true)
    public List<PrestacionVista> listar() {
        return prestaciones.findByConsultorio_IdOrderByNombreAsc(consultorioActual.id()).stream()
                .map(PrestacionVista::de).toList();
    }

    @Transactional
    public PrestacionVista crear(DatosPrestacion datos) {
        String nombre = validar(datos, null);
        Prestacion nueva = Prestacion.nueva(consultorios.getReferenceById(consultorioActual.id()), nombre,
                datos.duracionMin());
        return guardar(nueva);
    }

    @Transactional
    public PrestacionVista actualizar(Long id, DatosPrestacion datos) {
        Prestacion prestacion = cargar(id);
        String nombre = validar(datos, id);
        prestacion.actualizar(nombre, datos.duracionMin());
        return guardar(prestacion);
    }

    /** Idempotente. No vuelve a validar la duración: la granularidad pudo cambiar después del alta (D3). */
    @Transactional
    public PrestacionVista desactivar(Long id) {
        Prestacion prestacion = cargar(id);
        prestacion.desactivar();
        return PrestacionVista.de(prestacion);
    }

    /** Idempotente. Igual que {@link #desactivar}, no vuelve a validar la duración. */
    @Transactional
    public PrestacionVista activar(Long id) {
        Prestacion prestacion = cargar(id);
        prestacion.activar();
        return PrestacionVista.de(prestacion);
    }

    /** Reúne todos los errores de la solicitud; si no hay, devuelve el nombre normalizado. */
    private String validar(DatosPrestacion datos, Long idPropio) {
        List<ErrorCampo> errores = new ArrayList<>();
        String nombre = datos.nombre() == null ? "" : datos.nombre().strip();
        if (nombre.isEmpty()) {
            errores.add(new ErrorCampo("nombre", OBLIGATORIO));
        } else if (nombre.length() > NOMBRE_MAX) {
            errores.add(new ErrorCampo("nombre", NOMBRE_LARGO));
        } else if (nombreEnUso(nombre, idPropio)) {
            errores.add(new ErrorCampo("nombre", NOMBRE_DUPLICADO));
        }
        validarDuracion(datos.duracionMin(), errores);
        if (!errores.isEmpty()) {
            throw new ValidacionDominioException(errores);
        }
        return nombre;
    }

    private void validarDuracion(Integer duracion, List<ErrorCampo> errores) {
        if (duracion == null) {
            errores.add(new ErrorCampo("duracionMin", OBLIGATORIO));
            return;
        }
        if (duracion <= 0) {
            errores.add(new ErrorCampo("duracionMin", POSITIVA));
            return;
        }
        int granularidad = consultorioService.parametrosVigentes().granularidadMin();
        if (duracion % granularidad != 0) {
            errores.add(new ErrorCampo("duracionMin", "Debe ser múltiplo de " + granularidad + " minutos."));
        }
    }

    private boolean nombreEnUso(String nombre, Long idPropio) {
        Long cid = consultorioActual.id();
        return idPropio == null
                ? prestaciones.existsByConsultorio_IdAndNombreIgnoreCase(cid, nombre)
                : prestaciones.existsByConsultorio_IdAndNombreIgnoreCaseAndIdNot(cid, nombre, idPropio);
    }

    /** {@code saveAndFlush}: la restricción única se evalúa acá y no al commit, para responder 422 con campo. */
    private PrestacionVista guardar(Prestacion prestacion) {
        try {
            return PrestacionVista.de(prestaciones.saveAndFlush(prestacion));
        } catch (DataIntegrityViolationException ex) {
            if (RestriccionesDb.viola(ex, UQ_NOMBRE)) {
                throw new ValidacionDominioException("nombre", NOMBRE_DUPLICADO);
            }
            throw ex;
        }
    }

    private Prestacion cargar(Long id) {
        return prestaciones.findByIdAndConsultorio_Id(id, consultorioActual.id())
                .orElseThrow(() -> new RecursoNoEncontradoException("Prestación no encontrada"));
    }
}
