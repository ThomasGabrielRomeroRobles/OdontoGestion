package ar.edu.odontogestion.domain.historia;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoriaClinicaRepository extends JpaRepository<HistoriaClinica, Long> {

    Optional<HistoriaClinica> findByPaciente_Id(Long pacienteId);

    /**
     * Crea la historia del paciente salvo que ya exista (C-05 design D2). No lanza excepción ante el duplicado, así
     * que dos primeras escrituras concurrentes convergen en la misma fila sin marcar la transacción para rollback.
     * Los timestamps salen del {@code Clock} de la aplicación porque la auditoría de JPA no corre en una query nativa.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO historia_clinica (consultorio_id, paciente_id, creado_en, actualizado_en)
            VALUES (:consultorioId, :pacienteId, :ahora, :ahora)
            ON CONFLICT (paciente_id) DO NOTHING
            """)
    int insertarSiNoExiste(@Param("consultorioId") Long consultorioId, @Param("pacienteId") Long pacienteId,
            @Param("ahora") Instant ahora);
}
