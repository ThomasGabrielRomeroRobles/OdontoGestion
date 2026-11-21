package ar.edu.odontogestion.infrastructure.consultorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.usuario.UsuarioRepository;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Cada caso corre en una transacción que se revierte: la base compartida conserva el seed. */
@Transactional
class ConsultorioActualUnicoIT extends AbstractIntegrationTest {

    @Autowired
    private ConsultorioRepository consultorios;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void vaciarConsultorios() {
        // Desde V4 (C-06) la agenda referencia al consultorio con ON DELETE RESTRICT; se revierte igual que el resto.
        em.createNativeQuery("DELETE FROM bloqueo_agenda").executeUpdate();
        em.createNativeQuery("DELETE FROM horario_trabajo").executeUpdate();
        em.createNativeQuery("DELETE FROM prestacion").executeUpdate();
        usuarios.deleteAll();
        consultorios.deleteAll();
        consultorios.flush();
    }

    @Test
    void conUnConsultorioDevuelveSuIdYLoCacheaSinNuevasConsultas() {
        Long esperado = consultorios.saveAndFlush(Consultorio.nuevo("Unico", "UTC")).getId();
        var estadisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        ConsultorioActualUnico actual = new ConsultorioActualUnico(consultorios);

        long antes = estadisticas.getQueryExecutionCount();
        Long primero = actual.id();
        Long segundo = actual.id();

        assertThat(primero).isEqualTo(esperado);
        assertThat(segundo).isEqualTo(esperado);
        assertThat(estadisticas.getQueryExecutionCount() - antes).isEqualTo(1);
    }

    @Test
    void sinConsultoriosLanzaIllegalStateException() {
        ConsultorioActualUnico actual = new ConsultorioActualUnico(consultorios);

        assertThatThrownBy(actual::id).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No hay consultorio configurado");
    }

    @Test
    void conDosConsultoriosLanzaIllegalStateException() {
        consultorios.saveAndFlush(Consultorio.nuevo("Uno", "UTC"));
        consultorios.saveAndFlush(Consultorio.nuevo("Dos", "UTC"));
        ConsultorioActualUnico actual = new ConsultorioActualUnico(consultorios);

        assertThatThrownBy(actual::id).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("único consultorio");
    }

    @Test
    void unaFallaNoQuedaCacheada() {
        ConsultorioActualUnico actual = new ConsultorioActualUnico(consultorios);
        assertThatThrownBy(actual::id).isInstanceOf(IllegalStateException.class);

        Long id = consultorios.saveAndFlush(Consultorio.nuevo("Aparece despues", "UTC")).getId();

        assertThat(actual.id()).isEqualTo(id);
    }
}
