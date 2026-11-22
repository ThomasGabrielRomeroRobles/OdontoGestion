package ar.edu.odontogestion.api.publico;

/** Único cuerpo de éxito de una solicitud pública (D5): idéntico para DNI nuevo, existente, de baja y señuelo. */
public record SolicitudRecibidaResponse(String mensaje) {

    public static final String MENSAJE = "Si los datos son correctos, vas a recibir un email para confirmar el turno.";

    public static SolicitudRecibidaResponse generica() {
        return new SolicitudRecibidaResponse(MENSAJE);
    }
}
