package ar.edu.odontogestion.application.notificacion;

/** Asunto y cuerpo de texto plano listos para el canal. El cuerpo puede contener enlaces con token. */
public record MensajeRenderizado(String asunto, String cuerpo) {

    @Override
    public String toString() {
        return "MensajeRenderizado[asunto=" + asunto + ", cuerpo=<oculto>]";
    }
}
