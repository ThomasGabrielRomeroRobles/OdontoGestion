package ar.edu.odontogestion.api.consultorio;

import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;

public record ParametrosResponse(
        int granularidadMin,
        int anticipacionMinReservaMin,
        int limiteCancelacionHoras,
        int recordatorioHorasAntes,
        int vencimientoAvisoMin,
        int vencimientoPendienteMin,
        int toleranciaAusenteMin) {

    static ParametrosResponse from(ParametrosConsultorio p) {
        return new ParametrosResponse(p.granularidadMin(), p.anticipacionMinReservaMin(), p.limiteCancelacionHoras(),
                p.recordatorioHorasAntes(), p.vencimientoAvisoMin(), p.vencimientoPendienteMin(),
                p.toleranciaAusenteMin());
    }
}
