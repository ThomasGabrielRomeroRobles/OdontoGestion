package ar.edu.odontogestion.application.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractTurnoIT;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Prestaciones activas para el público (C-09 2.4). Las de prueba llevan el prefijo {@code IT-Turno-}. */
class PrestacionesPublicasIT extends AbstractTurnoIT {

    @Autowired
    private PrestacionService prestaciones;

    @Test
    void devuelveSoloLasActivasOrdenadasPorNombre() {
        Long activa = crearPrestacion(30);
        Long otraActiva = crearPrestacion(45);
        Long inactiva = crearPrestacionInactiva(30);

        List<PrestacionVista> activas = prestaciones.listarActivas();

        assertThat(activas).extracting(PrestacionVista::id).contains(activa, otraActiva).doesNotContain(inactiva);
        assertThat(activas).allMatch(PrestacionVista::activa);
        assertThat(activas).extracting(PrestacionVista::id).containsExactlyElementsOf(
                jdbc.queryForList("SELECT id FROM prestacion WHERE activa ORDER BY nombre", Long.class));
    }

    @Test
    void sinPrestacionesActivasLaListaEstaVacia() {
        List<Long> activasDelSeed = jdbc.queryForList("SELECT id FROM prestacion WHERE activa", Long.class);
        try {
            jdbc.update("UPDATE prestacion SET activa = false");

            assertThat(prestaciones.listarActivas()).isEmpty();
        } finally {
            activasDelSeed.forEach(id -> jdbc.update("UPDATE prestacion SET activa = true WHERE id = ?", id));
        }
    }
}
