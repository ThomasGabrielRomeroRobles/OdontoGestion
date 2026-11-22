package ar.edu.odontogestion.api.acciones;

import ar.edu.odontogestion.domain.token.PropositoToken;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /acciones/{token}}: la acción esperada debe coincidir con el propósito del token (D6). */
public record EjecutarAccionRequest(@NotNull(message = "Es obligatoria.") PropositoToken accion) {
}
