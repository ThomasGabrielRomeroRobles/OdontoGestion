package ar.edu.odontogestion.domain.historia;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.support.AbstractHistoriaIT;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Mapeo de las entidades clínicas contra V12, INSERT idempotente de la historia y orden de evoluciones. */
class HistoriaClinicaRepositoryIT extends AbstractHistoriaIT {

    @Autowired
    private HistoriaClinicaRepository historias;

    @Autowired
    private EvolucionClinicaRepository evoluciones;

    @Autowired
    private PiezaDentalEstadoRepository piezas;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void insertarSiNoExisteEsIdempotente() {
        Long paciente = crearPaciente();
        int primero = tx.execute(s -> historias.insertarSiNoExiste(consultorioId(), paciente, reloj.instant()));
        int segundo = tx.execute(s -> historias.insertarSiNoExiste(consultorioId(), paciente, reloj.instant()));

        assertThat(primero).isEqualTo(1);
        assertThat(segundo).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historia_clinica WHERE paciente_id = ?",
                Integer.class, paciente)).isEqualTo(1);
        Optional<HistoriaClinica> creada = tx.execute(s -> historias.findByPaciente_Id(paciente));
        assertThat(creada).isPresent();
    }

    @Test
    void evolucionesEnOrdenCronologico() {
        Long historia = insertarHistoria(crearPaciente());
        Long usuario = usuarioSeedId();
        String sql = "INSERT INTO evolucion_clinica (historia_id, usuario_id, fecha, nota, creado_en) "
                + "VALUES (?, ?, ?::date, ?, ?::timestamptz) RETURNING id";
        Long nov20a = jdbc.queryForObject(sql, Long.class, historia, usuario, "2026-11-20", "A", "2026-11-20T10:00:00Z");
        Long oct05 = jdbc.queryForObject(sql, Long.class, historia, usuario, "2026-10-05", "B", "2026-11-20T11:00:00Z");
        Long nov20b = jdbc.queryForObject(sql, Long.class, historia, usuario, "2026-11-20", "C", "2026-11-20T12:00:00Z");

        List<Long> ids = tx.execute(s -> evoluciones.findByHistoria_IdOrderByFechaAscCreadoEnAscIdAsc(historia)
                .stream().map(EvolucionClinica::getId).toList());

        assertThat(ids).containsExactly(oct05, nov20a, nov20b);
    }

    @Test
    void piezaMapeadaConEnumYAutor() {
        Long historia = insertarHistoria(crearPaciente());
        jdbc.update("INSERT INTO pieza_dental_estado (historia_id, pieza_fdi, estado, observacion, actualizado_por_id)"
                + " VALUES (?, 16, 'A_EXTRAER', 'Obs ficticia', ?)", historia, usuarioSeedId());

        tx.executeWithoutResult(s -> {
            PiezaDentalEstado p = piezas.findByHistoria_IdAndPiezaFdi(historia, (short) 16).orElseThrow();
            assertThat(p.getEstado()).isEqualTo(EstadoPieza.A_EXTRAER);
            assertThat(p.getObservacion()).isEqualTo("Obs ficticia");
            assertThat(p.getActualizadoPor().getId()).isEqualTo(usuarioSeedId());
        });
    }
}
