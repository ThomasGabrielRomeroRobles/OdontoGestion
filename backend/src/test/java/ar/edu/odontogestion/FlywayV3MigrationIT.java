package ar.edu.odontogestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class FlywayV3MigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    private void revertida(Runnable bloque) {
        tx.executeWithoutResult(status -> {
            bloque.run();
            status.setRollbackOnly();
        });
    }

    @Test
    void consultorioInsertadoSoloConNombreTieneLosParametrosPorDefecto() {
        revertida(() -> {
            Map<String, Object> fila = jdbc.queryForMap(
                    "INSERT INTO consultorio (nombre) VALUES ('Solo nombre') RETURNING granularidad_min, "
                            + "anticipacion_min_reserva_min, limite_cancelacion_horas, recordatorio_horas_antes, "
                            + "vencimiento_aviso_min, vencimiento_pendiente_min, tolerancia_ausente_min");
            assertThat(fila).containsEntry("granularidad_min", 15)
                    .containsEntry("anticipacion_min_reserva_min", 120)
                    .containsEntry("limite_cancelacion_horas", 2)
                    .containsEntry("recordatorio_horas_antes", 24)
                    .containsEntry("vencimiento_aviso_min", 60)
                    .containsEntry("vencimiento_pendiente_min", 30)
                    .containsEntry("tolerancia_ausente_min", 15);
        });
    }

    @Test
    void cadaParametroNoPositivoViolaSuCheck() {
        List<String> columnas = List.of("granularidad_min", "anticipacion_min_reserva_min",
                "limite_cancelacion_horas", "recordatorio_horas_antes", "vencimiento_aviso_min",
                "vencimiento_pendiente_min", "tolerancia_ausente_min");
        for (String columna : columnas) {
            revertida(() -> assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO consultorio (nombre, " + columna + ") VALUES ('C', 0)"))
                    .as(columna).isInstanceOf(DataIntegrityViolationException.class));
        }
    }

    @Test
    void unParametroPositivoDistintoDelDefaultEsAceptado() {
        revertida(() -> {
            Integer g = jdbc.queryForObject(
                    "INSERT INTO consultorio (nombre, granularidad_min) VALUES ('C', 30) RETURNING granularidad_min",
                    Integer.class);
            assertThat(g).isEqualTo(30);
        });
    }

    @Test
    void elHistorialRegistraLaVersionTres() {
        Boolean ok = jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '3'",
                Boolean.class);
        assertThat(ok).isTrue();
    }
}
