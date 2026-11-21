package ar.edu.odontogestion.application.consultorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;
import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsultorioServiceTest {

    private static final Long ID = 7L;
    private static final List<String> CAMPOS = List.of("granularidadMin", "anticipacionMinReservaMin",
            "limiteCancelacionHoras", "recordatorioHorasAntes", "vencimientoAvisoMin", "vencimientoPendienteMin",
            "toleranciaAusenteMin");

    @Mock
    private ConsultorioActual actual;

    @Mock
    private ConsultorioRepository repositorio;

    private Consultorio consultorio;
    private ConsultorioService servicio;

    @BeforeEach
    void preparar() {
        consultorio = Consultorio.nuevo("Antes", "America/Argentina/Buenos_Aires");
        // lenient: en los casos de validación el servicio falla antes de cargar el consultorio
        lenient().when(actual.id()).thenReturn(ID);
        lenient().when(repositorio.findById(ID)).thenReturn(Optional.of(consultorio));
        servicio = new ConsultorioService(actual, repositorio);
    }

    private static DatosConsultorio datos(ParametrosConsultorio parametros) {
        return new DatosConsultorio("Despues", "Calle 1", "123", "c@ejemplo.test", parametros);
    }

    private static ParametrosConsultorio conCampoEn(int indice, int valor) {
        int[] v = {15, 120, 2, 24, 60, 30, 15};
        v[indice] = valor;
        return new ParametrosConsultorio(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
    }

    @Test
    void obtenerDevuelveUnaVistaConDatosZonaYParametros() {
        ConsultorioVista vista = servicio.obtener();

        assertThat(vista).isEqualTo(new ConsultorioVista("Antes", "", "", "", "America/Argentina/Buenos_Aires",
                ParametrosConsultorio.porDefecto()));
    }

    @Test
    void actualizarAplicaDatosYParametrosYDevuelveLaVistaResultante() {
        ParametrosConsultorio nuevos = new ParametrosConsultorio(30, 60, 4, 48, 90, 45, 10);

        ConsultorioVista vista = servicio.actualizar(datos(nuevos));

        assertThat(consultorio.getNombre()).isEqualTo("Despues");
        assertThat(consultorio.getParametros()).isEqualTo(nuevos);
        assertThat(vista).isEqualTo(new ConsultorioVista("Despues", "Calle 1", "123", "c@ejemplo.test",
                "America/Argentina/Buenos_Aires", nuevos));
    }

    @Test
    void laZonaHorariaNuncaCambia() {
        servicio.actualizar(datos(new ParametrosConsultorio(30, 60, 4, 48, 90, 45, 10)));

        assertThat(consultorio.getZonaHoraria()).isEqualTo("America/Argentina/Buenos_Aires");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void cadaUnoDeLosSieteParametrosNoPositivosSeRechazaConSuCampoYNoModificaNada(int indice) {
        ParametrosConsultorio antes = consultorio.getParametros();

        assertThatThrownBy(() -> servicio.actualizar(datos(conCampoEn(indice, 0))))
                .isInstanceOfSatisfying(ValidacionDominioException.class, e ->
                        assertThat(e.errores()).extracting(ErrorCampo::field)
                                .containsExactly("parametros." + CAMPOS.get(indice)));

        assertThat(consultorio.getParametros()).isEqualTo(antes);
        assertThat(consultorio.getNombre()).isEqualTo("Antes");
    }

    @Test
    void variosParametrosInvalidosSeReportanJuntosYUnNegativoTambienCuenta() {
        ParametrosConsultorio invalidos = new ParametrosConsultorio(-1, 120, 2, 24, 60, -5, 0);

        assertThatThrownBy(() -> servicio.actualizar(datos(invalidos)))
                .isInstanceOfSatisfying(ValidacionDominioException.class, e ->
                        assertThat(e.errores()).extracting(ErrorCampo::field)
                                .containsExactlyInAnyOrder("parametros.granularidadMin",
                                        "parametros.vencimientoPendienteMin", "parametros.toleranciaAusenteMin"));

        assertThat(consultorio.getParametros()).isEqualTo(ParametrosConsultorio.porDefecto());
    }

    @Test
    void todosLosParametrosEnUnoSonValidos() {
        servicio.actualizar(datos(new ParametrosConsultorio(1, 1, 1, 1, 1, 1, 1)));

        assertThat(consultorio.getParametros()).isEqualTo(new ParametrosConsultorio(1, 1, 1, 1, 1, 1, 1));
    }

    @Test
    void parametrosVigentesLeeDeLaBaseEnCadaLlamada() {
        assertThat(servicio.parametrosVigentes()).isEqualTo(ParametrosConsultorio.porDefecto());

        consultorio.actualizarParametros(new ParametrosConsultorio(30, 120, 2, 24, 60, 30, 15));

        assertThat(servicio.parametrosVigentes().granularidadMin()).isEqualTo(30);
        verify(repositorio, times(2)).findById(ID);
    }
}
