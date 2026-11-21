package ar.edu.odontogestion.domain.error;

/**
 * Token de acceso del paciente inválido (inexistente, mal formado, vencido, usado, revocado o de otro
 * propósito). Mensaje fijo y sin constructor que reciba el valor: no puede filtrar el token ni la causa.
 */
public class TokenInvalidoException extends DomainException {

    public TokenInvalidoException() {
        super("El enlace no es válido o ya fue utilizado.");
    }

    @Override
    public String codigo() {
        return "token-invalido";
    }
}
