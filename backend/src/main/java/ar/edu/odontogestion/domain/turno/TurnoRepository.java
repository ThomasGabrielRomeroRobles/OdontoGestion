package ar.edu.odontogestion.domain.turno;

import ar.edu.odontogestion.domain.agenda.Hueco;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Toda consulta filtra por consultorio (regla dura #6). Las consultas de ocupación son orientativas (design D7):
 * espejan el predicado de {@code ex_turno_sin_superposicion} pero la garantía es la base.
 */
public interface TurnoRepository extends JpaRepository<Turno, Long> {

    Optional<Turno> findByIdAndConsultorio_Id(Long id, Long consultorioId);

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
}
