package ar.edu.odontogestion.domain.consultorio;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ConsultorioRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ConsultorioRepository repository;

    @Autowired
    private EntityManager em;

    @Test
    void guardaYLeeConOpcionalesVaciosYZonaIndicada() {
        Consultorio guardado = repository.saveAndFlush(Consultorio.nuevo("Consultorio Prueba", "UTC"));
        em.clear();

        Consultorio leido = repository.findById(guardado.getId()).orElseThrow();

        assertThat(leido.getNombre()).isEqualTo("Consultorio Prueba");
        assertThat(leido.getDireccion()).isEmpty();
        assertThat(leido.getTelefono()).isEmpty();
        assertThat(leido.getEmailContacto()).isEmpty();
        assertThat(leido.getZonaHoraria()).isEqualTo("UTC");
        assertThat(leido.getCreadoEn()).isNotNull();
    }

    @Test
    void actualizarDatosCambiaLosCamposEditablesPeroNoLaZona() {
        Consultorio c = repository.saveAndFlush(Consultorio.nuevo("Antes", "America/Argentina/Buenos_Aires"));

        c.actualizarDatos("Despues", "Calle 1", "123", "c@ejemplo.test");
        repository.flush();
        em.clear();

        Consultorio leido = repository.findById(c.getId()).orElseThrow();
        assertThat(leido.getNombre()).isEqualTo("Despues");
        assertThat(leido.getDireccion()).isEqualTo("Calle 1");
        assertThat(leido.getTelefono()).isEqualTo("123");
        assertThat(leido.getEmailContacto()).isEqualTo("c@ejemplo.test");
        assertThat(leido.getZonaHoraria()).isEqualTo("America/Argentina/Buenos_Aires");
    }

    @Test
    void unConsultorioNuevoTieneLosParametrosPorDefecto() {
        Consultorio c = repository.saveAndFlush(Consultorio.nuevo("Defaults", "UTC"));
        em.clear();

        ParametrosConsultorio p = repository.findById(c.getId()).orElseThrow().getParametros();

        assertThat(p).isEqualTo(new ParametrosConsultorio(15, 120, 2, 24, 60, 30, 15));
    }

    @Test
    void actualizarParametrosSePersisteLosSieteValores() {
        Consultorio c = repository.saveAndFlush(Consultorio.nuevo("Params", "UTC"));

        c.actualizarParametros(new ParametrosConsultorio(30, 60, 4, 48, 90, 45, 10));
        repository.flush();
        em.clear();

        assertThat(repository.findById(c.getId()).orElseThrow().getParametros())
                .isEqualTo(new ParametrosConsultorio(30, 60, 4, 48, 90, 45, 10));
    }
}
