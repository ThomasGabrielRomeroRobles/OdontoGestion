package ar.edu.odontogestion.domain.consultorio;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Parámetros configurables del consultorio (enteros). La positividad se valida en el servicio
 * (422 con campo); la base la respalda con {@code CHECK > 0}.
 */
@Embeddable
public record ParametrosConsultorio(
        @Column(name = "granularidad_min", nullable = false) int granularidadMin,
        @Column(name = "anticipacion_min_reserva_min", nullable = false) int anticipacionMinReservaMin,
        @Column(name = "limite_cancelacion_horas", nullable = false) int limiteCancelacionHoras,
        @Column(name = "recordatorio_horas_antes", nullable = false) int recordatorioHorasAntes,
        @Column(name = "vencimiento_aviso_min", nullable = false) int vencimientoAvisoMin,
        @Column(name = "vencimiento_pendiente_min", nullable = false) int vencimientoPendienteMin,
        @Column(name = "tolerancia_ausente_min", nullable = false) int toleranciaAusenteMin) {

    /** Valores por defecto de V3 (RN-AG-02, RN-AG-11, RN-TU-03, RN-NO-02, RN-LE-06, RN-PU-02, RN-AS-01). */
    public static ParametrosConsultorio porDefecto() {
        return new ParametrosConsultorio(15, 120, 2, 24, 60, 30, 15);
    }
}
