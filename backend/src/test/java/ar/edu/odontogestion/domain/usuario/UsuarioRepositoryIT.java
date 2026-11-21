package ar.edu.odontogestion.domain.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UsuarioRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private ConsultorioRepository consultorios;

    @Autowired
    private EntityManager em;

    private Consultorio consultorio() {
        return consultorios.saveAndFlush(Consultorio.nuevo("C Usuarios", "UTC"));
    }

    @Test
    void findByEmailNoDistingueMayusculas() {
        usuarios.saveAndFlush(Usuario.nuevo(consultorio(), "Dra.Prueba@Ejemplo.test", "hash", "Ana", "Perez", ""));
        em.clear();

        assertThat(usuarios.findByEmail("dra.prueba@ejemplo.TEST")).isPresent();
        assertThat(usuarios.findByEmail("otro@ejemplo.test")).isEmpty();
    }

    @Test
    void elEmailGuardadoConservaLaCapitalizacionOriginal() {
        usuarios.saveAndFlush(Usuario.nuevo(consultorio(), "Dra.Prueba@Ejemplo.test", "hash", "Ana", "Perez", ""));
        em.clear();

        assertThat(usuarios.findByEmail("dra.prueba@ejemplo.test").orElseThrow().getEmail())
                .isEqualTo("Dra.Prueba@Ejemplo.test");
    }

    @Test
    void elConsultorioSeCargaPerezosamenteYEsObligatorio() {
        Usuario guardado = usuarios.saveAndFlush(
                Usuario.nuevo(consultorio(), "lazy@ejemplo.test", "hash", "Ana", "Perez", "MN 1"));
        em.clear();

        Usuario leido = usuarios.findById(guardado.getId()).orElseThrow();
        PersistenceUnitUtil util = em.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(util.isLoaded(leido, "consultorio")).isFalse();
        assertThatThrownBy(() -> usuarios.saveAndFlush(
                Usuario.nuevo(null, "sin@ejemplo.test", "hash", "Ana", "Perez", "")))
                .isInstanceOf(Exception.class);
    }

    @Test
    void nuevoUsuarioEstaActivoYSinAccesoPrevio() {
        Usuario u = usuarios.saveAndFlush(Usuario.nuevo(consultorio(), "a@ejemplo.test", "h", "Ana", "Perez", ""));
        em.clear();

        Usuario leido = usuarios.findById(u.getId()).orElseThrow();
        assertThat(leido.isActivo()).isTrue();
        assertThat(leido.getUltimoAcceso()).isNull();
    }
}
