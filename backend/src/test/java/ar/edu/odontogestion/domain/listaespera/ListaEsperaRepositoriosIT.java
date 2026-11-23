package ar.edu.odontogestion.domain.listaespera;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.agenda.PrestacionRepository;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Persistir y releer ambas entidades (mapeo contra el esquema validado) y las consultas del repositorio. */
class ListaEsperaRepositoriosIT extends AbstractListaEsperaIT {

    @Autowired
    private EntradaListaEsperaRepository entradas;
    @Autowired
    private AvisoHuecoRepository avisos;
    @Autowired
    private ConsultorioRepository consultorios;
    @Autowired
    private PacienteRepository pacientes;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private TransactionTemplate tx;

    @Test
    void persisteYRelee() {
        Long pacienteId = crearPaciente();
        Long prestacionId = crearPrestacion(30);
        Long turnoId = insertarTurno(pacienteId, prestacionId, EstadoTurno.CANCELADO, lunes("10:00"), lunes("10:30"));
        Instant limite = reloj.instant().plusSeconds(86400);

        Long[] ids = tx.execute(s -> {
            var consultorio = consultorios.getReferenceById(consultorioId());
            EntradaListaEspera e = entradas.saveAndFlush(EntradaListaEspera.pendiente(consultorio,
                    pacientes.getReferenceById(pacienteId), prestaciones.getReferenceById(prestacionId),
                    LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 27), OrigenEntrada.PUBLICA, limite));
            AvisoHueco a = avisos.saveAndFlush(AvisoHueco.enviado(consultorio, e, turnoId, prestacionId,
                    lunes("10:00"), lunes("10:30"), lunes("09:00")));
            return new Long[] {e.getId(), a.getId()};
        });

        tx.executeWithoutResult(s -> {
            EntradaListaEspera e = entradas.findByIdAndConsultorio_Id(ids[0], consultorioId()).orElseThrow();
            assertThat(e.getEstado()).isEqualTo(EstadoEntrada.PENDIENTE_CONFIRMACION);
            assertThat(e.getFechaDesde()).isEqualTo(LocalDate.of(2026, 11, 23));
            assertThat(e.getPendienteHasta()).isEqualTo(limite);
            assertThat(e.getOrigen()).isEqualTo(OrigenEntrada.PUBLICA);
            assertThat(e.getCreadoEn()).isNotNull();
            AvisoHueco a = avisos.findByIdAndConsultorio_Id(ids[1], consultorioId()).orElseThrow();
            assertThat(a.getEstado()).isEqualTo(EstadoAviso.ENVIADO);
            assertThat(a.getInicio()).isEqualTo(lunes("10:00"));
            assertThat(a.getEntrada().getId()).isEqualTo(ids[0]);
        });
    }

    @Test
    void buscarCompatiblesFiltraPorEstadoPrestacionFechaYPacienteActivo() {
        Long prestacion = crearPrestacion(30);
        Long otra = crearPrestacion(30);
        Long ok = crearPaciente();
        Long bajaP = crearPacienteDeBaja();
        LocalDate lunes = LocalDate.of(2026, 11, 23);
        Long compatible = insertarEntrada(ok, prestacion, "ACTIVA", lunes, lunes);
        insertarEntrada(crearPaciente(), prestacion, "PENDIENTE_CONFIRMACION", lunes, lunes);
        insertarEntrada(crearPaciente(), prestacion, "CANCELADA", lunes, lunes);
        insertarEntrada(crearPaciente(), otra, "ACTIVA", lunes, lunes);
        insertarEntrada(crearPaciente(), prestacion, "ACTIVA", lunes.plusDays(1), lunes.plusDays(5));
        insertarEntrada(bajaP, prestacion, "ACTIVA", lunes, lunes);

        List<EntradaListaEspera> r = tx.execute(s -> entradas.buscarCompatibles(consultorioId(), prestacion, lunes));

        assertThat(r).extracting(EntradaListaEspera::getId).containsExactly(compatible);
    }

    @Test
    void insertarSiNoExisteEsIdempotente() {
        Long pacienteId = crearPaciente();
        Long prestacionId = crearPrestacion(30);
        Long turnoId = insertarTurno(pacienteId, prestacionId, EstadoTurno.CANCELADO, lunes("10:00"), lunes("10:30"));
        Long entrada = insertarEntrada(pacienteId, prestacionId, "ACTIVA", LocalDate.of(2026, 11, 23),
                LocalDate.of(2026, 11, 23));
        List<Long> primero = tx.execute(s -> avisos.insertarSiNoExiste(consultorioId(), entrada, turnoId,
                prestacionId, lunes("10:00"), lunes("10:30"), lunes("09:00"), reloj.instant()));
        List<Long> segundo = tx.execute(s -> avisos.insertarSiNoExiste(consultorioId(), entrada, turnoId,
                prestacionId, lunes("10:00"), lunes("10:30"), lunes("09:00"), reloj.instant()));
        assertThat(primero).hasSize(1);
        assertThat(segundo).isEmpty();
    }
}
