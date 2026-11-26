package ar.edu.odontogestion.application.historia;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.historia.DatosAnamnesis;
import ar.edu.odontogestion.support.AbstractHistoriaIT;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Primeras escrituras concurrentes del mismo paciente (C-05 design D2). Sin transacción de test: cada hilo confirma
 * la suya. El INSERT ... ON CONFLICT DO NOTHING hace que ambas usen la misma historia sin errores.
 */
class HistoriaClinicaConcurrenciaIT extends AbstractHistoriaIT {

    @Autowired
    private HistoriaClinicaService servicio;

    private final ExecutorService hilos = Executors.newFixedThreadPool(2);

    @AfterEach
    void detenerHilos() {
        hilos.shutdownNow();
    }

    @RepeatedTest(3)
    void anamnesisYEvolucionEnParaleloCreanUnaSolaHistoria() throws Exception {
        Long paciente = crearPaciente();
        CountDownLatch largada = new CountDownLatch(1);
        Future<AnamnesisVista> a = hilos.submit(() -> {
            largada.await();
            return servicio.guardarAnamnesis(paciente,
                    new DatosAnamnesis("Penicilina", null, null, null, null, null), EMAIL_SEED);
        });
        Future<EvolucionVista> e = hilos.submit(() -> {
            largada.await();
            return servicio.registrarEvolucion(paciente, new DatosEvolucion("Nota ficticia", null, null), EMAIL_SEED);
        });
        largada.countDown();

        assertThat(a.get(20, TimeUnit.SECONDS).alergias()).isEqualTo("Penicilina");
        assertThat(e.get(20, TimeUnit.SECONDS).id()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historia_clinica WHERE paciente_id = ?",
                Integer.class, paciente)).isEqualTo(1);
        HistoriaVista vista = servicio.consultar(paciente, EMAIL_SEED);
        assertThat(vista.anamnesis().alergias()).isEqualTo("Penicilina");
        assertThat(vista.evoluciones()).hasSize(1);
    }
}
