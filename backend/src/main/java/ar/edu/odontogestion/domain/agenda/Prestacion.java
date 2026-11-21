package ar.edu.odontogestion.domain.agenda;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Tipo de turno con duración fija (RN-AG-03). Nunca se borra: se desactiva para conservar el historial. */
@Entity
@Table(name = "prestacion")
public class Prestacion extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultorio_id", nullable = false, updatable = false)
    private Consultorio consultorio;

    @Column(nullable = false, columnDefinition = "citext")
    private String nombre;

    @Column(name = "duracion_min", nullable = false)
    private int duracionMin;

    @Column(nullable = false)
    private boolean activa = true;

    protected Prestacion() {
    }

    private Prestacion(Consultorio consultorio, String nombre, int duracionMin) {
        this.consultorio = consultorio;
        this.nombre = nombre;
        this.duracionMin = duracionMin;
    }

    /** Las reglas (no vacío, granularidad) las valida el servicio antes de llegar acá. */
    public static Prestacion nueva(Consultorio consultorio, String nombre, int duracionMin) {
        return new Prestacion(consultorio, nombre, duracionMin);
    }

    public void actualizar(String nombre, int duracionMin) {
        this.nombre = nombre;
        this.duracionMin = duracionMin;
    }

    public void activar() {
        this.activa = true;
    }

    public void desactivar() {
        this.activa = false;
    }

    public Consultorio getConsultorio() {
        return consultorio;
    }

    public String getNombre() {
        return nombre;
    }

    public int getDuracionMin() {
        return duracionMin;
    }

    public boolean isActiva() {
        return activa;
    }
}
