package ar.edu.odontogestion.domain.consultorio;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Consultorio (único en el MVP, pero toda entidad de negocio lo referencia desde el día 1). */
@Entity
@Table(name = "consultorio")
public class Consultorio extends AuditableEntity {

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private String direccion = "";

    @Column(nullable = false)
    private String telefono = "";

    @Column(name = "email_contacto", nullable = false, columnDefinition = "citext")
    private String emailContacto = "";

    @Column(name = "zona_horaria", nullable = false, updatable = false)
    private String zonaHoraria;

    @Embedded
    private ParametrosConsultorio parametros = ParametrosConsultorio.porDefecto();

    protected Consultorio() {
    }

    private Consultorio(String nombre, String zonaHoraria) {
        this.nombre = nombre;
        this.zonaHoraria = zonaHoraria;
    }

    public static Consultorio nuevo(String nombre, String zonaHoraria) {
        return new Consultorio(nombre, zonaHoraria);
    }

    /** Actualiza los datos editables; la zona horaria nunca cambia. */
    public void actualizarDatos(String nombre, String direccion, String telefono, String emailContacto) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.telefono = telefono;
        this.emailContacto = emailContacto;
    }

    public void actualizarParametros(ParametrosConsultorio parametros) {
        this.parametros = parametros;
    }

    public ParametrosConsultorio getParametros() {
        return parametros;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getEmailContacto() {
        return emailContacto;
    }

    public String getZonaHoraria() {
        return zonaHoraria;
    }
}
