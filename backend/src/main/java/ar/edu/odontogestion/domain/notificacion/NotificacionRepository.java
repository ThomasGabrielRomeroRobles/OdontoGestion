package ar.edu.odontogestion.domain.notificacion;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda consulta filtra por consultorio (regla dura #6). */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    /**
     * Inserción idempotente (design D7): {@code ON CONFLICT DO NOTHING} sobre los índices únicos parciales.
     * En PostgreSQL una violación de unicidad abortaría la transacción de origen; así, el duplicado se ignora
     * y la transacción sigue sana (RN-NO-06).
     *
     * @param datos JSON con las variables de plantilla
     * @param enlaces JSON con la lista de {@link EnlaceProgramado}
     * @return 1 si se insertó, 0 si ya existía
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notificacion (consultorio_id, tipo, canal, destinatario, turno_id, aviso_id,
                                      entrada_lista_espera_id, datos, enlaces, programada_para,
                                      creado_en, actualizado_en)
            VALUES (:consultorioId, :tipo, :canal, :destinatario, :turnoId, :avisoId, :entradaId,
                    CAST(:datos AS jsonb), CAST(:enlaces AS jsonb), :ahora, :ahora, :ahora)
            ON CONFLICT DO NOTHING
            """)
    int insertarSiNoExiste(@Param("consultorioId") Long consultorioId, @Param("tipo") String tipo,
            @Param("canal") String canal, @Param("destinatario") String destinatario,
            @Param("turnoId") Long turnoId, @Param("avisoId") Long avisoId, @Param("entradaId") Long entradaId,
            @Param("datos") String datos, @Param("enlaces") String enlaces, @Param("ahora") Instant ahora);
}
