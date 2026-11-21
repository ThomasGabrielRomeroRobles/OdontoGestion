package ar.edu.odontogestion.application.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrencia de buscarOCrearPorDni (design D5). Sin transacción de test: cada hilo confirma la suya, así que
 * los pacientes se borran en {@code @AfterEach}.
 */
@Import(FixedClockTestConfig.class)
class PacienteConcurrenciaIT extends AbstractIntegrationTest {

    private static final String DNI_PARALELO = "30777666";
    private static final String DNI_CARRERA = "30777667";

    @Autowired
    private PacienteService servicio;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private final ExecutorService hilos = Executors.newFixedThreadPool(2);

    @AfterEach
    void limpiar() {
        hilos.shutdownNow();
        jdbc.update("DELETE FROM paciente WHERE dni IN (?, ?)", DNI_PARALELO, DNI_CARRERA);
    }

    private static DatosPaciente datos(String dni, String email) {
        return new DatosPaciente("Ana", "Concurrente", dni, null, "+54 11 5555-0009", email, null);
    }

    private int cantidad(String dni) {
        return jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = ?", Integer.class, dni);
    }

    @Test
    void dosPedidosEnParaleloConElMismoDniTerminanEnUnSoloPaciente() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        Future<ResultadoBuscarOCrear> a = hilos.submit(() -> {
            largada.await();
            return servicio.buscarOCrearPorDni(datos(DNI_PARALELO, "a@ejemplo.test"));
        });
        Future<ResultadoBuscarOCrear> b = hilos.submit(() -> {
            largada.await();
            return servicio.buscarOCrearPorDni(datos(DNI_PARALELO, "b@ejemplo.test"));
        });
        largada.countDown();

        ResultadoBuscarOCrear ra = a.get(20, TimeUnit.SECONDS);
        ResultadoBuscarOCrear rb = b.get(20, TimeUnit.SECONDS);

        assertThat(ra.paciente().id()).isEqualTo(rb.paciente().id());
        assertThat(List.of(ra.creado(), rb.creado())).containsExactlyInAnyOrder(true, false);
        assertThat(cantidad(DNI_PARALELO)).isEqualTo(1);
    }

    @Test
    void quienPierdeLaCarreraDentroDeUnaTransaccionMayorReutilizaElPacienteYPuedeConfirmar() throws Exception {
        CountDownLatch aInserto = new CountDownLatch(1);
        CountDownLatch aPuedeConfirmar = new CountDownLatch(1);

        // A: inserta y retiene la transacción sin confirmar.
        Future<ResultadoBuscarOCrear> a = hilos.submit(() -> tx.execute(s -> {
            ResultadoBuscarOCrear r = servicio.buscarOCrearPorDni(datos(DNI_CARRERA, "a@ejemplo.test"));
            aInserto.countDown();
            try {
                aPuedeConfirmar.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return r;
        }));
        assertThat(aInserto.await(20, TimeUnit.SECONDS)).isTrue();

        // B: caso de uso con transacción propia; su INSERT queda bloqueado por la fila pendiente de A.
        Future<ResultadoBuscarOCrear> b = hilos.submit(() -> tx.execute(s -> {
            ResultadoBuscarOCrear r = servicio.buscarOCrearPorDni(datos(DNI_CARRERA, "b@ejemplo.test"));
            // escritura propia del caso de uso después de la operación: debe poder confirmarse
            jdbc.update("UPDATE paciente SET actualizado_en = actualizado_en WHERE id = ?", r.paciente().id());
            return r;
        }));

        Thread.sleep(500); // B ya está esperando en el INSERT … ON CONFLICT
        assertThat(b.isDone()).as("B debe esperar a que A confirme").isFalse();
        aPuedeConfirmar.countDown();

        ResultadoBuscarOCrear ra = a.get(20, TimeUnit.SECONDS);
        ResultadoBuscarOCrear rb = b.get(20, TimeUnit.SECONDS); // sin UnexpectedRollbackException

        assertThat(ra.creado()).isTrue();
        assertThat(rb.creado()).isFalse();
        assertThat(rb.paciente().id()).isEqualTo(ra.paciente().id());
        assertThat(rb.paciente().email()).isEqualTo("a@ejemplo.test");
        assertThat(cantidad(DNI_CARRERA)).isEqualTo(1);
    }
}
