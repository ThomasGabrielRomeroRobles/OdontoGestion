package ar.edu.odontogestion.domain.usuario;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Odontólogo: único usuario con login (DD-01). Sin setters públicos: solo métodos de dominio. */
@Entity
@Table(name = "usuario")
public class Usuario extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultorio_id", nullable = false, updatable = false)
    private Consultorio consultorio;

    @Column(nullable = false, updatable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(nullable = false)
    private String matricula = "";

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "ultimo_acceso")
    private Instant ultimoAcceso;

    protected Usuario() {
    }

    private Usuario(Consultorio consultorio, String email, String passwordHash, String nombre, String apellido,
            String matricula) {
        this.consultorio = consultorio;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.apellido = apellido;
        this.matricula = matricula;
    }

    public static Usuario nuevo(Consultorio consultorio, String email, String passwordHash, String nombre,
            String apellido, String matricula) {
        return new Usuario(consultorio, email, passwordHash, nombre, apellido, matricula);
    }

    public void registrarAcceso(Instant instante) {
        if (instante == null) {
            throw new IllegalArgumentException("El instante de acceso es obligatorio");
        }
        this.ultimoAcceso = instante;
    }

    public void cambiarPasswordHash(String nuevoHash) {
        if (nuevoHash == null || nuevoHash.isBlank()) {
            throw new IllegalArgumentException("El hash de contraseña no puede estar vacío");
        }
        this.passwordHash = nuevoHash;
    }

    /** El email es el identificador de login y no se edita desde el perfil. */
    public void actualizarPerfil(String nombre, String apellido, String matricula) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.matricula = matricula;
    }

    public Consultorio getConsultorio() {
        return consultorio;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String getMatricula() {
        return matricula;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getUltimoAcceso() {
        return ultimoAcceso;
    }
}
