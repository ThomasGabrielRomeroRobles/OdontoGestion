package ar.edu.odontogestion.domain.agenda;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Franja bloqueada de la agenda (vacaciones, imprevistos), en UTC. Los bloqueos pueden solaparse entre sí. */
@Entity
@Table(name = "bloqueo_agenda")
public class BloqueoAgenda extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultorio_id", nullable = false, updatable = false)
    private Consultorio consultorio;

    @Column(nullable = false)
    private Instant desde;

    @Column(nullable = false)
    private Instant hasta;

    /** Opcional: sin motivo se guarda {@code ""}, nunca {@code null} (convención de C-02). */
    @Column(nullable = false)
    private String motivo = "";

    protected BloqueoAgenda() {
    }

    private BloqueoAgenda(Consultorio consultorio, Instant desde, Instant hasta, String motivo) {
        this.consultorio = consultorio;
        actualizar(desde, hasta, motivo);
    }

    public static BloqueoAgenda nuevo(Consultorio consultorio, Instant desde, Instant hasta, String motivo) {
        return new BloqueoAgenda(consultorio, desde, hasta, motivo);
    }

    public void actualizar(Instant desde, Instant hasta, String motivo) {
        this.desde = desde;
        this.hasta = hasta;
        this.motivo = motivo == null ? "" : motivo;
    }

    public Consultorio getConsultorio() {
        return consultorio;
    }

    public Instant getDesde() {
        return desde;
    }

    public Instant getHasta() {
        return hasta;
    }

    public String getMotivo() {
        return motivo;
    }
}
