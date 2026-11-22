package ar.edu.odontogestion.domain.turno;

import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.paciente.Paciente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;

/**
 * Turno de la agenda (design D4). Sus estados cambian solo por los métodos de dominio, que aplican la tabla de
 * {@link EstadoTurno}. Que dos turnos que ocupan agenda no se superpongan lo garantiza la base
 * ({@code ex_turno_sin_superposicion}, regla dura #8), nunca esta clase. {@code version} detecta modificaciones
 * concurrentes del mismo turno (D9).
 */
@Entity
@Table(name = "turno")
public class Turno extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultorio_id", nullable = false, updatable = false)
    private Consultorio consultorio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false, updatable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestacion_id", nullable = false, updatable = false)
    private Prestacion prestacion;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTurno estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OrigenTurno origen;

    @Column(name = "pendiente_hasta")
    private Instant pendienteHasta;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelado_por")
    private CanceladoPor canceladoPor;

    @Version
    @Column(nullable = false)
    private long version;

    protected Turno() {
    }

    private Turno(Consultorio consultorio, Paciente paciente, Prestacion prestacion, Instant inicio,
            OrigenTurno origen, EstadoTurno estado, Instant pendienteHasta) {
        this.consultorio = consultorio;
        this.paciente = paciente;
        this.prestacion = prestacion;
        this.inicio = inicio;
        this.fin = inicio.plus(Duration.ofMinutes(prestacion.getDuracionMin()));
        this.origen = origen;
        this.estado = estado;
        this.pendienteHasta = pendienteHasta;
    }

    /** Turno confirmado por el consultorio: {@code fin = inicio + duración de la prestación}. */
    public static Turno reservado(Consultorio consultorio, Paciente paciente, Prestacion prestacion, Instant inicio,
            OrigenTurno origen) {
        return new Turno(consultorio, paciente, prestacion, inicio, origen, EstadoTurno.RESERVADO, null);
    }

    /** Solicitud que retiene el hueco hasta {@code pendienteHasta} (RN-PU-02). */
    public static Turno pendiente(Consultorio consultorio, Paciente paciente, Prestacion prestacion, Instant inicio,
            OrigenTurno origen, Instant pendienteHasta) {
        return new Turno(consultorio, paciente, prestacion, inicio, origen, EstadoTurno.PENDIENTE_CONFIRMACION,
                pendienteHasta);
    }

    /** {@code RESERVADO → CONFIRMADO} (el paciente confirma por su enlace, C-11). */
    public void confirmar() {
        transicionarA(EstadoTurno.CONFIRMADO);
    }

    /** {@code PENDIENTE_CONFIRMACION → RESERVADO} (el paciente confirma la solicitud pública, C-09). */
    public void confirmarSolicitud() {
        transicionarA(EstadoTurno.RESERVADO);
    }

    public void marcarAtendido() {
        transicionarA(EstadoTurno.ATENDIDO);
    }

    /** La transición a {@code AUSENTE} la dispara C-08; la máquina de estados ya la admite. */
    public void marcarAusente() {
        transicionarA(EstadoTurno.AUSENTE);
    }

    /** {@code PENDIENTE_CONFIRMACION → EXPIRADO}: libera el hueco (la expiración masiva la hace el repositorio). */
    public void expirar() {
        transicionarA(EstadoTurno.EXPIRADO);
    }

    /** Cancela el turno; un motivo en blanco se guarda como {@code null}. */
    public void cancelar(CanceladoPor quien, String motivo) {
        transicionarA(EstadoTurno.CANCELADO);
        this.canceladoPor = quien;
        this.motivoCancelacion = motivo == null || motivo.isBlank() ? null : motivo.strip();
    }

    private void transicionarA(EstadoTurno destino) {
        if (!estado.puedePasarA(destino)) {
            throw new TransicionInvalidaException(estado, destino);
        }
        this.estado = destino;
    }

    public Consultorio getConsultorio() {
        return consultorio;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public Prestacion getPrestacion() {
        return prestacion;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public OrigenTurno getOrigen() {
        return origen;
    }

    public Instant getPendienteHasta() {
        return pendienteHasta;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public CanceladoPor getCanceladoPor() {
        return canceladoPor;
    }

    public long getVersion() {
        return version;
    }
}
