package ar.edu.odontogestion.domain.historia;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Estado vigente de una pieza del odontograma (US-071): una fila por historia y pieza; registra autor y fecha. */
@Entity
@Table(name = "pieza_dental_estado")
public class PiezaDentalEstado extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "historia_id", nullable = false, updatable = false)
    private HistoriaClinica historia;

    @Column(name = "pieza_fdi", nullable = false, updatable = false)
    private short piezaFdi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPieza estado;

    @Column(nullable = false)
    private String observacion = "";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actualizado_por_id", nullable = false)
    private Usuario actualizadoPor;

    protected PiezaDentalEstado() {
    }

    public static PiezaDentalEstado nueva(HistoriaClinica historia, PiezaFdi pieza, EstadoPieza estado,
            String observacion, Usuario autor) {
        PiezaDentalEstado p = new PiezaDentalEstado();
        p.historia = historia;
        p.piezaFdi = (short) pieza.numero();
        p.actualizar(estado, observacion, autor);
        return p;
    }

    public void actualizar(EstadoPieza estado, String observacion, Usuario autor) {
        this.estado = estado;
        this.observacion = observacion == null ? "" : observacion.trim();
        this.actualizadoPor = autor;
    }

    public HistoriaClinica getHistoria() {
        return historia;
    }

    public int getPiezaFdi() {
        return piezaFdi;
    }

    public EstadoPieza getEstado() {
        return estado;
    }

    public String getObservacion() {
        return observacion;
    }

    public Usuario getActualizadoPor() {
        return actualizadoPor;
    }
}
