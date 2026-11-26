package ar.edu.odontogestion.domain.historia;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvolucionClinicaRepository extends JpaRepository<EvolucionClinica, Long> {

    /** Orden cronológico: fecha de la consulta, luego alta y finalmente id (desempate estable). */
    List<EvolucionClinica> findByHistoria_IdOrderByFechaAscCreadoEnAscIdAsc(Long historiaId);

    /** Busca dentro de la historia: una evolución de otro paciente se trata como inexistente (404). */
    Optional<EvolucionClinica> findByIdAndHistoria_Id(Long id, Long historiaId);
}
