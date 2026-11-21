package ar.edu.odontogestion.application.consultorio;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;
import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lectura y edición de los datos y parámetros del consultorio único. */
@Service
public class ConsultorioService {

    private static final String MENSAJE_POSITIVO = "Debe ser un entero mayor que cero.";

    private final ConsultorioActual consultorioActual;
    private final ConsultorioRepository consultorios;

    public ConsultorioService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
    }

    @Transactional(readOnly = true)
    public ConsultorioVista obtener() {
        return aVista(cargar());
    }

    /**
     * Contrato para C-06/C-07/C-09/C-11: parámetros vigentes leídos de la base en cada llamada (nadie los cachea).
     */
    @Transactional(readOnly = true)
    public ParametrosConsultorio parametrosVigentes() {
        return cargar().getParametros();
    }

    /**
     * Valida los parámetros (422 con campo, todos juntos y sin modificar nada) y luego aplica datos y
     * parámetros. La zona horaria nunca cambia.
     */
    @Transactional
    public ConsultorioVista actualizar(DatosConsultorio datos) {
        ParametrosConsultorio p = datos.parametros();
        List<ErrorCampo> errores = new ArrayList<>();
        exigirPositivo(errores, "granularidadMin", p.granularidadMin());
        exigirPositivo(errores, "anticipacionMinReservaMin", p.anticipacionMinReservaMin());
        exigirPositivo(errores, "limiteCancelacionHoras", p.limiteCancelacionHoras());
        exigirPositivo(errores, "recordatorioHorasAntes", p.recordatorioHorasAntes());
        exigirPositivo(errores, "vencimientoAvisoMin", p.vencimientoAvisoMin());
        exigirPositivo(errores, "vencimientoPendienteMin", p.vencimientoPendienteMin());
        exigirPositivo(errores, "toleranciaAusenteMin", p.toleranciaAusenteMin());
        if (!errores.isEmpty()) {
            throw new ValidacionDominioException(errores);
        }

        Consultorio consultorio = cargar();
        consultorio.actualizarDatos(datos.nombre(), datos.direccion(), datos.telefono(), datos.emailContacto());
        consultorio.actualizarParametros(p);
        return aVista(consultorio);
    }

    private Consultorio cargar() {
        return consultorios.findById(consultorioActual.id())
                .orElseThrow(() -> new RecursoNoEncontradoException("Consultorio no encontrado"));
    }

    private static ConsultorioVista aVista(Consultorio c) {
        return new ConsultorioVista(c.getNombre(), c.getDireccion(), c.getTelefono(), c.getEmailContacto(),
                c.getZonaHoraria(), c.getParametros());
    }

    private static void exigirPositivo(List<ErrorCampo> errores, String campo, int valor) {
        if (valor <= 0) {
            errores.add(new ErrorCampo("parametros." + campo, MENSAJE_POSITIVO));
        }
    }
}
