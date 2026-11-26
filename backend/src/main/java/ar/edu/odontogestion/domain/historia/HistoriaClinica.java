package ar.edu.odontogestion.domain.historia;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.paciente.Paciente;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Historia clínica del paciente: una sola por paciente (RN-HC-01), raíz del agregado clínico. Se crea en la
 * primera escritura clínica con un INSERT idempotente ({@link HistoriaClinicaRepository#insertarSiNoExiste}).
 * Sin {@code toString} propio: ninguna entidad clínica vuelca su contenido en logs (RN-HC-06).
 */
@Entity
@Table(name = "historia_clinica")
public class HistoriaClinica extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultorio_id", nullable = false, updatable = false)
    private Consultorio consultorio;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false, updatable = false)
    private Paciente paciente;

    protected HistoriaClinica() {
    }

    public Consultorio getConsultorio() {
        return consultorio;
    }

    public Paciente getPaciente() {
        return paciente;
    }
}
