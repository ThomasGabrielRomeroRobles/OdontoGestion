package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import ar.edu.odontogestion.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

@Import(FixedClockTestConfig.class)
class AuditingIT extends AbstractIntegrationTest {

    @Autowired
    private ConsultorioRepository repository;

    @Autowired
    private MutableClock reloj;

    @Autowired
    private TransactionTemplate tx;

    @AfterEach
    void restaurarReloj() {
        reloj.set(FixedClockTestConfig.AHORA);
    }

    @Test
    void alPersistirCreadoEnYActualizadoEnSonElInstanteDelReloj() {
        Long id = tx.execute(s -> repository.save(Consultorio.nuevo("Auditoria A", "Zona/Prueba")).getId());
        try {
            tx.executeWithoutResult(s -> {
                Consultorio c = repository.findById(id).orElseThrow();
                assertThat(c.getCreadoEn()).isEqualTo(Instant.parse("2026-11-20T12:00:00Z"));
                assertThat(c.getActualizadoEn()).isEqualTo(Instant.parse("2026-11-20T12:00:00Z"));
            });
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    void alModificarSoloCambiaActualizadoEn() {
        Long id = tx.execute(s -> repository.save(Consultorio.nuevo("Auditoria B", "Zona/Prueba")).getId());
        try {
            reloj.advance(Duration.ofMinutes(5));
            tx.executeWithoutResult(s -> repository.findById(id).orElseThrow()
                    .actualizarDatos("Auditoria B2", "", "", ""));

            tx.executeWithoutResult(s -> {
                Consultorio c = repository.findById(id).orElseThrow();
                assertThat(c.getCreadoEn()).isEqualTo(Instant.parse("2026-11-20T12:00:00Z"));
                assertThat(c.getActualizadoEn()).isEqualTo(Instant.parse("2026-11-20T12:05:00Z"));
            });
        } finally {
            repository.deleteById(id);
        }
    }
}
