package ar.edu.odontogestion.api.paciente;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Filtros de {@code GET /api/v1/pacientes} (design D7), ambos opcionales. Se validan como un {@code @ModelAttribute}
 * para que un valor inválido dé el mismo {@code 400} con {@code errors[{field}]} que el resto de la API.
 */
public record BusquedaPacienteRequest(
        @Pattern(regexp = PacienteRequest.PATRON_DNI, message = "Debe tener 7 u 8 dígitos.") String dni,
        @Size(max = 100, message = "Máximo 100 caracteres.") String q) {
}
