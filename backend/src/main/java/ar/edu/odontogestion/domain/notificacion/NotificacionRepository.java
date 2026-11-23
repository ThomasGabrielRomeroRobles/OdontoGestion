package ar.edu.odontogestion.domain.notificacion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda consulta filtra por consultorio (regla dura #6). */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    /** Registro de envíos (design D13): filtros opcionales; el orden lo da el {@link Pageable}. */
    @Query("""
            SELECT n FROM Notificacion n
             WHERE n.consultorioId = :consultorioId
               AND (:estado IS NULL OR n.estado = :estado)
               AND (:tipo IS NULL OR n.tipo = :tipo)
            """)
    Page<Notificacion> buscar(@Param("consultorioId") Long consultorioId, @Param("estado") EstadoNotificacion estado,
            @Param("tipo") TipoNotificacion tipo, Pageable pageable);

    Optional<Notificacion> findByIdAndConsultorioId(Long id, Long consultorioId);

    /** Candidatas del procesador (design D10): pendientes cuyo momento llegó, las más antiguas primero. */
    @Query(nativeQuery = true, value = """
            SELECT id FROM notificacion
             WHERE consultorio_id = :consultorioId AND estado = 'PENDIENTE' AND programada_para <= :ahora
             ORDER BY programada_para, id
             LIMIT :lote
            """)
    List<Long> idsPendientes(@Param("consultorioId") Long consultorioId, @Param("ahora") Instant ahora,
            @Param("lote") int lote);

    /**
     * Toma la notificación para procesarla re-verificando estado y momento. {@code SKIP LOCKED}: si otra
     * ejecución ya la tiene bloqueada devuelve vacío, así dos procesamientos superpuestos no envían dos veces.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM notificacion
             WHERE id = :id AND estado = 'PENDIENTE' AND programada_para <= :ahora
             FOR UPDATE SKIP LOCKED
            """)
    Optional<Notificacion> tomarParaProcesar(@Param("id") Long id, @Param("ahora") Instant ahora);

    /**
     * Inserción idempotente (design D7): {@code ON CONFLICT DO NOTHING} sobre los índices únicos parciales.
     * En PostgreSQL una violación de unicidad abortaría la transacción de origen; así, el duplicado se ignora
     * y la transacción sigue sana (RN-NO-06).
     *
     * @param datos JSON con las variables de plantilla
     * @param enlaces JSON con la lista de {@link EnlaceProgramado}
     * @return 1 si se insertó, 0 si ya existía
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notificacion (consultorio_id, tipo, canal, destinatario, turno_id, aviso_id,
                                      entrada_lista_espera_id, datos, enlaces, programada_para,
                                      creado_en, actualizado_en)
            VALUES (:consultorioId, :tipo, :canal, :destinatario, :turnoId, :avisoId, :entradaId,
                    CAST(:datos AS jsonb), CAST(:enlaces AS jsonb), :ahora, :ahora, :ahora)
            ON CONFLICT DO NOTHING
            """)
    int insertarSiNoExiste(@Param("consultorioId") Long consultorioId, @Param("tipo") String tipo,
            @Param("canal") String canal, @Param("destinatario") String destinatario,
            @Param("turnoId") Long turnoId, @Param("avisoId") Long avisoId, @Param("entradaId") Long entradaId,
            @Param("datos") String datos, @Param("enlaces") String enlaces, @Param("ahora") Instant ahora);

    /**
     * Descarta las notificaciones todavía {@code PENDIENTE} de un turno y tipo (C-08 design D4): quedan
     * {@code FALLIDA} con el motivo, sin borrar la fila (el registro de envíos no se pierde). Si el procesador tiene
     * la fila tomada, el {@code UPDATE} espera su commit y una ya {@code ENVIADA} no se toca.
     *
     * @return filas descartadas
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE notificacion SET estado = 'FALLIDA', error = :motivo, actualizado_en = :ahora
             WHERE consultorio_id = :consultorioId AND turno_id = :turnoId AND tipo = :tipo
               AND estado = 'PENDIENTE'
            """)
    int descartarPendientes(@Param("consultorioId") Long consultorioId, @Param("turnoId") Long turnoId,
            @Param("tipo") String tipo, @Param("motivo") String motivo, @Param("ahora") Instant ahora);
}
