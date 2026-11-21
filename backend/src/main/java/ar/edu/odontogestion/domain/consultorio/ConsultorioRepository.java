package ar.edu.odontogestion.domain.consultorio;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConsultorioRepository extends JpaRepository<Consultorio, Long> {

    java.util.Optional<Consultorio> findFirstByOrderByIdAsc();

    /** Ids ordenados; con {@code Pageable.ofSize(2)} alcanza para detectar 0, 1 o más de un consultorio. */
    @Query("select c.id from Consultorio c order by c.id")
    List<Long> idsOrdenados(Pageable pageable);
}
