package ar.edu.odontogestion.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mapeo JPA de V4 (pasa {@code ddl-auto=validate} si el contexto arranca) y consultas de los repositorios.
 * Todo corre en transacciones revertidas: no deja datos para otros tests.
 */
class AgendaConfigRepositoriesIT extends AbstractIntegrationTest {

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private ConsultorioRepository consultorios;

    @Autowired
    private PrestacionRepository prestaciones;

    @Autowired
    private HorarioTrabajoRepository horarios;

    @Autowired
    private BloqueoAgendaRepository bloqueos;

    private void revertida(Consumer<Consultorio> bloque) {
        tx.executeWithoutResult(status -> {
            bloque.accept(consultorios.findFirstByOrderByIdAsc().orElseThrow());
            status.setRollbackOnly();
        });
    }

    @Test
    void prestacionSeGuardaConAuditoriaYConsultorio() {
        revertida(consultorio -> {
            Prestacion guardada = prestaciones.saveAndFlush(Prestacion.nueva(consultorio, "Blanqueamiento", 60));
            assertThat(guardada.getId()).isNotNull();
            assertThat(guardada.getCreadoEn()).isNotNull();
            assertThat(guardada.getActualizadoEn()).isNotNull();
            assertThat(guardada.isActiva()).isTrue();
            assertThat(guardada.getConsultorio().getId()).isEqualTo(consultorio.getId());
        });
    }

    @Test
    void prestacionesDelConsultorioOrdenadasPorNombreIncluidoElSeed() {
        revertida(consultorio -> {
            prestaciones.saveAndFlush(Prestacion.nueva(consultorio, "Aaa primera", 15));
            List<String> nombres = prestaciones.findByConsultorio_IdOrderByNombreAsc(consultorio.getId()).stream()
                    .map(Prestacion::getNombre).toList();
            assertThat(nombres).startsWith("Aaa primera").contains("Consulta", "Control", "Endodoncia");
            assertThat(nombres).isSorted();
        });
    }

    @Test
    void existeNombreSinDistinguirMayusculasYExcluyendoLaPropia() {
        revertida(consultorio -> {
            Long cid = consultorio.getId();
            assertThat(prestaciones.existsByConsultorio_IdAndNombreIgnoreCase(cid, "CONSULTA")).isTrue();
            assertThat(prestaciones.existsByConsultorio_IdAndNombreIgnoreCase(cid, "Inexistente")).isFalse();
            Long idConsulta = prestaciones.findByConsultorio_IdOrderByNombreAsc(cid).stream()
                    .filter(p -> p.getNombre().equals("Consulta")).findFirst().orElseThrow().getId();
            assertThat(prestaciones.existsByConsultorio_IdAndNombreIgnoreCaseAndIdNot(cid, "consulta", idConsulta))
                    .isFalse();
        });
    }

    @Test
    void horarioMapeaSmallintYHorasYSeOrdenaPorDiaYHora() {
        revertida(consultorio -> {
            HorarioTrabajo sabado = horarios.saveAndFlush(
                    HorarioTrabajo.nuevo(consultorio, 6, LocalTime.of(9, 0), LocalTime.of(12, 0)));
            assertThat(sabado.getDiaSemana()).isEqualTo(6);
            assertThat(sabado.getCreadoEn()).isNotNull();

            List<String> orden = horarios.findByConsultorio_IdOrderByDiaSemanaAscHoraDesdeAsc(consultorio.getId())
                    .stream().map(h -> h.getDiaSemana() + " " + h.getHoraDesde()).toList();
            assertThat(orden).startsWith("1 09:00", "1 14:00", "2 09:00").endsWith("6 09:00");

            assertThat(horarios.findByConsultorio_IdAndDiaSemana(consultorio.getId(), (short) 1)).hasSize(2);
        });
    }

    @Test
    void bloqueoGuardaMotivoVacioYSeConsultaPorInterseccion() {
        revertida(consultorio -> {
            Long cid = consultorio.getId();
            Instant base = Instant.parse("2030-01-10T00:00:00Z");
            BloqueoAgenda pasado = bloqueos.saveAndFlush(
                    BloqueoAgenda.nuevo(consultorio, base.minusSeconds(7200), base.minusSeconds(3600), ""));
            BloqueoAgenda proximo = bloqueos.saveAndFlush(
                    BloqueoAgenda.nuevo(consultorio, base.plusSeconds(3600), base.plusSeconds(7200), "Curso"));
            BloqueoAgenda lejano = bloqueos.saveAndFlush(
                    BloqueoAgenda.nuevo(consultorio, base.plusSeconds(86_400 * 60), base.plusSeconds(86_400 * 61), ""));
            assertThat(pasado.getMotivo()).isEmpty();
            assertThat(pasado.getCreadoEn()).isNotNull();

            assertThat(bloqueos.findByConsultorio_IdAndHastaGreaterThanOrderByDesdeAsc(cid, base))
                    .extracting(BloqueoAgenda::getId).containsExactly(proximo.getId(), lejano.getId());
            assertThat(bloqueos.findByConsultorio_IdAndHastaGreaterThanAndDesdeLessThanOrderByDesdeAsc(
                    cid, base, base.plusSeconds(86_400 * 30)))
                    .extracting(BloqueoAgenda::getId).containsExactly(proximo.getId());
        });
    }
}
