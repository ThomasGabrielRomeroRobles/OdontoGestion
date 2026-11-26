package ar.edu.odontogestion.domain.historia;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnamnesisRepository extends JpaRepository<Anamnesis, Long> {

    Optional<Anamnesis> findByHistoria_Id(Long historiaId);
}
