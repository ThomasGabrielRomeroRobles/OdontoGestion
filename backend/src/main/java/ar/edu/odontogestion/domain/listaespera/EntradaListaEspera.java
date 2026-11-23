package ar.edu.odontogestion.domain.listaespera;

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
import java.time.Instant;
import java.time.LocalDate;

/**
 * Pedido de un paciente de ser avisado cuando se libere un horario de una prestación dentro de un rango de fechas
 * (C-10 D1). Los estados cambian solo por los métodos de dominio. Que no haya dos entradas vigentes del mismo
 * paciente y prestación lo garantiza la base ({@code uq_entrada_lista_espera_vigente}); {@code version} detecta
 * modificaciones concurrentes.
 */
@Entity
@Table(name = "entrada_lista_espera")
public class EntradaListaEspera extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultorio_id", nullable = false, updatable = false)
    private Consultorio consultorio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false, updatable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestacion_id", nullable = false, updatable = false)
    private Prestacion prestacion;

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta", nullable = false)
    private LocalDate fechaHasta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEntrada estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OrigenEntrada origen;

    @Column(name = "pendiente_hasta")
    private Instant pendienteHasta;

    @Column(name = "turno_resultante_id")
    private Long turnoResultanteId;

    @Version
    @Column(nullable = false)
    private long version;

    protected EntradaListaEspera() {
    }

    private EntradaListaEspera(Consultorio consultorio, Paciente paciente, Prestacion prestacion, LocalDate desde,
            LocalDate hasta, OrigenEntrada origen, Instant pendienteHasta) {
        this.consultorio = consultorio;
        this.paciente = paciente;
        this.prestacion = prestacion;
        this.fechaDesde = desde;
        this.fechaHasta = hasta;
        this.origen = origen;
        this.estado = EstadoEntrada.PENDIENTE_CONFIRMACION;
        this.pendienteHasta = pendienteHasta;
    }

    /** Entrada nueva que espera la confirmación del paciente hasta {@code pendienteHasta}. */
    public static EntradaListaEspera pendiente(Consultorio consultorio, Paciente paciente, Prestacion prestacion,
            LocalDate desde, LocalDate hasta, OrigenEntrada origen, Instant pendienteHasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta");
        }
        return new EntradaListaEspera(consultorio, paciente, prestacion, desde, hasta, origen, pendienteHasta);
    }

    /** {@code PENDIENTE_CONFIRMACION → ACTIVA}. */
    public void confirmar() {
        transicionarA(EstadoEntrada.ACTIVA, estado == EstadoEntrada.PENDIENTE_CONFIRMACION);
        this.pendienteHasta = null;
    }

    /** {@code PENDIENTE_CONFIRMACION | ACTIVA → CANCELADA}. */
    public void cancelar() {
        transicionarA(EstadoEntrada.CANCELADA, estado.vigente());
    }

    /** {@code ACTIVA → CUBIERTA}: el paciente aceptó un aviso y quedó con ese turno. */
    public void cubrir(Long turnoId) {
        transicionarA(EstadoEntrada.CUBIERTA, estado == EstadoEntrada.ACTIVA);
        this.turnoResultanteId = turnoId;
    }

    /** {@code PENDIENTE_CONFIRMACION → VENCIDA}: el paciente no confirmó a tiempo. */
    public void vencerPendiente() {
        transicionarA(EstadoEntrada.VENCIDA, estado == EstadoEntrada.PENDIENTE_CONFIRMACION);
    }

    /** {@code true} si está pendiente y su plazo de confirmación ya llegó (aunque no se haya marcado vencida). */
    public boolean pendienteVencida(Instant ahora) {
        return estado == EstadoEntrada.PENDIENTE_CONFIRMACION && pendienteHasta != null
                && !pendienteHasta.isAfter(ahora);
    }

    private void transicionarA(EstadoEntrada destino, boolean permitida) {
        if (!permitida) {
            throw new TransicionListaEsperaInvalidaException("una entrada de lista de espera", estado, destino);
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

    public LocalDate getFechaDesde() {
        return fechaDesde;
    }

    public LocalDate getFechaHasta() {
        return fechaHasta;
    }

    public EstadoEntrada getEstado() {
        return estado;
    }

    public OrigenEntrada getOrigen() {
        return origen;
    }

    public Instant getPendienteHasta() {
        return pendienteHasta;
    }

    public Long getTurnoResultanteId() {
        return turnoResultanteId;
    }

    public long getVersion() {
        return version;
    }
}
