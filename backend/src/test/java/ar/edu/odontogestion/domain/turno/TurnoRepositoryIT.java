package ar.edu.odontogestion.domain.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.agenda.PrestacionRepository;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.paciente.Paciente;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Cada test corre en una transacción revertida (no deja turnos en el contenedor compartido). */
@Transactional
class TurnoRepositoryIT extends AbstractIntegrationTest {

    private static final Instant T0900 = Instant.parse("2040-03-05T12:00:00Z");
    private static final Instant T0930 = Instant.parse("2040-03-05T12:30:00Z");
    private static final Instant T1000 = Instant.parse("2040-03-05T13:00:00Z");
    private static final Instant T1030 = Instant.parse("2040-03-05T13:30:00Z");
    private static final Instant T1100 = Instant.parse("2040-03-05T14:00:00Z");
    private static final Instant AHORA = Instant.parse("2040-03-01T12:00:00Z");

    @Autowired
    private TurnoRepository turnos;
    @Autowired
    private PacienteRepository pacientes;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private ConsultorioRepository consultorios;
    @Autowired
    private ConsultorioActual consultorioActual;
    @Autowired
    private EntityManager em;

    private Consultorio consultorio;
    private Paciente paciente;
    private Prestacion prestacion;

    @BeforeEach
    void datosBase() {
        consultorio = consultorios.findById(consultorioActual.id()).orElseThrow();
        paciente = pacientes.saveAndFlush(Paciente.nuevo(consultorio,
                new DatosPaciente("Ana", "Ficticia", "39000010", null, "+54 11 5555-0000", "ana-repo@ejemplo.test",
                        null)));
        prestacion = prestaciones.saveAndFlush(Prestacion.nueva(consultorio, "Prestacion de prueba repo", 30));
    }

    private Turno guardar(Turno t) {
        return turnos.saveAndFlush(t);
    }

    private Turno reservado(Instant inicio) {
        return guardar(Turno.reservado(consultorio, paciente, prestacion, inicio, OrigenTurno.ODONTOLOGO));
    }

    @Test
    void buscaPorIdSoloDentroDelConsultorio() {
        Turno t = reservado(T0900);
        em.clear();

        assertThat(turnos.findByIdAndConsultorio_Id(t.getId(), consultorioActual.id())).isPresent();
        assertThat(turnos.findByIdAndConsultorio_Id(t.getId(), consultorioActual.id() + 1000)).isEmpty();
        assertThat(turnos.findByIdAndConsultorio_Id(t.getId() + 1_000_000, consultorioActual.id())).isEmpty();
    }

    @Test
    void listaLosTurnosQueEmpiezanEnElRangoOrdenadosPorInicioYConPacienteYPrestacionCargados() {
        Turno tarde = reservado(T1000);
        Turno cancelado = reservado(T0930);
        cancelado.cancelar(CanceladoPor.ODONTOLOGO, null);
        guardar(cancelado);
        Turno temprano = reservado(T0900);
        reservado(T1100); // empieza justo en "hasta": excluido por el intervalo semiabierto
        em.clear();

        List<Turno> lista = turnos.listarPorInicioEntre(consultorioActual.id(), T0900, T1100);

        assertThat(lista).extracting(Turno::getId).containsExactly(temprano.getId(), cancelado.getId(),
                tarde.getId());
        assertThat(lista).allSatisfy(t -> {
            assertThat(Persistence.getPersistenceUtil().isLoaded(t, "paciente")).isTrue();
            assertThat(Persistence.getPersistenceUtil().isLoaded(t, "prestacion")).isTrue();
        });
        assertThat(lista).extracting(Turno::getEstado).containsExactly(EstadoTurno.RESERVADO,
                EstadoTurno.CANCELADO, EstadoTurno.RESERVADO);
    }

    @Test
    void sinTurnosEnElRangoDevuelveVacio() {
        reservado(T1100);

        assertThat(turnos.listarPorInicioEntre(consultorioActual.id(), T0900, T1100)).isEmpty();
    }

    @Test
    void ocupantesDevuelveSoloLosQueOcupanAgendaYSolapanElRango() {
        Turno reservado = reservado(T0900);
        Turno atendido = reservado(T0930);
        atendido.marcarAtendido();
        guardar(atendido);
        Turno cancelado = reservado(T1000);
        cancelado.cancelar(CanceladoPor.ODONTOLOGO, "x");
        guardar(cancelado);
        Turno pendienteVigente = guardar(Turno.pendiente(consultorio, paciente, prestacion, T1030,
                OrigenTurno.PUBLICA, AHORA.plusSeconds(600)));
        Turno fuera = reservado(T1100);
        em.clear();

        List<Turno> ocupantes = turnos.ocupantesQueSolapan(consultorioActual.id(), T0900, T1100, AHORA);

        assertThat(ocupantes).extracting(Turno::getId).containsExactlyInAnyOrder(reservado.getId(),
                atendido.getId(), pendienteVigente.getId())
                .doesNotContain(cancelado.getId(), fuera.getId());
        assertThat(ocupantes).allSatisfy(t -> assertThat(Persistence.getPersistenceUtil()
                .isLoaded(t, "paciente")).isTrue());
    }

    @Test
    void ocupantesIgnoraPendientesVencidasAunqueNoEstenMarcadasExpiradasYLosContiguos() {
        Turno vencida = guardar(Turno.pendiente(consultorio, paciente, prestacion, T0930, OrigenTurno.PUBLICA,
                AHORA.minusSeconds(60)));
        Turno contiguoAnterior = reservado(T0900); // termina en 09:30 = "desde" del rango consultado
        em.clear();

        List<Turno> ocupantes = turnos.ocupantesQueSolapan(consultorioActual.id(), T0930, T1000, AHORA);

        assertThat(ocupantes).extracting(Turno::getId).doesNotContain(vencida.getId(), contiguoAnterior.getId());
    }

    @Test
    void intervalosOcupadosDevuelveSoloLosRangosParaLaCalculadora() {
        Turno r = reservado(T0900);
        Turno c = reservado(T0930);
        c.cancelar(CanceladoPor.ODONTOLOGO, null);
        guardar(c);
        em.clear();

        List<Hueco> intervalos = turnos.intervalosOcupados(consultorioActual.id(), T0900, T1100, AHORA);

        assertThat(intervalos).containsExactly(new Hueco(r.getInicio(), r.getFin()));
    }
}
