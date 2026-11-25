package ar.edu.odontogestion.domain.paciente;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda consulta filtra por consultorio: el id de un paciente de otro consultorio se trata como inexistente. */
public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    /** Tope de resultados de la búsqueda (design D7). */
    int MAXIMO_RESULTADOS = 20;

    /** {@code dni} ya normalizado ({@link Dni#normalizar}). */
    Optional<Paciente> findByConsultorioIdAndDni(Long consultorioId, String dni);

    Optional<Paciente> findByIdAndConsultorioId(Long id, Long consultorioId);

    /** {@code true} si el consultorio tiene al menos un paciente (lo usa el seed de demo para no repetirse). */
    boolean existsByConsultorioId(Long consultorioId);

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

    /**
     * Suma una ausencia (RN-AS-02, C-08 design D3). {@code UPDATE} relativo: dos marcas simultáneas de turnos
     * distintos del mismo paciente no se pisan (la entidad no tiene {@code @Version}). Limpia el contexto de
     * persistencia para que una lectura posterior en la misma transacción vea el valor nuevo.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Paciente p set p.cantidadAusencias = p.cantidadAusencias + 1 where p.id = :id")
    int incrementarAusencias(@Param("id") Long id);

    /**
     * Resta una ausencia al revertir una marca (RN-AS-06), sin bajar de cero.
     *
     * @return 1 si restó, 0 si el contador ya estaba en cero
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Paciente p set p.cantidadAusencias = p.cantidadAusencias - 1 "
            + "where p.id = :id and p.cantidadAusencias > 0")
    int decrementarAusencias(@Param("id") Long id);
}
