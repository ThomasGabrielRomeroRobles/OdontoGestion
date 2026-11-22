package ar.edu.odontogestion.application.publico;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import ar.edu.odontogestion.application.paciente.PacienteService;
import ar.edu.odontogestion.application.paciente.ResultadoBuscarOCrear;
import ar.edu.odontogestion.application.turno.ReservaTurnoService;
import ar.edu.odontogestion.application.turno.SolicitudReserva;
import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;
import ar.edu.odontogestion.domain.error.DemasiadasSolicitudesException;
import ar.edu.odontogestion.domain.paciente.Dni;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import ar.edu.odontogestion.domain.usuario.Emails;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitud pública de turno (C-09 D2): en <b>una sola transacción</b> libera las pendientes vencidas, aplica el
 * máximo de pendientes por DNI o email, busca o crea al paciente sin tocar sus datos (RN-PU-04) y crea el turno
 * {@code PENDIENTE_CONFIRMACION} con el núcleo de C-07. Si cualquier paso falla (incluido un conflicto de agenda
 * que decide la base) nada persiste, tampoco el paciente nuevo. Los mails los encola el listener en
 * {@code BEFORE_COMMIT}.
 *
 * <p>Nunca loguea DNI, email, teléfono ni nombre.
 */
@Service
public class SolicitudPublicaService {

    /** Máximo de solicitudes pendientes vigentes por DNI o por email (RN-PU-03, DD-14). */
    static final int MAX_PENDIENTES = 2;

    private final ConsultorioActual consultorioActual;
    private final ConsultorioService consultorioService;
    private final PacienteService pacientes;
    private final ReservaTurnoService reservas;
    private final TurnoRepository turnos;
    private final Clock clock;

    public SolicitudPublicaService(ConsultorioActual consultorioActual, ConsultorioService consultorioService,
            PacienteService pacientes, ReservaTurnoService reservas, TurnoRepository turnos, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.consultorioService = consultorioService;
        this.pacientes = pacientes;
        this.reservas = reservas;
        this.turnos = turnos;
        this.clock = clock;
    }

    /**
     * @throws DemasiadasSolicitudesException ya hay {@value #MAX_PENDIENTES} pendientes vigentes del DNI o email (429)
     * @throws ar.edu.odontogestion.domain.error.ValidacionDominioException horario o prestación inválidos (422)
     * @throws ar.edu.odontogestion.domain.error.RecursoNoEncontradoException prestación inexistente (404)
     * @throws ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException el intervalo ya está ocupado (409)
     */
    @Transactional
    public ResultadoSolicitud solicitar(DatosSolicitudPublica datos) {
        Long consultorioId = consultorioActual.id();
        Instant ahora = clock.instant();
        // Las vencidas se liberan antes de contar: una pendiente vencida no cuenta ni retiene el hueco.
        turnos.expirarPendientesVencidas(consultorioId, ahora);

        ParametrosConsultorio parametros = consultorioService.parametrosVigentes();
        String dni = Dni.normalizar(datos.paciente().dni());
        String email = Emails.normalizar(datos.paciente().email());
        if (turnos.contarPendientesVigentesPorDniOEmail(consultorioId, dni, email, ahora) >= MAX_PENDIENTES) {
            throw new DemasiadasSolicitudesException(parametros.vencimientoPendienteMin() * 60.0);
        }

        ResultadoBuscarOCrear resultado = pacientes.buscarOCrearPorDni(datos.paciente());
        if (!resultado.paciente().activo()) {
            // Mismo resultado visible que un DNI nuevo: evita el 422 de C-07, que revelaría que el DNI existe.
            return ResultadoSolicitud.PACIENTE_DE_BAJA;
        }
        reservas.reservar(SolicitudReserva.publica(resultado.paciente().id(), datos.prestacionId(), datos.inicio(),
                parametros.anticipacionMinReservaMin()));
        return ResultadoSolicitud.PENDIENTE_CREADA;
    }
}
