package ar.edu.odontogestion.domain.listaespera;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda consulta filtra por consultorio (regla dura #6). */
public interface AvisoHuecoRepository extends JpaRepository<AvisoHueco, Long> {

    Optional<AvisoHueco> findByIdAndConsultorio_Id(Long id, Long consultorioId);

    /** Solo la clave del hueco, sin cargar la entidad: así el {@code FOR UPDATE} posterior relee el estado vigente. */
    @Query("""
            select new ar.edu.odontogestion.domain.listaespera.HuecoDeAviso(a.turnoOrigenId, a.inicio)
            from AvisoHueco a where a.id = :id and a.consultorio.id = :consultorioId
            """)
    Optional<HuecoDeAviso> huecoDe(@Param("id") Long id, @Param("consultorioId") Long consultorioId);

    List<AvisoHueco> findByTurnoOrigenIdAndInicioOrderById(Long turnoOrigenId, Instant inicio);

    /**
     * {@code SELECT … FOR UPDATE} de <b>todos</b> los avisos del hueco, por id (C-10 D5): serializa en la base las
     * aceptaciones del mismo hueco. El orden fijo evita interbloqueos entre aceptaciones concurrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from AvisoHueco a
            where a.consultorio.id = :consultorioId and a.turnoOrigenId = :turnoOrigenId and a.inicio = :inicio
            order by a.id
            """)
    List<AvisoHueco> bloquearDelHueco(@Param("consultorioId") Long consultorioId,
            @Param("turnoOrigenId") Long turnoOrigenId, @Param("inicio") Instant inicio);

    /**
     * Crea el aviso si no existe ya uno para el mismo hueco y entrada (idempotencia del listener). Devuelve el id,
     * o vacío si ya existía.
     */
    @Query(nativeQuery = true, value = """
            INSERT INTO aviso_hueco (consultorio_id, entrada_lista_espera_id, turno_origen_id, prestacion_id,
                                     inicio, fin, estado, vence_en, creado_en, actualizado_en)
            VALUES (:consultorioId, :entradaId, :turnoOrigenId, :prestacionId, :inicio, :fin, 'ENVIADO', :venceEn,
                    :ahora, :ahora)
            ON CONFLICT ON CONSTRAINT uq_aviso_hueco_entrada DO NOTHING
            RETURNING id
            """)
    List<Long> insertarSiNoExiste(@Param("consultorioId") Long consultorioId, @Param("entradaId") Long entradaId,
            @Param("turnoOrigenId") Long turnoOrigenId, @Param("prestacionId") Long prestacionId,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin, @Param("venceEn") Instant venceEn,
            @Param("ahora") Instant ahora);

    /** Cierra los avisos {@code ENVIADO} del hueco salvo {@code exceptoId}. Las filas ya están bloqueadas. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE aviso_hueco
               SET estado = 'CERRADO', version = version + 1, actualizado_en = :ahora
             WHERE consultorio_id = :consultorioId AND turno_origen_id = :turnoOrigenId AND inicio = :inicio
               AND estado = 'ENVIADO' AND id <> :exceptoId
            """)
    int cerrarEnviadosDelHueco(@Param("consultorioId") Long consultorioId, @Param("turnoOrigenId") Long turnoOrigenId,
            @Param("inicio") Instant inicio, @Param("exceptoId") Long exceptoId, @Param("ahora") Instant ahora);

    /** Pasa a {@code EXPIRADO} los avisos {@code ENVIADO} vencidos (barrido, C-10 D6). Idempotente. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE aviso_hueco
               SET estado = 'EXPIRADO', version = version + 1, actualizado_en = :ahora
             WHERE consultorio_id = :consultorioId AND estado = 'ENVIADO' AND vence_en <= :ahora
            """)
    int expirarVencidos(@Param("consultorioId") Long consultorioId, @Param("ahora") Instant ahora);
}
