package ar.edu.odontogestion.domain.agenda;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta filtra por consultorio (regla dura #6). */
public interface HorarioTrabajoRepository extends JpaRepository<HorarioTrabajo, Long> {

    List<HorarioTrabajo> findByConsultorio_IdOrderByDiaSemanaAscHoraDesdeAsc(Long consultorioId);

    List<HorarioTrabajo> findByConsultorio_IdAndDiaSemana(Long consultorioId, short diaSemana);

    Optional<HorarioTrabajo> findByIdAndConsultorio_Id(Long id, Long consultorioId);
}
