package ar.edu.odontogestion.domain.listaespera;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda consulta filtra por consultorio (regla dura #6). */
public interface EntradaListaEsperaRepository extends JpaRepository<EntradaListaEspera, Long> {

    Optional<EntradaListaEspera> findByIdAndConsultorio_Id(Long id, Long consultorioId);

    /**
     * Entradas {@code ACTIVA} de la prestación cuyo rango incluye {@code fechaLocal} (bordes incluidos) y cuyo
     * paciente sigue activo (C-10 D4). El orden es por alta, solo para que el resultado sea determinista.
     */
    @Query("""
            select e from EntradaListaEspera e
            join fetch e.paciente p
            where e.consultorio.id = :consultorioId
              and e.prestacion.id = :prestacionId
              and e.estado = ar.edu.odontogestion.domain.listaespera.EstadoEntrada.ACTIVA
              and e.fechaDesde <= :fechaLocal and e.fechaHasta >= :fechaLocal
              and p.activo = true
            order by e.creadoEn, e.id
            """)
    List<EntradaListaEspera> buscarCompatibles(@Param("consultorioId") Long consultorioId,
            @Param("prestacionId") Long prestacionId, @Param("fechaLocal") LocalDate fechaLocal);

    /** Listado del odontólogo: la más reciente primero, con paciente y prestación cargados (sin N+1). */
    @Query("""
            select e from EntradaListaEspera e
            join fetch e.paciente
            join fetch e.prestacion
            where e.consultorio.id = :consultorioId
            order by e.creadoEn desc, e.id desc
            """)
    List<EntradaListaEspera> listar(@Param("consultorioId") Long consultorioId);

    /**
     * Vence en masa las pendientes del paciente y la prestación cuyo plazo ya pasó, para liberar el único parcial
     * antes de reinscribir. Incrementa {@code version}. Exige una transacción abierta.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE entrada_lista_espera
               SET estado = 'VENCIDA', version = version + 1, actualizado_en = :ahora
             WHERE consultorio_id = :consultorioId AND paciente_id = :pacienteId AND prestacion_id = :prestacionId
               AND estado = 'PENDIENTE_CONFIRMACION' AND pendiente_hasta <= :ahora
            """)
    int vencerPendientesVencidas(@Param("consultorioId") Long consultorioId, @Param("pacienteId") Long pacienteId,
            @Param("prestacionId") Long prestacionId, @Param("ahora") Instant ahora);
}
