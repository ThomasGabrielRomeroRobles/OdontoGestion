package ar.edu.odontogestion.application.agenda;

/** Franja tal como llega de la API: horas como texto {@code HH:mm} para validarlas con 422 por campo (D11). */
public record DatosHorario(Integer diaSemana, String horaDesde, String horaHasta) {
}
