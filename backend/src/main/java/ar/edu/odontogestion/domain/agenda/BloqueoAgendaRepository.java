package ar.edu.odontogestion.domain.agenda;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Toda consulta filtra por consultorio (regla dura #6). Las de rango devuelven los bloqueos que se intersecan con
 * {@code [inicio, fin)}: dos métodos en vez de un parámetro nulo, que PostgreSQL no sabe tipar.
 */
public interface BloqueoAgendaRepository extends JpaRepository<BloqueoAgenda, Long> {

    /** Sin límite superior: {@code hasta > inicio}. */
    List<BloqueoAgenda> findByConsultorio_IdAndHastaGreaterThanOrderByDesdeAsc(Long consultorioId, Instant inicio);

    /** Intersección con {@code [inicio, fin)}: {@code hasta > inicio AND desde < fin}. */
    List<BloqueoAgenda> findByConsultorio_IdAndHastaGreaterThanAndDesdeLessThanOrderByDesdeAsc(Long consultorioId,
            Instant inicio, Instant fin);

    Optional<BloqueoAgenda> findByIdAndConsultorio_Id(Long id, Long consultorioId);
}
