package ar.edu.odontogestion.domain.paciente;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda consulta filtra por consultorio: el id de un paciente de otro consultorio se trata como inexistente. */
public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    /** Tope de resultados de la búsqueda (design D7). */
    int MAXIMO_RESULTADOS = 20;

    /** {@code dni} ya normalizado ({@link Dni#normalizar}). */
    Optional<Paciente> findByConsultorioIdAndDni(Long consultorioId, String dni);

    Optional<Paciente> findByIdAndConsultorioId(Long id, Long consultorioId);

    List<Paciente> findByConsultorioIdOrderByApellidoAscNombreAscIdAsc(Long consultorioId, Limit limit);

    /**
     * Inserta el paciente salvo que el DNI ya exista en el consultorio (design D5). Devuelve el id insertado, o una
     * lista vacía si otra transacción ya lo registró. No lanza excepción ante el duplicado, así que no deja la
     * transacción de quien llama marcada para rollback. Los valores llegan normalizados; los timestamps salen del
     * {@code Clock} de la aplicación porque la auditoría de JPA no corre en una query nativa.
     */
    @Query(nativeQuery = true, value = """
            INSERT INTO paciente (consultorio_id, nombre, apellido, dni, fecha_nacimiento, telefono, email,
                                  direccion, creado_en, actualizado_en)
            VALUES (:consultorioId, :nombre, :apellido, :dni, :fechaNacimiento, :telefono, :email,
                    :direccion, :ahora, :ahora)
            ON CONFLICT (consultorio_id, dni) DO NOTHING
            RETURNING id
            """)
    List<Long> insertarSiNoExiste(@Param("consultorioId") Long consultorioId, @Param("nombre") String nombre,
            @Param("apellido") String apellido, @Param("dni") String dni,
            @Param("fechaNacimiento") LocalDate fechaNacimiento, @Param("telefono") String telefono,
            @Param("email") String email, @Param("direccion") String direccion, @Param("ahora") Instant ahora);

    /** Parámetros armados por {@link BusquedaPaciente}. */
    @Query("""
            select p from Paciente p
            where p.consultorio.id = :consultorioId
              and (lower(p.apellido) like :patron escape '\\'
                   or lower(p.nombre) like :patron escape '\\'
                   or lower(p.email) like :patron escape '\\'
                   or p.dni like :prefijoDni)
            order by p.apellido, p.nombre, p.id
            """)
    List<Paciente> buscarPorTexto(@Param("consultorioId") Long consultorioId, @Param("patron") String patron,
            @Param("prefijoDni") String prefijoDni, Limit limit);
}
