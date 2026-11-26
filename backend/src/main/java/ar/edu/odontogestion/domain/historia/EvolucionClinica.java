package ar.edu.odontogestion.domain.historia;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Nota de evolución (US-072, RN-HC-04): autor, fecha civil de la consulta y turno opcional. En el MVP la edita solo
 * su autor dentro de una ventana desde el alta (C-05 design D5); la etapa posterior la vuelve append-only.
 */
@Entity
@Table(name = "evolucion_clinica")
public class EvolucionClinica extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "historia_id", nullable = false, updatable = false)
    private HistoriaClinica historia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario autor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_id", updatable = false)
    private Turno turno;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private String nota;

    @Column(name = "editada_en")
    private Instant editadaEn;

    protected EvolucionClinica() {
    }

    public static EvolucionClinica nueva(HistoriaClinica historia, Usuario autor, Turno turno, LocalDate fecha,
            String nota) {
        EvolucionClinica e = new EvolucionClinica();
        e.historia = historia;
        e.autor = autor;
        e.turno = turno;
        e.fecha = fecha;
        e.nota = nota.trim();
        return e;
    }

    /** {@code true} si el usuario es el autor y {@code ahora} no pasó del alta + ventana (límite inclusivo). */
    public boolean esEditablePor(Long usuarioId, Instant ahora, Duration ventana) {
        return esAutor(usuarioId) && dentroDeVentana(ahora, ventana);
    }

    /** Edita nota y fecha; conserva el alta y registra la fecha de edición. Valida autor (403) y ventana (422). */
    public void editar(Long usuarioId, LocalDate nuevaFecha, String nuevaNota, Instant ahora, Duration ventana) {
        if (!esAutor(usuarioId)) {
            throw new NoEsAutorException();
        }
        if (!dentroDeVentana(ahora, ventana)) {
            throw new EvolucionFueraDeVentanaException();
        }
        this.fecha = nuevaFecha;
        this.nota = nuevaNota.trim();
        this.editadaEn = ahora;
    }

    private boolean esAutor(Long usuarioId) {
        return Objects.equals(autor.getId(), usuarioId);
    }

    private boolean dentroDeVentana(Instant ahora, Duration ventana) {
        return !ahora.isAfter(getCreadoEn().plus(ventana));
    }

    public HistoriaClinica getHistoria() {
        return historia;
    }

    public Usuario getAutor() {
        return autor;
    }

    public Turno getTurno() {
        return turno;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getNota() {
        return nota;
    }

    public Instant getEditadaEn() {
        return editadaEn;
    }
}
