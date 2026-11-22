package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import ar.edu.odontogestion.domain.turno.OrigenTurno;
import ar.edu.odontogestion.domain.turno.evento.SolicitudConfirmada;
import ar.edu.odontogestion.domain.turno.evento.TurnoCancelado;
import ar.edu.odontogestion.domain.turno.evento.TurnoCreado;
import ar.edu.odontogestion.domain.turno.evento.TurnoMovido;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisos al paciente por cambios en su turno (design D10/D11). Corre en {@code BEFORE_COMMIT}: la fila del outbox se
 * inserta en la <b>misma transacción</b> que el cambio del turno, así que si el turno se revierte no queda una
 * notificación fantasma, y un fallo posterior del envío (lo hace el scheduler de C-03) no revierte nada (RN-NO-06).
 *
 * <p>Contenido: solo consultorio, instante de inicio (ISO-8601 UTC) y nombre de la prestación, exactamente las tres
 * variables que exigen las plantillas {@code CONFIRMACION_TURNO} y {@code CANCELACION} de C-03. Sin DNI, sin token
 * (los enlaces los suman C-09/C-11) y sin datos clínicos (regla dura #10). Los logs llevan solo el id del turno.
 */
@Component
public class NotificacionesTurnoListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesTurnoListener.class);

    private final TurnoRepository turnos;
    private final NotificationService notificaciones;

    public NotificacionesTurnoListener(TurnoRepository turnos, NotificationService notificaciones) {
        this.turnos = turnos;
        this.notificaciones = notificaciones;
    }

    /**
     * Un turno que nace {@code RESERVADO} se confirma al paciente. Una solicitud pública {@code PENDIENTE_CONFIRMACION}
     * recibe el mail con el enlace {@code CONFIRMAR_SOLICITUD}, que vence en {@code pendiente_hasta} (C-09 D2).
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void alCrear(TurnoCreado evento) {
        if (evento.estado() == EstadoTurno.RESERVADO) {
            turnos.findById(evento.turnoId()).ifPresent(t -> encolar(TipoNotificacion.CONFIRMACION_TURNO, t,
                    t.getInicio(), List.of()));
        } else if (evento.estado() == EstadoTurno.PENDIENTE_CONFIRMACION && evento.origen() == OrigenTurno.PUBLICA) {
            turnos.findById(evento.turnoId()).ifPresent(t -> encolar(TipoNotificacion.CONFIRMAR_SOLICITUD, t,
                    t.getInicio(), List.of(new EnlaceProgramado(PropositoToken.CONFIRMAR_SOLICITUD,
                            t.getPendienteHasta()))));
        }
    }

    /** Confirmada la solicitud por el paciente: se le avisa que el turno quedó reservado (C-09 D6). */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void alConfirmarSolicitud(SolicitudConfirmada evento) {
        turnos.findById(evento.turnoId()).ifPresent(t -> encolar(TipoNotificacion.CONFIRMACION_TURNO, t,
                t.getInicio(), List.of()));
    }

    /** Mover re-notifica con el horario nuevo (RN-NO-07: {@code CONFIRMACION_TURNO} puede repetirse). */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void alMover(TurnoMovido evento) {
        turnos.findById(evento.turnoId()).ifPresent(t -> encolar(TipoNotificacion.CONFIRMACION_TURNO, t,
                evento.inicioNuevo(), List.of()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void alCancelar(TurnoCancelado evento) {
        Optional<Turno> turno = turnos.findById(evento.turnoId());
        if (turno.isEmpty()) {
            log.warn("No se encolan avisos: el turno {} ya no existe", evento.turnoId());
            return;
        }
        encolar(TipoNotificacion.CANCELACION, turno.get(), turno.get().getInicio(), List.of());
    }

    private void encolar(TipoNotificacion tipo, Turno turno, Instant inicio, List<EnlaceProgramado> enlaces) {
        String email = turno.getPaciente().getEmail();
        if (email == null || email.isBlank()) {
            log.warn("No se encola {} del turno {}: el paciente no tiene email", tipo, turno.getId());
            return;
        }
        Map<String, String> datos = Map.of(
                "consultorio", turno.getConsultorio().getNombre(),
                "inicio", inicio.toString(),
                "prestacion", turno.getPrestacion().getNombre());
        notificaciones.encolar(tipo, email, new ReferenciaToken.Turno(turno.getId()), datos, enlaces);
    }
}
