package ar.edu.odontogestion.application.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.agenda.PrestacionRepository;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;
import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PrestacionServiceTest {

    private static final Long CID = 7L;

    @Mock
    private ConsultorioActual actual;

    @Mock
    private ConsultorioService consultorioService;

    @Mock
    private ConsultorioRepository consultorios;

    @Mock
    private PrestacionRepository prestaciones;

    private Consultorio consultorio;
    private PrestacionService servicio;

    @BeforeEach
    void preparar() {
        consultorio = Consultorio.nuevo("Consultorio Demo", "America/Argentina/Buenos_Aires");
        lenient().when(actual.id()).thenReturn(CID);
        lenient().when(consultorios.getReferenceById(CID)).thenReturn(consultorio);
        granularidad(15);
        lenient().when(prestaciones.saveAndFlush(any(Prestacion.class))).thenAnswer(i -> i.getArgument(0));
        servicio = new PrestacionService(actual, consultorioService, consultorios, prestaciones);
    }

    private void granularidad(int minutos) {
        lenient().when(consultorioService.parametrosVigentes())
                .thenReturn(new ParametrosConsultorio(minutos, 120, 2, 24, 60, 30, 15));
    }

    private static DataIntegrityViolationException violacion(String restriccion) {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ERROR: violates constraint \"" + restriccion + "\""));
    }

    private static void esperaErrorEn(Runnable accion, String campo) {
        assertThatThrownBy(accion::run).isInstanceOf(ValidacionDominioException.class)
                .satisfies(e -> assertThat(((ValidacionDominioException) e).errores())
                        .extracting(ErrorCampo::field).contains(campo));
    }

    @Test
    void altaValidaGuardaActivaYNormalizaElNombre() {
        PrestacionVista vista = servicio.crear(new DatosPrestacion("  Blanqueamiento ", 60));
        assertThat(vista.nombre()).isEqualTo("Blanqueamiento");
        assertThat(vista.duracionMin()).isEqualTo(60);
        assertThat(vista.activa()).isTrue();
        verify(prestaciones).saveAndFlush(any(Prestacion.class));
    }

    @Test
    void duracionNoMultiploDeLaGranularidadDa422() {
        esperaErrorEn(() -> servicio.crear(new DatosPrestacion("Rara", 40)), "duracionMin");
        verify(prestaciones, never()).saveAndFlush(any());
    }

    @Test
    void laGranularidadSeLeeDeLosParametrosVigentes() {
        granularidad(30);
        esperaErrorEn(() -> servicio.crear(new DatosPrestacion("Rara", 45)), "duracionMin");
        assertThat(servicio.crear(new DatosPrestacion("Media hora", 60)).duracionMin()).isEqualTo(60);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -15})
    void duracionNoPositivaDa422(int duracion) {
        esperaErrorEn(() -> servicio.crear(new DatosPrestacion("Rara", duracion)), "duracionMin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void nombreVacioDa422(String nombre) {
        esperaErrorEn(() -> servicio.crear(new DatosPrestacion(nombre, 30)), "nombre");
    }

    @Test
    void nombreDeMasDeOchentaCaracteresDa422() {
        esperaErrorEn(() -> servicio.crear(new DatosPrestacion("x".repeat(81), 30)), "nombre");
    }

    @Test
    void erroresDeNombreYDuracionSeInformanJuntos() {
        assertThatThrownBy(() -> servicio.crear(new DatosPrestacion("", 40)))
                .isInstanceOf(ValidacionDominioException.class)
                .satisfies(e -> assertThat(((ValidacionDominioException) e).errores())
                        .extracting(ErrorCampo::field).containsExactlyInAnyOrder("nombre", "duracionMin"));
    }

    @Test
    void nombreRepetidoDetectadoAntesDeGuardarDa422() {
        when(prestaciones.existsByConsultorio_IdAndNombreIgnoreCase(CID, "consulta")).thenReturn(true);
        esperaErrorEn(() -> servicio.crear(new DatosPrestacion("consulta", 30)), "nombre");
        verify(prestaciones, never()).saveAndFlush(any());
    }

    @Test
    void laRestriccionUnicaDeLaBaseSeTraduceA422EnNombre() {
        when(prestaciones.saveAndFlush(any(Prestacion.class))).thenThrow(violacion("uq_prestacion_consultorio_nombre"));
        esperaErrorEn(() -> servicio.crear(new DatosPrestacion("Consulta", 30)), "nombre");
    }

    @Test
    void otraRestriccionSeRelanzaSinTraducir() {
        DataIntegrityViolationException otra = violacion("ck_otra_cosa");
        when(prestaciones.saveAndFlush(any(Prestacion.class))).thenThrow(otra);
        assertThatThrownBy(() -> servicio.crear(new DatosPrestacion("Consulta", 30))).isSameAs(otra);
    }

    @Test
    void edicionValidaCambiaNombreYDuracion() {
        Prestacion existente = Prestacion.nueva(consultorio, "Consulta", 30);
        when(prestaciones.findByIdAndConsultorio_Id(5L, CID)).thenReturn(Optional.of(existente));

        PrestacionVista vista = servicio.actualizar(5L, new DatosPrestacion("Consulta", 45));

        assertThat(vista.duracionMin()).isEqualTo(45);
        verify(prestaciones).existsByConsultorio_IdAndNombreIgnoreCaseAndIdNot(eq(CID), eq("Consulta"), any());
    }

    @Test
    void edicionValidaLaDuracionContraLaGranularidad() {
        when(prestaciones.findByIdAndConsultorio_Id(5L, CID))
                .thenReturn(Optional.of(Prestacion.nueva(consultorio, "Consulta", 30)));
        esperaErrorEn(() -> servicio.actualizar(5L, new DatosPrestacion("Consulta", 40)), "duracionMin");
    }

    @Test
    void edicionDeInexistenteDa404() {
        when(prestaciones.findByIdAndConsultorio_Id(99L, CID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.actualizar(99L, new DatosPrestacion("X", 30)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void desactivarNoValidaLaDuracionAunqueYaNoSeaMultiplo() {
        granularidad(30);
        Prestacion limpieza = Prestacion.nueva(consultorio, "Limpieza", 45);
        when(prestaciones.findByIdAndConsultorio_Id(3L, CID)).thenReturn(Optional.of(limpieza));

        assertThat(servicio.desactivar(3L).activa()).isFalse();
        assertThat(servicio.activar(3L).activa()).isTrue();
        verify(prestaciones, never()).existsByConsultorio_IdAndNombreIgnoreCaseAndIdNot(anyLong(), anyString(), any());
    }

    @Test
    void desactivarYActivarSonIdempotentes() {
        Prestacion p = Prestacion.nueva(consultorio, "Control", 15);
        when(prestaciones.findByIdAndConsultorio_Id(4L, CID)).thenReturn(Optional.of(p));

        servicio.desactivar(4L);
        assertThat(servicio.desactivar(4L).activa()).isFalse();
        servicio.activar(4L);
        assertThat(servicio.activar(4L).activa()).isTrue();
    }

    @Test
    void desactivarInexistenteDa404() {
        when(prestaciones.findByIdAndConsultorio_Id(99L, CID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.desactivar(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
