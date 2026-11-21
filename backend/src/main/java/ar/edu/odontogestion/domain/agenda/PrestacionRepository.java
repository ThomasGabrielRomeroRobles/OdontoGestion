package ar.edu.odontogestion.domain.agenda;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta filtra por consultorio (regla dura #6). */
public interface PrestacionRepository extends JpaRepository<Prestacion, Long> {

    List<Prestacion> findByConsultorio_IdOrderByNombreAsc(Long consultorioId);

    Optional<Prestacion> findByIdAndConsultorio_Id(Long id, Long consultorioId);

    boolean existsByConsultorio_IdAndNombreIgnoreCase(Long consultorioId, String nombre);

    boolean existsByConsultorio_IdAndNombreIgnoreCaseAndIdNot(Long consultorioId, String nombre, Long id);
}
