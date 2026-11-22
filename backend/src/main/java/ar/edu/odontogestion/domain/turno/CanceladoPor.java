package ar.edu.odontogestion.domain.turno;

/** Quién canceló el turno; coincide con {@code ck_turno_cancelado_por} (V9). */
public enum CanceladoPor {
    ODONTOLOGO,
    PACIENTE,
    SISTEMA
}
