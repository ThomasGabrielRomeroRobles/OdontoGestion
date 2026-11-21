package ar.edu.odontogestion.domain.agenda;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;

/**
 * Franja semanal de atención, en hora local del consultorio. Que no se solape con otra del mismo día lo garantiza
 * la base ({@code ex_horario_trabajo_sin_solapamiento}).
 */
@Entity
@Table(name = "horario_trabajo")
public class HorarioTrabajo extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultorio_id", nullable = false, updatable = false)
    private Consultorio consultorio;

    /** {@code short} mapea a {@code smallint}: con {@code int} fallaría {@code ddl-auto=validate} (int2 vs int4). */
    @Column(name = "dia_semana", nullable = false)
    private short diaSemana;

    /*
     * Hora local del consultorio, sin zona. LocalTimeJdbcType la envía tal cual al driver (setObject): el mapeo por
     * defecto pasa por java.sql.Time con la zona de la JVM y, con hibernate.jdbc.time_zone=UTC, corría las horas
     * (09:00 se guardaba como 12:00 en una JVM en UTC-3, p. ej. Windows en Argentina).
     */
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "hora_desde", nullable = false)
    private LocalTime horaDesde;

    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "hora_hasta", nullable = false)
    private LocalTime horaHasta;

    protected HorarioTrabajo() {
    }

    private HorarioTrabajo(Consultorio consultorio, int diaSemana, LocalTime horaDesde, LocalTime horaHasta) {
        this.consultorio = consultorio;
        actualizar(diaSemana, horaDesde, horaHasta);
    }

    /** {@code diaSemana}: 1 = lunes … 7 = domingo (ISO). Las reglas las valida el servicio. */
    public static HorarioTrabajo nuevo(Consultorio consultorio, int diaSemana, LocalTime horaDesde,
            LocalTime horaHasta) {
        return new HorarioTrabajo(consultorio, diaSemana, horaDesde, horaHasta);
    }

    public void actualizar(int diaSemana, LocalTime horaDesde, LocalTime horaHasta) {
        this.diaSemana = (short) diaSemana;
        this.horaDesde = horaDesde;
        this.horaHasta = horaHasta;
    }

    /** Intervalo semiabierto {@code [desde, hasta)}: franjas contiguas no se solapan. */
    public boolean seSolapaCon(int otroDia, LocalTime desde, LocalTime hasta) {
        return diaSemana == otroDia && horaDesde.isBefore(hasta) && desde.isBefore(horaHasta);
    }

    public Consultorio getConsultorio() {
        return consultorio;
    }

    public int getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getHoraDesde() {
        return horaDesde;
    }

    public LocalTime getHoraHasta() {
        return horaHasta;
    }
}
