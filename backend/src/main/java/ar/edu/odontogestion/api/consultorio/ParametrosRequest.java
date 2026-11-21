package ar.edu.odontogestion.api.consultorio;

import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;
import jakarta.validation.constraints.NotNull;

/** Los siete parámetros son obligatorios (400 si falta alguno); que sean positivos se valida en el servicio (422). */
public record ParametrosRequest(
        @NotNull(message = "Es obligatorio.") Integer granularidadMin,
        @NotNull(message = "Es obligatorio.") Integer anticipacionMinReservaMin,
        @NotNull(message = "Es obligatorio.") Integer limiteCancelacionHoras,
        @NotNull(message = "Es obligatorio.") Integer recordatorioHorasAntes,
        @NotNull(message = "Es obligatorio.") Integer vencimientoAvisoMin,
        @NotNull(message = "Es obligatorio.") Integer vencimientoPendienteMin,
        @NotNull(message = "Es obligatorio.") Integer toleranciaAusenteMin) {

    ParametrosConsultorio aParametros() {
        return new ParametrosConsultorio(granularidadMin, anticipacionMinReservaMin, limiteCancelacionHoras,
                recordatorioHorasAntes, vencimientoAvisoMin, vencimientoPendienteMin, toleranciaAusenteMin);
    }
}
