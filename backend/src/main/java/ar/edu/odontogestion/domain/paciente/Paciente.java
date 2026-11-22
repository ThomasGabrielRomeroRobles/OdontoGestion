package ar.edu.odontogestion.domain.paciente;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.usuario.Emails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Paciente del consultorio: sin cuenta ni contraseña (RN-PA-02), identificado por DNI único por consultorio
 * (RN-PA-01). Sin setters públicos: el alta y la edición normalizan DNI, email y espacios.
 * {@code cantidadAusencias} y {@code activo} solo cambian por casos de uso propios (C-08 y la baja lógica).
 */
@Entity
@Table(name = "paciente")
public class Paciente extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultorio_id", nullable = false, updatable = false)
    private Consultorio consultorio;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(nullable = false)
    private String dni;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(nullable = false)
    private String telefono;

    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(nullable = false)
    private String direccion = "";

    @Column(name = "cantidad_ausencias", nullable = false)
    private int cantidadAusencias;

    @Column(nullable = false)
    private boolean activo = true;

    protected Paciente() {
    }

    private Paciente(Consultorio consultorio) {
        this.consultorio = consultorio;
    }

    public static Paciente nuevo(Consultorio consultorio, DatosPaciente datos) {
        Paciente p = new Paciente(consultorio);
        p.actualizarDatos(datos);
        return p;
    }

    /** Reemplaza los datos administrativos; no toca ausencias ni estado. */
    public void actualizarDatos(DatosPaciente datos) {
        this.dni = Dni.normalizar(datos.dni());
        this.nombre = recortar(datos.nombre());
        this.apellido = recortar(datos.apellido());
        this.telefono = recortar(datos.telefono());
        this.email = Emails.normalizar(datos.email());
        this.direccion = datos.direccion() == null ? "" : datos.direccion().trim();
        this.fechaNacimiento = datos.fechaNacimiento();
    }

    private static String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }

    public Consultorio getConsultorio() {
        return consultorio;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String getDni() {
        return dni;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getEmail() {
        return email;
    }

    public String getDireccion() {
        return direccion;
    }

    public int getCantidadAusencias() {
        return cantidadAusencias;
    }

    public boolean isActivo() {
        return activo;
    }
}
