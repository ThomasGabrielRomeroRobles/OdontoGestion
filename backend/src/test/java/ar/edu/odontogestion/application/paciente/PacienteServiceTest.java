package ar.edu.odontogestion.application.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.ConflictoException;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.paciente.Paciente;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    private static final Long CONSULTORIO = 1L;
    private static final Instant AHORA = Instant.parse("2026-11-20T12:00:00Z");

    @Mock
    private ConsultorioActual actual;

    @Mock
    private ConsultorioRepository consultorios;

    @Mock
    private PacienteRepository pacientes;

    private final Consultorio consultorio = Consultorio.nuevo("C", "UTC");
    private PacienteService servicio;

    @BeforeEach
    void preparar() {
        lenient().when(actual.id()).thenReturn(CONSULTORIO);
        lenient().when(consultorios.getReferenceById(CONSULTORIO)).thenReturn(consultorio);
        lenient().when(pacientes.saveAndFlush(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));
        servicio = new PacienteService(actual, consultorios, pacientes, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static DatosPaciente datos(String dni, String telefono) {
        return new DatosPaciente("Ana", "Pérez", dni, null, telefono, "ana@ejemplo.test", null);
    }

    private Paciente existente(Long id, String dni) {
        Paciente p = Paciente.nuevo(consultorio, datos(dni, "+54 11 5555-0001"));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static DataIntegrityViolationException violacion(String restriccion) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", null, restriccion));
    }

    // ---- crear

    @Test
    void crearValidoDevuelveLaVistaActivaYSinAusencias() {
        when(pacientes.findByConsultorioIdAndDni(CONSULTORIO, "30123456")).thenReturn(Optional.empty());

        PacienteVista vista = servicio.crear(datos("30.123.456", "+54 11 5555-0001"));

        assertThat(vista.dni()).isEqualTo("30123456");
        assertThat(vista.cantidadAusencias()).isZero();
        assertThat(vista.activo()).isTrue();
        verify(pacientes).saveAndFlush(any(Paciente.class));
    }

    @Test
    void crearConDniExistenteEsConflictoSinElDniEnElMensajeYSinGuardar() {
        when(pacientes.findByConsultorioIdAndDni(CONSULTORIO, "30123456"))
                .thenReturn(Optional.of(existente(5L, "30123456")));

        assertThatThrownBy(() -> servicio.crear(datos("30.123.456", "1")))
                .isInstanceOf(ConflictoException.class)
                .hasMessageNotContaining("30123456");
        verify(pacientes, never()).saveAndFlush(any());
    }

    @Test
    void crearTraduceLaViolacionDeUnicidadDelDniAConflicto() {
        when(pacientes.findByConsultorioIdAndDni(CONSULTORIO, "30123456")).thenReturn(Optional.empty());
        when(pacientes.saveAndFlush(any(Paciente.class))).thenThrow(violacion("uq_paciente_consultorio_dni"));

        assertThatThrownBy(() -> servicio.crear(datos("30123456", "1")))
                .isInstanceOf(ConflictoException.class)
                .hasMessageNotContaining("30123456");
    }

    @Test
    void crearRelanzaOtrasViolacionesDeIntegridad() {
        when(pacientes.findByConsultorioIdAndDni(CONSULTORIO, "30123456")).thenReturn(Optional.empty());
        DataIntegrityViolationException otra = violacion("paciente_telefono_check");
        when(pacientes.saveAndFlush(any(Paciente.class))).thenThrow(otra);

        assertThatThrownBy(() -> servicio.crear(datos("30123456", "1"))).isSameAs(otra);
    }

    // ---- obtener

    @Test
    void obtenerDeOtroConsultorioOInexistenteEsNoEncontrado() {
        when(pacientes.findByIdAndConsultorioId(99L, CONSULTORIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtenerDevuelveLaVista() {
        when(pacientes.findByIdAndConsultorioId(5L, CONSULTORIO)).thenReturn(Optional.of(existente(5L, "30123456")));

        assertThat(servicio.obtener(5L).id()).isEqualTo(5L);
    }

    // ---- actualizar

    @Test
    void actualizarInexistenteEsNoEncontrado() {
        when(pacientes.findByIdAndConsultorioId(99L, CONSULTORIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(99L, datos("30123456", "1")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizarAUnDniDeOtroPacienteEsConflictoYNoModifica() {
        Paciente b = existente(6L, "30123457");
        when(pacientes.findByIdAndConsultorioId(6L, CONSULTORIO)).thenReturn(Optional.of(b));
        when(pacientes.findByConsultorioIdAndDni(CONSULTORIO, "30123456"))
                .thenReturn(Optional.of(existente(5L, "30123456")));

        assertThatThrownBy(() -> servicio.actualizar(6L, datos("30123456", "1")))
                .isInstanceOf(ConflictoException.class);
        assertThat(b.getDni()).isEqualTo("30123457");
    }

    @Test
    void actualizarConservandoElPropioDniEnOtroFormatoEsValido() {
        Paciente a = existente(5L, "30123456");
        when(pacientes.findByIdAndConsultorioId(5L, CONSULTORIO)).thenReturn(Optional.of(a));
        when(pacientes.findByConsultorioIdAndDni(CONSULTORIO, "30123456")).thenReturn(Optional.of(a));

        PacienteVista vista = servicio.actualizar(5L, datos("30.123.456", "+54 11 5555-0002"));

        assertThat(vista.telefono()).isEqualTo("+54 11 5555-0002");
    }

    @Test
    void actualizarTraduceLaViolacionDeUnicidadAConflicto() {
        Paciente a = existente(5L, "30123456");
        when(pacientes.findByIdAndConsultorioId(5L, CONSULTORIO)).thenReturn(Optional.of(a));
        when(pacientes.findByConsultorioIdAndDni(CONSULTORIO, "30999999")).thenReturn(Optional.empty());
        when(pacientes.saveAndFlush(a)).thenThrow(violacion("uq_paciente_consultorio_dni"));

        assertThatThrownBy(() -> servicio.actualizar(5L, datos("30999999", "1")))
                .isInstanceOf(ConflictoException.class);
    }

    // ---- buscarOCrearPorDni

    @Test
    void buscarOCrearConDniExistenteDevuelveElPacienteSinEscribir() {
        Paciente a = existente(5L, "30123456");
        when(pacientes.findByConsultorioIdAndDni(CONSULTORIO, "30123456")).thenReturn(Optional.of(a));

        ResultadoBuscarOCrear r = servicio.buscarOCrearPorDni(
                new DatosPaciente("Otra", "Persona", "30.123.456", null, "2", "otra@ejemplo.test", null));

        assertThat(r.creado()).isFalse();
        assertThat(r.paciente().id()).isEqualTo(5L);
        assertThat(r.paciente().email()).isEqualTo("ana@ejemplo.test");
        verify(pacientes, never()).insertarSiNoExiste(anyLong(), anyString(), anyString(), anyString(), any(),
                anyString(), anyString(), anyString(), any());
        verify(pacientes, never()).saveAndFlush(any());
    }
}
