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

    /** Error máximo guardado (coincide con el {@code CHECK} de V6). */
    public static final int MAX_ERROR = 500;

    /** Envío aceptado por el canal. */
    public void marcarEnviada(Instant ahora) {
        this.intentos++;
        this.estado = EstadoNotificacion.ENVIADA;
        this.enviadaEn = ahora;
        this.error = null;
    }

    /** Intento fallido con reintento programado. */
    public void registrarFalloConReintento(String error, Instant programadaPara) {
        this.intentos++;
        this.error = truncar(error);
        this.programadaPara = programadaPara;
    }

    /** Intento fallido sin más reintentos automáticos. */
    public void registrarFalloDefinitivo(String error) {
        this.intentos++;
        this.estado = EstadoNotificacion.FALLIDA;
        this.error = truncar(error);
    }

    /** Descartada sin intentar el envío (p. ej. enlace vencido): no cuenta como intento. */
    public void descartar(String error) {
        this.estado = EstadoNotificacion.FALLIDA;
        this.error = truncar(error);
    }

    private static String truncar(String error) {
        if (error == null || error.isBlank()) {
            return "Fallo de envío sin detalle";
        }
        return error.length() > MAX_ERROR ? error.substring(0, MAX_ERROR) : error;
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
