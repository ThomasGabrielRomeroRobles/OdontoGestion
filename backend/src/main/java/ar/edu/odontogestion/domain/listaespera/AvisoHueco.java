package ar.edu.odontogestion.domain.listaespera;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Oferta de un hueco liberado a una entrada de la lista de espera (C-10 D1). Los avisos del mismo hueco comparten
 * {@code (turnoOrigenId, inicio)}; aceptar uno cierra los demás. Solo {@code ENVIADO} admite transiciones.
 */
@Entity
@Table(name = "aviso_hueco")
public class AvisoHueco extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultorio_id", nullable = false, updatable = false)
    private Consultorio consultorio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entrada_lista_espera_id", nullable = false, updatable = false)
    private EntradaListaEspera entrada;

    @Column(name = "turno_origen_id", nullable = false, updatable = false)
    private Long turnoOrigenId;

    @Column(name = "prestacion_id", nullable = false, updatable = false)
    private Long prestacionId;

    @Column(nullable = false, updatable = false)
    private Instant inicio;

    @Column(nullable = false, updatable = false)
    private Instant fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAviso estado;

    @Column(name = "vence_en", nullable = false, updatable = false)
    private Instant venceEn;

    @Version
    @Column(nullable = false)
    private long version;

    protected AvisoHueco() {
    }

    private AvisoHueco(Consultorio consultorio, EntradaListaEspera entrada, Long turnoOrigenId, Long prestacionId,
            Instant inicio, Instant fin, Instant venceEn) {
        this.consultorio = consultorio;
        this.entrada = entrada;
        this.turnoOrigenId = turnoOrigenId;
        this.prestacionId = prestacionId;
        this.inicio = inicio;
        this.fin = fin;
        this.venceEn = venceEn;
        this.estado = EstadoAviso.ENVIADO;
    }

    public static AvisoHueco enviado(Consultorio consultorio, EntradaListaEspera entrada, Long turnoOrigenId,
            Long prestacionId, Instant inicio, Instant fin, Instant venceEn) {
        return new AvisoHueco(consultorio, entrada, turnoOrigenId, prestacionId, inicio, fin, venceEn);
    }

    /** {@code ENVIADO → ACEPTADO}. */
    public void aceptar() {
        transicionarA(EstadoAviso.ACEPTADO);
    }

    /** {@code ENVIADO → CERRADO}: otro aviso del mismo hueco ganó, o el hueco se ocupó. */
    public void cerrar() {
        transicionarA(EstadoAviso.CERRADO);
    }

    /** {@code ENVIADO → EXPIRADO}: pasó {@code vence_en} sin respuesta. */
    public void expirar() {
        transicionarA(EstadoAviso.EXPIRADO);
    }

    /** {@code true} si {@code vence_en <= ahora}. */
    public boolean vencido(Instant ahora) {
        return !venceEn.isAfter(ahora);
    }

    private void transicionarA(EstadoAviso destino) {
        if (estado != EstadoAviso.ENVIADO) {
            throw new TransicionListaEsperaInvalidaException("un aviso de hueco", estado, destino);
        }
        this.estado = destino;
    }

    public Consultorio getConsultorio() {
        return consultorio;
    }

    public EntradaListaEspera getEntrada() {
        return entrada;
    }

    public Long getTurnoOrigenId() {
        return turnoOrigenId;
    }

    public Long getPrestacionId() {
        return prestacionId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public EstadoAviso getEstado() {
        return estado;
    }

    public Instant getVenceEn() {
        return venceEn;
    }

    public long getVersion() {
        return version;
    }
}
