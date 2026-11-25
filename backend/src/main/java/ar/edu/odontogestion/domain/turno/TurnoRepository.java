package ar.edu.odontogestion.domain.turno;

import ar.edu.odontogestion.domain.agenda.Hueco;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Toda consulta filtra por consultorio (regla dura #6). Las consultas de ocupación son orientativas (design D7):
 * espejan el predicado de {@code ex_turno_sin_superposicion} pero la garantía es la base.
 */
public interface TurnoRepository extends JpaRepository<Turno, Long> {

    Optional<Turno> findByIdAndConsultorio_Id(Long id, Long consultorioId);

    /**
     * Candidatos a recordatorio (C-11 D1/D2/D7): {@code RESERVADO} que empiezan en {@code (ahora, hasta]}, creados
     * con al menos {@code horas} de anticipación (RN-NO-09) y sin {@code RECORDATORIO} previo. El {@code NOT EXISTS}
     * es solo una optimización: la idempotencia la garantiza el índice único parcial de V6.
     */
    @Query(value = """
            SELECT t.id FROM turno t
            WHERE t.consultorio_id = :consultorioId AND t.estado = 'RESERVADO'
              AND t.inicio > :ahora AND t.inicio <= :hasta
              AND t.creado_en <= t.inicio - make_interval(hours => :horas)
              AND NOT EXISTS (SELECT 1 FROM notificacion n WHERE n.turno_id = t.id AND n.tipo = 'RECORDATORIO')
            ORDER BY t.inicio
            """, nativeQuery = true)
    List<Long> idsParaRecordatorio(@Param("consultorioId") Long consultorioId, @Param("ahora") Instant ahora,
            @Param("hasta") Instant hasta, @Param("horas") int horas);

    /** Turnos {@code AUSENTE} de un paciente, del más reciente al más antiguo, con la prestación cargada (C-08 D6). */
    @Query("""
            select t from Turno t
            join fetch t.prestacion
            where t.consultorio.id = :consultorioId and t.paciente.id = :pacienteId
              and t.estado = ar.edu.odontogestion.domain.turno.EstadoTurno.AUSENTE
            order by t.inicio desc, t.id desc
            """)
    List<Turno> ausenciasDelPaciente(@Param("consultorioId") Long consultorioId,
            @Param("pacienteId") Long pacienteId);

    /** Turnos que empiezan en {@code [desde, hasta)}, en todos los estados, con paciente y prestación cargados. */
    @Query("""
            select t from Turno t
            join fetch t.paciente
            join fetch t.prestacion
            where t.consultorio.id = :consultorioId and t.inicio >= :desde and t.inicio < :hasta
            order by t.inicio, t.id
            """)
    List<Turno> listarPorInicioEntre(@Param("consultorioId") Long consultorioId, @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Turnos que ocupan agenda (todos salvo {@code CANCELADO}/{@code EXPIRADO}, D2) y se solapan con
     * {@code [desde, hasta)}. Una pendiente cuyo {@code pendiente_hasta <= ahora} cuenta como libre aunque todavía
     * no se haya marcado {@code EXPIRADO} (D7).
     */
    @Query("""
            select t from Turno t
            join fetch t.paciente
            join fetch t.prestacion
            where t.consultorio.id = :consultorioId
              and t.inicio < :hasta and t.fin > :desde
              and t.estado not in (ar.edu.odontogestion.domain.turno.EstadoTurno.CANCELADO,
                                   ar.edu.odontogestion.domain.turno.EstadoTurno.EXPIRADO)
              and not (t.estado = ar.edu.odontogestion.domain.turno.EstadoTurno.PENDIENTE_CONFIRMACION
                       and t.pendienteHasta <= :ahora)
            order by t.inicio, t.id
            """)
    List<Turno> ocupantesQueSolapan(@Param("consultorioId") Long consultorioId, @Param("desde") Instant desde,
            @Param("hasta") Instant hasta, @Param("ahora") Instant ahora);

    /** Solo los rangos de {@link #ocupantesQueSolapan}, para la calculadora de disponibilidad. */
    @Query("""
            select new ar.edu.odontogestion.domain.agenda.Hueco(t.inicio, t.fin) from Turno t
            where t.consultorio.id = :consultorioId
              and t.inicio < :hasta and t.fin > :desde
              and t.estado not in (ar.edu.odontogestion.domain.turno.EstadoTurno.CANCELADO,
                                   ar.edu.odontogestion.domain.turno.EstadoTurno.EXPIRADO)
              and not (t.estado = ar.edu.odontogestion.domain.turno.EstadoTurno.PENDIENTE_CONFIRMACION
                       and t.pendienteHasta <= :ahora)
            order by t.inicio
            """)
    List<Hueco> intervalosOcupados(@Param("consultorioId") Long consultorioId, @Param("desde") Instant desde,
            @Param("hasta") Instant hasta, @Param("ahora") Instant ahora);

    /**
     * Solicitudes {@code PENDIENTE_CONFIRMACION} aún vigentes ({@code pendiente_hasta > ahora}) de pacientes del
     * consultorio con ese DNI <b>o</b> ese email (C-09 D4: máximo de pendientes). {@code dni} y {@code email} llegan
     * normalizados; las vencidas y las ya confirmadas no cuentan.
     */
    @Query("""
            select count(t) from Turno t
            where t.consultorio.id = :consultorioId
              and t.estado = ar.edu.odontogestion.domain.turno.EstadoTurno.PENDIENTE_CONFIRMACION
              and t.pendienteHasta > :ahora
              and (t.paciente.dni = :dni or lower(t.paciente.email) = :email)
            """)
    long contarPendientesVigentesPorDniOEmail(@Param("consultorioId") Long consultorioId, @Param("dni") String dni,
            @Param("email") String email, @Param("ahora") Instant ahora);

    /**
     * Expira en masa las pendientes vencidas del consultorio (design D8). Idempotente. Incrementa {@code version}
     * para que una transacción concurrente que ya había cargado el turno falle con 409 al guardar. Exige una
     * transacción abierta (la abre el service) y no publica eventos: la expiración no libera un hueco ofrecido.
     *
     * @return cantidad de turnos expirados
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE turno
               SET estado = 'EXPIRADO', version = version + 1, actualizado_en = :ahora
             WHERE consultorio_id = :consultorioId
               AND estado = 'PENDIENTE_CONFIRMACION'
               AND pendiente_hasta <= :ahora
            """)
    int expirarPendientesVencidas(@Param("consultorioId") Long consultorioId, @Param("ahora") Instant ahora);
}
