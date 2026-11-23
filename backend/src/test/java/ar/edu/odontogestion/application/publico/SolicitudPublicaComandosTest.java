package ar.edu.odontogestion.application.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.application.agenda.DisponibilidadService;
import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.error.DemasiadasSolicitudesException;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SolicitudPublicaComandosTest {

    private static final Instant INICIO = Instant.parse("2026-11-23T13:00:00Z");
    private static final DatosSolicitudPublica DATOS = new DatosSolicitudPublica(3L, INICIO,
            new DatosPaciente("Beto", "Ficticio", "30.123.456", null, "+54 11 4444-0000", "beto@ejemplo.test", null));

    private final LimitadorSolicitudesPublicas limitador = mock(LimitadorSolicitudesPublicas.class);
    private final SolicitudPublicaService servicio = mock(SolicitudPublicaService.class);
    private final DisponibilidadService disponibilidad = mock(DisponibilidadService.class);
    private final SolicitudPublicaComandos comandos =
            new SolicitudPublicaComandos(limitador, servicio, disponibilidad);

    @Test
    void ordenIpLuegoIdentidadLuegoServicio() {
        comandos.solicitar("10.0.0.1", DATOS, false);

        InOrder orden = inOrder(limitador, servicio);
        orden.verify(limitador).registrarIp("10.0.0.1");
        orden.verify(limitador).registrarIdentidad("30.123.456", "beto@ejemplo.test");
        orden.verify(servicio).solicitar(DATOS);
    }

    @Test
    void conSenueloConTextoCuentaParaLaIpPeroNoLlamaAIdentidadNiAlServicio(CapturedOutput salida) {
        comandos.solicitar("10.0.0.1", DATOS, true);

        verify(limitador).registrarIp("10.0.0.1");
        verify(limitador, never()).registrarIdentidad(anyString(), anyString());
        verifyNoInteractions(servicio);
        assertThat(salida.getAll()).contains("descartada por señuelo").doesNotContain("10.0.0.1")
                .doesNotContain("beto@ejemplo.test").doesNotContain("30.123.456");
    }

    @Test
    void siLaIpExcedeElLimiteNoSeEvaluaNadaMas() {
        doThrow(new DemasiadasSolicitudesException(60)).when(limitador).registrarIp("10.0.0.1");

        assertThatThrownBy(() -> comandos.solicitar("10.0.0.1", DATOS, false))
                .isInstanceOf(DemasiadasSolicitudesException.class);

        verify(limitador, never()).registrarIdentidad(anyString(), anyString());
        verifyNoInteractions(servicio);
    }

    @Test
    void siLaIdentidadExcedeElLimiteNoSeLlamaAlServicio() {
        doThrow(new DemasiadasSolicitudesException(60)).when(limitador).registrarIdentidad(anyString(), anyString());

        assertThatThrownBy(() -> comandos.solicitar("10.0.0.1", DATOS, false))
                .isInstanceOf(DemasiadasSolicitudesException.class);

        verifyNoInteractions(servicio);
    }

    @Test
    void unConflictoDeAgendaSeCompletaConLasAlternativasPublicasFueraDeLaTransaccion() {
        List<Hueco> alternativas = List.of(new Hueco(INICIO.plusSeconds(1800), INICIO.plusSeconds(3600)));
        when(servicio.solicitar(any())).thenThrow(new HuecoNoDisponibleException());
        when(disponibilidad.alternativasPublicas(3L, INICIO, 3)).thenReturn(alternativas);

        assertThatThrownBy(() -> comandos.solicitar("10.0.0.1", DATOS, false))
                .isInstanceOfSatisfying(HuecoNoDisponibleException.class,
                        e -> assertThat(e.alternativas()).isEqualTo(alternativas));
    }

    @Test
    void otroConflictoUsaSusPropiasAlternativas() {
        when(servicio.solicitar(any())).thenThrow(new HuecoNoDisponibleException());
        when(disponibilidad.alternativasPublicas(3L, INICIO, 3)).thenReturn(List.of());

        assertThatThrownBy(() -> comandos.solicitar("10.0.0.2", DATOS, false))
                .isInstanceOfSatisfying(HuecoNoDisponibleException.class,
                        e -> assertThat(e.alternativas()).isEmpty());
    }
}
