package ar.edu.odontogestion.domain.usuario;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * La columna es {@code citext}, pero Hibernate enlaza el parámetro como {@code varchar} y PostgreSQL
     * resolvería la comparación como {@code text} (sensible a mayúsculas). El {@code cast} explícito
     * mantiene la comparación en {@code citext}.
     */
    @Query(value = "SELECT * FROM usuario WHERE email = CAST(:email AS citext)", nativeQuery = true)
    Optional<Usuario> findByEmail(@Param("email") String email);
}
