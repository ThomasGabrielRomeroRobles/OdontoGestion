package ar.edu.odontogestion.application.acciones;

import ar.edu.odontogestion.application.acciones.ResumenAccionResponse.Motivo;
import ar.edu.odontogestion.application.acciones.ResumenAccionResponse.TurnoResumen;
import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenIdentificado;
import ar.edu.odontogestion.domain.turno.CanceladoPor;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import ar.edu.odontogestion.domain.turno.evento.SolicitudConfirmada;
import ar.edu.odontogestion.domain.turno.evento.TurnoCancelado;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acciones del paciente por enlace de email (C-09 D6): confirmar la solicitud, confirmar el turno y cancelarlo. El
 * {@code GET} ({@link #resumen}) nunca consume el token; el {@code POST} ({@link #ejecutar}) consume <b>primero</b>
 * con la {@code UPDATE} condicional (un segundo {@code POST} concurrente espera y luego recibe {@code 410}) y aplica
 * la acción en la misma transacción: si falla, todo se revierte y el token sigue vigente.
 *
 * <p>Orden de los efectos de la cancelación: primero se revocan los demás tokens del turno y recién después se
 * modifica el turno. Así las transacciones concurrentes toman los bloqueos en el mismo orden (token → turno) y no
 * hay interbloqueo con una confirmación simultánea.
 *
 * <p>Ningún mensaje ni log lleva el token, la URL ni datos del paciente (regla dura #10).
 */
@Service
public class AccionTokenService {

    /** Propósitos que atiende {@code /acciones/{token}}: los de turno. */
    static final Set<PropositoToken> ACCIONES = EnumSet.of(PropositoToken.CONFIRMAR_SOLICITUD,
            PropositoToken.CONFIRMAR_TURNO, PropositoToken.CANCELAR_TURNO);

    private final TokenAccesoService tokens;
    private final TurnoRepository turnos;
    private final ConsultorioActual consultorioActual;
    private final ConsultorioService consultorioService;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public AccionTokenService(TokenAccesoService tokens, TurnoRepository turnos, ConsultorioActual consultorioActual,
            ConsultorioService consultorioService, ApplicationEventPublisher eventos, Clock clock) {
        this.tokens = tokens;
        this.turnos = turnos;
        this.consultorioActual = consultorioActual;
        this.consultorioService = consultorioService;
        this.eventos = eventos;
        this.clock = clock;
    }

    /**
     * Qué muestra el enlace, sin consumirlo ni modificar nada.
     *
     * @throws TokenInvalidoException token inexistente, usado, vencido, revocado, de otro propósito, de un turno que
     *                                ya no existe o solicitud pendiente cuyo plazo ya pasó (410)
     */
    @Transactional(readOnly = true)
    public ResumenAccionResponse resumen(String token) {
        TokenIdentificado identificado = tokens.identificar(token, ACCIONES);
        Turno turno = cargar(identificado.referencia());
        Instant ahora = clock.instant();
        if (identificado.proposito() == PropositoToken.CONFIRMAR_SOLICITUD && turno.pendienteVencida(ahora)) {
            throw new TokenInvalidoException();
        }
        return resumen(identificado.proposito(), identificado.venceEn(), turno, ahora);
    }

    /**
     * Ejecuta la acción esperada. {@code accion} debe coincidir con el propósito del token: si no (o si no es una
     * acción de turno) es {@code 410} sin consumirlo.
     *
     * @throws TokenInvalidoException enlace inválido o acción que no coincide (410), o solicitud ya vencida
     * @throws ar.edu.odontogestion.domain.turno.TransicionInvalidaException el estado del turno no admite la acción
     *                                                                        (422, el token sigue vigente)
     * @throws ar.edu.odontogestion.domain.turno.LimiteCancelacionException cancelación fuera del límite (422)
     */
    @Transactional
    public ResumenAccionResponse ejecutar(String token, PropositoToken accion) {
        if (!ACCIONES.contains(accion)) {
            throw new TokenInvalidoException();
        }
        ReferenciaToken referencia = tokens.consumir(token, accion);
        if (!(referencia instanceof ReferenciaToken.Turno ref)) {
            throw new TokenInvalidoException();
        }
        Instant ahora = clock.instant();
        switch (accion) {
            case CONFIRMAR_SOLICITUD -> confirmarSolicitud(cargar(ref), ahora);
            case CONFIRMAR_TURNO -> confirmarTurno(cargar(ref));
            case CANCELAR_TURNO -> cancelar(ref, ahora);
            default -> throw new TokenInvalidoException();
        }
        Turno turno = cargar(ref);
        return new ResumenAccionResponse(accion, null, false, Motivo.ESTADO_NO_PERMITE,
                accion == PropositoToken.CANCELAR_TURNO ? turno.limiteCancelacion(limiteHoras()) : null,
                turnoResumen(turno));
    }

    private void confirmarSolicitud(Turno turno, Instant ahora) {
        if (turno.pendienteVencida(ahora)) {
            throw new TokenInvalidoException(); // revierte el consumo: la solicitud ya no se puede confirmar
        }
        turno.confirmarSolicitud();
        turnos.saveAndFlush(turno);
        eventos.publishEvent(new SolicitudConfirmada(turno.getId()));
    }

    private void confirmarTurno(Turno turno) {
        turno.confirmar();
        turnos.saveAndFlush(turno);
    }

    private void cancelar(ReferenciaToken.Turno ref, Instant ahora) {
        // Los demás enlaces del turno se invalidan antes de tocar el turno (orden de bloqueos, ver la clase).
        tokens.revocar(ref, Optional.empty());
        Turno turno = cargar(ref);
        Hueco liberado = new Hueco(turno.getInicio(), turno.getFin());
        turno.cancelarPorPaciente(ahora, limiteHoras());
        turnos.saveAndFlush(turno);
        eventos.publishEvent(new TurnoCancelado(turno.getId(), CanceladoPor.PACIENTE));
        eventos.publishEvent(HuecoLiberado.de(turno, liberado));
    }

    private ResumenAccionResponse resumen(PropositoToken accion, Instant venceEn, Turno turno, Instant ahora) {
        int limiteHoras = limiteHoras();
        boolean puede = switch (accion) {
            case CONFIRMAR_SOLICITUD -> turno.getEstado().puedePasarA(EstadoTurno.RESERVADO);
            case CONFIRMAR_TURNO -> turno.getEstado().puedePasarA(EstadoTurno.CONFIRMADO);
            default -> turno.getEstado() == EstadoTurno.RESERVADO || turno.getEstado() == EstadoTurno.CONFIRMADO;
        };
        Instant limite = accion == PropositoToken.CANCELAR_TURNO ? turno.limiteCancelacion(limiteHoras) : null;
        Motivo motivo = null;
        if (!puede) {
            motivo = Motivo.ESTADO_NO_PERMITE;
        } else if (limite != null && ahora.isAfter(limite)) {
            motivo = Motivo.FUERA_DE_LIMITE;
        }
        return new ResumenAccionResponse(accion, venceEn, motivo == null, motivo, limite, turnoResumen(turno));
    }

    private static TurnoResumen turnoResumen(Turno turno) {
        return new TurnoResumen(turno.getConsultorio().getNombre(), turno.getPrestacion().getNombre(),
                turno.getInicio(), turno.getFin(), turno.getEstado());
    }

    private int limiteHoras() {
        return consultorioService.parametrosVigentes().limiteCancelacionHoras();
    }

    private Turno cargar(ReferenciaToken referencia) {
        if (!(referencia instanceof ReferenciaToken.Turno ref)) {
            throw new TokenInvalidoException();
        }
        return turnos.findByIdAndConsultorio_Id(ref.id(), consultorioActual.id())
                .orElseThrow(TokenInvalidoException::new);
    }
}
