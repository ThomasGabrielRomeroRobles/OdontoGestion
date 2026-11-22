package ar.edu.odontogestion.domain.notificacion;

import ar.edu.odontogestion.domain.comun.AuditableEntity;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Notificación del outbox (V6, design D6). Se inserta con SQL nativo idempotente
 * ({@link NotificacionRepository#insertarSiNoExiste}); por JPA solo se lee y se actualiza su estado.
 * Los datos de origen (tipo, destinatario, referencia, variables y enlaces) son inmutables.
 */
@Entity
@Table(name = "notificacion")
public class Notificacion extends AuditableEntity {

    @Column(name = "consultorio_id", nullable = false, updatable = false)
    private Long consultorioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TipoNotificacion tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CanalNotificacion canal;

    @Column(nullable = false, updatable = false, columnDefinition = "citext")
    private String destinatario;

    @Column(name = "turno_id", updatable = false)
    private Long turnoId;

    @Column(name = "aviso_id", updatable = false)
    private Long avisoId;

    @Column(name = "entrada_lista_espera_id", updatable = false)
    private Long entradaListaEsperaId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, String> datos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<EnlaceProgramado> enlaces;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoNotificacion estado;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "reintentos_manuales", nullable = false)
    private int reintentosManuales;

    @Column
    private String error;

    @Column(name = "programada_para", nullable = false)
    private Instant programadaPara;

    @Column(name = "enviada_en")
    private Instant enviadaEn;

    protected Notificacion() {
    }

    public ReferenciaToken referencia() {
        if (turnoId != null) {
            return new ReferenciaToken.Turno(turnoId);
        }
        if (avisoId != null) {
            return new ReferenciaToken.Aviso(avisoId);
        }
        return new ReferenciaToken.EntradaListaEspera(entradaListaEsperaId);
    }

    public Long getConsultorioId() {
        return consultorioId;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public CanalNotificacion getCanal() {
        return canal;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public Long getAvisoId() {
        return avisoId;
    }

    public Long getEntradaListaEsperaId() {
        return entradaListaEsperaId;
    }

    public Map<String, String> getDatos() {
        return datos == null ? Map.of() : Map.copyOf(datos);
    }

    public List<EnlaceProgramado> getEnlaces() {
        return enlaces == null ? List.of() : List.copyOf(enlaces);
    }

    public EstadoNotificacion getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public int getReintentosManuales() {
        return reintentosManuales;
    }

    public String getError() {
        return error;
    }

    public Instant getProgramadaPara() {
        return programadaPara;
    }

    public Instant getEnviadaEn() {
        return enviadaEn;
    }
}
