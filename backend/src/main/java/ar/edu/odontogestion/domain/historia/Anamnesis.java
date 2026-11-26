package ar.edu.odontogestion.domain.historia;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Anamnesis de la historia (US-070): una por historia, se reemplaza completa en cada guardado. Registra autor y
 * fecha del último guardado ({@code actualizadoPor} + {@code actualizadoEn}, RN-HC-05 en su versión MVP).
 */
@Entity
@Table(name = "anamnesis")
public class Anamnesis extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "historia_id", nullable = false, updatable = false)
    private HistoriaClinica historia;

    @Column(nullable = false)
    private String alergias = "";

    @Column(name = "medicacion_actual", nullable = false)
    private String medicacionActual = "";

    @Column(name = "enfermedades_previas", nullable = false)
    private String enfermedadesPrevias = "";

    @Column(name = "antecedentes_relevantes", nullable = false)
    private String antecedentesRelevantes = "";

    @Column
    private Boolean embarazo;

    @Column(nullable = false)
    private String observaciones = "";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actualizado_por_id", nullable = false)
    private Usuario actualizadoPor;

    protected Anamnesis() {
    }

    public static Anamnesis nueva(HistoriaClinica historia, DatosAnamnesis datos, Usuario autor) {
        Anamnesis a = new Anamnesis();
        a.historia = historia;
        a.reemplazar(datos, autor);
        return a;
    }

    /** Reemplaza todo el contenido (los campos omitidos quedan vacíos) y fija el autor. */
    public void reemplazar(DatosAnamnesis datos, Usuario autor) {
        this.alergias = texto(datos.alergias());
        this.medicacionActual = texto(datos.medicacionActual());
        this.enfermedadesPrevias = texto(datos.enfermedadesPrevias());
        this.antecedentesRelevantes = texto(datos.antecedentesRelevantes());
        this.embarazo = datos.embarazo();
        this.observaciones = texto(datos.observaciones());
        this.actualizadoPor = autor;
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    public HistoriaClinica getHistoria() {
        return historia;
    }

    public String getAlergias() {
        return alergias;
    }

    public String getMedicacionActual() {
        return medicacionActual;
    }

    public String getEnfermedadesPrevias() {
        return enfermedadesPrevias;
    }

    public String getAntecedentesRelevantes() {
        return antecedentesRelevantes;
    }

    public Boolean getEmbarazo() {
        return embarazo;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public Usuario getActualizadoPor() {
        return actualizadoPor;
    }
}
