package ar.edu.odontogestion.application.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.paciente.Paciente;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.turno.CanceladoPor;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.OrigenTurno;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import ar.edu.odontogestion.domain.turno.evento.TurnoCancelado;
import ar.edu.odontogestion.domain.turno.evento.TurnoCreado;
import ar.edu.odontogestion.domain.turno.evento.TurnoMovido;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(OutputCaptureExtension.class)
class NotificacionesTurnoListenerTest {

    private static final Instant INICIO = Instant.parse("2026-11-23T13:00:00Z");
    private static final String EMAIL = "ana.ficticia@ejemplo.test";
    private static final String DNI = "30111222";

    private final TurnoRepository turnos = mock(TurnoRepository.class);
    private final NotificationService notificaciones = mock(NotificationService.class);
    private final NotificacionesTurnoListener listener = new NotificacionesTurnoListener(turnos, notificaciones);

    private Consultorio consultorio;

    @BeforeEach
    void preparar() {
        consultorio = Consultorio.nuevo("Consultorio Ficticio Sonrisa", "America/Argentina/Buenos_Aires");
    }

    private Turno turno(long id, String email, String prestacionNombre, Instant inicio) {
        Paciente paciente = Paciente.nuevo(consultorio,
                new DatosPaciente("Ana", "Ficticia", DNI, null, "+54 11 5555-0000", email, null));
        Prestacion prestacion = Prestacion.nueva(consultorio, prestacionNombre, 30);
        Turno t = Turno.reservado(consultorio, paciente, prestacion, inicio, OrigenTurno.ODONTOLOGO);
        ReflectionTestUtils.setField(t, "id", id);
        when(turnos.findById(id)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void turnoCreadoReservadoEncolaLaConfirmacionConLosTresDatosDeLaPlantilla() {
        turno(7L, EMAIL, "Limpieza", INICIO);

        listener.alCrear(new TurnoCreado(7L, 1L, EstadoTurno.RESERVADO, OrigenTurno.ODONTOLOGO));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
        verify(notificaciones).encolar(eq(TipoNotificacion.CONFIRMACION_TURNO), eq(EMAIL),
                eq(new ReferenciaToken.Turno(7L)), datos.capture(), eq(List.of()));
        assertThat(datos.getValue()).containsOnly(
                Map.entry("consultorio", "Consultorio Ficticio Sonrisa"),
                Map.entry("inicio", "2026-11-23T13:00:00Z"),
                Map.entry("prestacion", "Limpieza"));
        // La plantilla de C-03 acepta exactamente esos datos y ninguno es el DNI.
        PlantillaNotificacion.de(TipoNotificacion.CONFIRMACION_TURNO).validarDatos(datos.getValue());
        assertThat(datos.getValue().values()).doesNotContain(DNI);
    }

    @Test
    void otroTurnoCreadoUsaSusPropiosDatos() {
        turno(8L, "otra@ejemplo.test", "Endodoncia", Instant.parse("2026-12-01T17:30:00Z"));

        listener.alCrear(new TurnoCreado(8L, 1L, EstadoTurno.RESERVADO, OrigenTurno.LISTA_ESPERA));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
        verify(notificaciones).encolar(eq(TipoNotificacion.CONFIRMACION_TURNO), eq("otra@ejemplo.test"),
                eq(new ReferenciaToken.Turno(8L)), datos.capture(), eq(List.of()));
        assertThat(datos.getValue()).containsEntry("inicio", "2026-12-01T17:30:00Z")
                .containsEntry("prestacion", "Endodoncia");
    }

    @Test
    void turnoCreadoPendienteDeConfirmacionNoEncolaNada() {
        listener.alCrear(new TurnoCreado(9L, 1L, EstadoTurno.PENDIENTE_CONFIRMACION, OrigenTurno.PUBLICA));

        verifyNoInteractions(notificaciones);
        verifyNoInteractions(turnos);
    }

    @Test
    void turnoMovidoEncolaOtraConfirmacionConElInicioNuevo() {
        turno(7L, EMAIL, "Limpieza", Instant.parse("2026-11-23T14:00:00Z"));

        listener.alMover(new TurnoMovido(7L, INICIO, INICIO.plusSeconds(1800),
                Instant.parse("2026-11-23T14:00:00Z"), Instant.parse("2026-11-23T14:30:00Z")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
        verify(notificaciones).encolar(eq(TipoNotificacion.CONFIRMACION_TURNO), eq(EMAIL),
                eq(new ReferenciaToken.Turno(7L)), datos.capture(), eq(List.of()));
        assertThat(datos.getValue()).containsEntry("inicio", "2026-11-23T14:00:00Z");
        PlantillaNotificacion.de(TipoNotificacion.CONFIRMACION_TURNO).validarDatos(datos.getValue());
    }

    @Test
    void turnoCanceladoEncolaLaCancelacion() {
        turno(7L, EMAIL, "Limpieza", INICIO);

        listener.alCancelar(new TurnoCancelado(7L, CanceladoPor.ODONTOLOGO));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
        verify(notificaciones).encolar(eq(TipoNotificacion.CANCELACION), eq(EMAIL),
                eq(new ReferenciaToken.Turno(7L)), datos.capture(), eq(List.of()));
        assertThat(datos.getValue()).containsEntry("inicio", "2026-11-23T13:00:00Z");
        PlantillaNotificacion.de(TipoNotificacion.CANCELACION).validarDatos(datos.getValue());
    }

    @Test
    void sinEmailNoEncolaYElLogNoTrazaDatosPersonales(CapturedOutput salida) {
        Turno t = turno(7L, EMAIL, "Limpieza", INICIO);
        ReflectionTestUtils.setField(t.getPaciente(), "email", "  ");

        listener.alCrear(new TurnoCreado(7L, 1L, EstadoTurno.RESERVADO, OrigenTurno.ODONTOLOGO));
        listener.alCancelar(new TurnoCancelado(7L, CanceladoPor.ODONTOLOGO));

        verify(notificaciones, never()).encolar(any(), any(), any(), anyMap(), anyList());
        String log = salida.getOut() + salida.getErr();
        assertThat(log).contains("turno 7").doesNotContain("Ana").doesNotContain("Ficticia").doesNotContain(DNI)
                .doesNotContain("ejemplo.test");
    }

    @Test
    void siElTurnoYaNoExisteNoEncolaNiLanza() {
        when(turnos.findById(99L)).thenReturn(Optional.empty());

        listener.alCancelar(new TurnoCancelado(99L, CanceladoPor.SISTEMA));

        verifyNoInteractions(notificaciones);
    }
}
