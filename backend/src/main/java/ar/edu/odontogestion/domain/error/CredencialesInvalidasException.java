package ar.edu.odontogestion.domain.error;

/** Login fallido (401). El mensaje es fijo: no distingue email inexistente, contraseña mala ni usuario inactivo. */
public class CredencialesInvalidasException extends DomainException {

    public static final String MENSAJE = "Email o contraseña incorrectos.";

    public CredencialesInvalidasException() {
        super(MENSAJE);
    }

    @Override
    public String codigo() {
        return "credenciales-invalidas";
    }
}
