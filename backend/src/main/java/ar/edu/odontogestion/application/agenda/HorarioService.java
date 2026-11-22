package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import ar.edu.odontogestion.application.comun.RestriccionesDb;
import ar.edu.odontogestion.domain.agenda.HorarioTrabajo;
import ar.edu.odontogestion.domain.agenda.HorarioTrabajoRepository;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Horarios semanales de atención (US-012). Las horas llegan como texto y se validan acá (422 por campo, D11); el no
 * solapamiento lo garantiza la base ({@code EXCLUDE}) y se traduce por nombre de restricción (D3).
 */
@Service
public class HorarioService {

    static final String EX_SOLAPAMIENTO = "ex_horario_trabajo_sin_solapamiento";

    private static final Pattern HH_MM = Pattern.compile("([01]\\d|2[0-3]):([0-5]\\d)");
    private static final String OBLIGATORIO = "Es obligatorio.";
    private static final String HORA_INVALIDA = "Debe ser una hora válida en formato HH:mm.";
    private static final String DIA_INVALIDO = "Debe ser un día entre 1 (lunes) y 7 (domingo).";
    private static final String RANGO_INVERTIDO = "Debe ser posterior a la hora de inicio.";
    private static final String SOLAPADA = "Se superpone con otra franja del mismo día.";

    private final ConsultorioActual consultorioActual;
    private final ConsultorioService consultorioService;
    private final ConsultorioRepository consultorios;
    private final HorarioTrabajoRepository horarios;

    public HorarioService(ConsultorioActual consultorioActual, ConsultorioService consultorioService,
            ConsultorioRepository consultorios, HorarioTrabajoRepository horarios) {
        this.consultorioActual = consultorioActual;
        this.consultorioService = consultorioService;
        this.consultorios = consultorios;
        this.horarios = horarios;
    }

    /** Ordenados por día y hora de inicio. */
    @Transactional(readOnly = true)
    public List<HorarioVista> listar() {
        return horarios.findByConsultorio_IdOrderByDiaSemanaAscHoraDesdeAsc(consultorioActual.id()).stream()
                .map(HorarioVista::de).toList();
    }

    @Transactional
    public HorarioVista crear(DatosHorario datos) {
        Franja franja = validar(datos, null);
        HorarioTrabajo nuevo = HorarioTrabajo.nuevo(consultorios.getReferenceById(consultorioActual.id()),
                franja.dia(), franja.desde(), franja.hasta());
        return guardar(nuevo);
    }

    /** Valida todo antes de tocar la entidad: si falla, la franja queda como estaba. */
    @Transactional
    public HorarioVista actualizar(Long id, DatosHorario datos) {
        HorarioTrabajo horario = cargar(id);
        Franja franja = validar(datos, id);
        horario.actualizar(franja.dia(), franja.desde(), franja.hasta());
        return guardar(horario);
    }

    @Transactional
    public void eliminar(Long id) {
        horarios.delete(cargar(id));
    }

    private record Franja(int dia, LocalTime desde, LocalTime hasta) {
    }

    private Franja validar(DatosHorario datos, Long idPropio) {
        List<ErrorCampo> errores = new ArrayList<>();
        Integer dia = datos.diaSemana();
        if (dia == null) {
            errores.add(new ErrorCampo("diaSemana", OBLIGATORIO));
        } else if (dia < 1 || dia > 7) {
            errores.add(new ErrorCampo("diaSemana", DIA_INVALIDO));
        }
        int granularidad = consultorioService.parametrosVigentes().granularidadMin();
        LocalTime desde = hora("horaDesde", datos.horaDesde(), granularidad, errores);
        LocalTime hasta = hora("horaHasta", datos.horaHasta(), granularidad, errores);
        if (desde != null && hasta != null && !desde.isBefore(hasta)) {
            errores.add(new ErrorCampo("horaHasta", RANGO_INVERTIDO));
        }
        if (errores.isEmpty() && haySolapamiento(dia, desde, hasta, idPropio)) {
            errores.add(new ErrorCampo("horaDesde", SOLAPADA));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionDominioException(errores);
        }
        return new Franja(dia, desde, hasta);
    }

    /** {@code HH:mm} estricto y alineado a la granularidad; si no, agrega el error y devuelve {@code null}. */
    private static LocalTime hora(String campo, String valor, int granularidad, List<ErrorCampo> errores) {
        if (valor == null) {
            errores.add(new ErrorCampo(campo, OBLIGATORIO));
            return null;
        }
        Matcher m = HH_MM.matcher(valor);
        if (!m.matches()) {
            errores.add(new ErrorCampo(campo, HORA_INVALIDA));
            return null;
        }
        LocalTime hora = LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        if ((hora.getHour() * 60 + hora.getMinute()) % granularidad != 0) {
            errores.add(new ErrorCampo(campo, "Debe estar alineada a " + granularidad + " minutos."));
            return null;
        }
        return hora;
    }

    /** Chequeo previo para el caso común; la garantía ante carreras es el {@code EXCLUDE} de la base. */
    private boolean haySolapamiento(int dia, LocalTime desde, LocalTime hasta, Long idPropio) {
        return horarios.findByConsultorio_IdAndDiaSemana(consultorioActual.id(), (short) dia).stream()
                .filter(h -> idPropio == null || !idPropio.equals(h.getId()))
                .anyMatch(h -> h.seSolapaCon(dia, desde, hasta));
    }

    private HorarioVista guardar(HorarioTrabajo horario) {
        try {
            return HorarioVista.de(horarios.saveAndFlush(horario));
        } catch (DataIntegrityViolationException ex) {
            if (RestriccionesDb.viola(ex, EX_SOLAPAMIENTO)) {
                throw new ValidacionDominioException("horaDesde", SOLAPADA);
            }
            throw ex;
        }
    }

    private HorarioTrabajo cargar(Long id) {
        return horarios.findByIdAndConsultorio_Id(id, consultorioActual.id())
                .orElseThrow(() -> new RecursoNoEncontradoException("Horario no encontrado"));
    }
}
