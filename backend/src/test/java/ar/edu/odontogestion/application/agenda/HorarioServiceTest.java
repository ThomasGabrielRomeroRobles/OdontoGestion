package ar.edu.odontogestion.application.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import ar.edu.odontogestion.domain.agenda.HorarioTrabajo;
import ar.edu.odontogestion.domain.agenda.HorarioTrabajoRepository;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;
import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HorarioServiceTest {

    private static final Long CID = 7L;

    @Mock
    private ConsultorioActual actual;

    @Mock
    private ConsultorioService consultorioService;

    @Mock
    private ConsultorioRepository consultorios;

    @Mock
    private HorarioTrabajoRepository horarios;

    private Consultorio consultorio;
    private HorarioService servicio;

    @BeforeEach
    void preparar() {
        consultorio = Consultorio.nuevo("Consultorio Demo", "America/Argentina/Buenos_Aires");
        lenient().when(actual.id()).thenReturn(CID);
        lenient().when(consultorios.getReferenceById(CID)).thenReturn(consultorio);
        lenient().when(consultorioService.parametrosVigentes())
                .thenReturn(new ParametrosConsultorio(15, 120, 2, 24, 60, 30, 15));
        lenient().when(horarios.saveAndFlush(any(HorarioTrabajo.class))).thenAnswer(i -> i.getArgument(0));
        servicio = new HorarioService(actual, consultorioService, consultorios, horarios);
    }

    /** Franja existente del lunes con id fijo (el id lo asigna la base; acá se simula). */
    private HorarioTrabajo lunes(long id, int h1, int h2) {
        HorarioTrabajo h = HorarioTrabajo.nuevo(consultorio, 1, LocalTime.of(h1, 0), LocalTime.of(h2, 0));
        ReflectionTestUtils.setField(h, "id", id);
        return h;
    }

    private void existentesDelLunes(HorarioTrabajo... franjas) {
        lenient().when(horarios.findByConsultorio_IdAndDiaSemana(CID, (short) 1)).thenReturn(List.of(franjas));
    }

    private static void esperaErrorEn(Runnable accion, String campo) {
        assertThatThrownBy(accion::run).isInstanceOf(ValidacionDominioException.class)
                .satisfies(e -> assertThat(((ValidacionDominioException) e).errores())
                        .extracting(ErrorCampo::field).contains(campo));
    }

    @Test
    void altaValidaGuardaYDevuelveHorasEnFormatoHHmm() {
        HorarioVista vista = servicio.crear(new DatosHorario(6, "09:00", "12:00"));
        assertThat(vista.diaSemana()).isEqualTo(6);
        assertThat(vista.horaDesde()).isEqualTo("09:00");
        assertThat(vista.horaHasta()).isEqualTo("12:00");
        verify(horarios).saveAndFlush(any(HorarioTrabajo.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"9", "25:00", "ab", "09:00:00", ""})
    void horaDesdeMalFormadaDa422EnSuCampo(String valor) {
        esperaErrorEn(() -> servicio.crear(new DatosHorario(6, valor, "12:00")), "horaDesde");
    }

    @Test
    void horaHastaMalFormadaDa422EnSuCampo() {
        esperaErrorEn(() -> servicio.crear(new DatosHorario(6, "09:00", "24:30")), "horaHasta");
    }

    @ParameterizedTest
    @ValueSource(strings = {"13:00", "14:00"})
    void finNoPosteriorAlInicioDa422EnHoraHasta(String hasta) {
        esperaErrorEn(() -> servicio.crear(new DatosHorario(6, "14:00", hasta)), "horaHasta");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, -1})
    void diaFueraDeRangoDa422(int dia) {
        esperaErrorEn(() -> servicio.crear(new DatosHorario(dia, "09:00", "12:00")), "diaSemana");
    }

    @Test
    void horaNoAlineadaALaGranularidadDa422() {
        esperaErrorEn(() -> servicio.crear(new DatosHorario(6, "09:10", "12:00")), "horaDesde");
        esperaErrorEn(() -> servicio.crear(new DatosHorario(6, "09:00", "12:05")), "horaHasta");
    }

    @Test
    void solapamientoDetectadoAntesDeGuardarDa422EnHoraDesde() {
        existentesDelLunes(lunes(10, 9, 13), lunes(11, 14, 18));
        esperaErrorEn(() -> servicio.crear(new DatosHorario(1, "12:00", "15:00")), "horaDesde");
        verify(horarios, never()).saveAndFlush(any());
    }

    @Test
    void franjaContiguaNoSeSolapa() {
        existentesDelLunes(lunes(10, 9, 13), lunes(11, 14, 18));
        assertThat(servicio.crear(new DatosHorario(1, "13:00", "14:00")).horaDesde()).isEqualTo("13:00");
    }

    @Test
    void alEditarNoChocaConsigoMisma() {
        HorarioTrabajo manana = lunes(10, 9, 13);
        existentesDelLunes(manana, lunes(11, 14, 18));
        when(horarios.findByIdAndConsultorio_Id(10L, CID)).thenReturn(Optional.of(manana));

        HorarioVista vista = servicio.actualizar(10L, new DatosHorario(1, "08:00", "13:00"));

        assertThat(vista.horaDesde()).isEqualTo("08:00");
    }

    @Test
    void alEditarDetectaChoqueConOtraFranja() {
        HorarioTrabajo tarde = lunes(11, 14, 18);
        existentesDelLunes(lunes(10, 9, 13), tarde);
        when(horarios.findByIdAndConsultorio_Id(11L, CID)).thenReturn(Optional.of(tarde));

        esperaErrorEn(() -> servicio.actualizar(11L, new DatosHorario(1, "12:00", "18:00")), "horaDesde");
        assertThat(tarde.getHoraDesde()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void laRestriccionDeExclusionSeTraduceA422EnHoraDesde() {
        when(horarios.saveAndFlush(any(HorarioTrabajo.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: conflicting key value violates exclusion constraint "
                        + "\"ex_horario_trabajo_sin_solapamiento\"")));
        esperaErrorEn(() -> servicio.crear(new DatosHorario(6, "09:00", "12:00")), "horaDesde");
    }

    @Test
    void edicionDeInexistenteDa404() {
        when(horarios.findByIdAndConsultorio_Id(99L, CID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.actualizar(99L, new DatosHorario(1, "09:00", "10:00")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void bajaDeInexistenteDa404() {
        when(horarios.findByIdAndConsultorio_Id(99L, CID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.eliminar(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void bajaEliminaLaFranja() {
        HorarioTrabajo manana = lunes(10, 9, 13);
        when(horarios.findByIdAndConsultorio_Id(10L, CID)).thenReturn(Optional.of(manana));
        servicio.eliminar(10L);
        verify(horarios).delete(manana);
    }
}
