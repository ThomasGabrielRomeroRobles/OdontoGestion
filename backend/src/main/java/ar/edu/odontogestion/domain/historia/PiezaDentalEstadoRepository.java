package ar.edu.odontogestion.domain.historia;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PiezaDentalEstadoRepository extends JpaRepository<PiezaDentalEstado, Long> {

    List<PiezaDentalEstado> findByHistoria_Id(Long historiaId);

    Optional<PiezaDentalEstado> findByHistoria_IdAndPiezaFdi(Long historiaId, short piezaFdi);
}
