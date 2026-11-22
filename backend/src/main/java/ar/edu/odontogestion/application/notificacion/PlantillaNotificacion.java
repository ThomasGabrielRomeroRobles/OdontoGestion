package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.PropositoToken;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Plantilla de texto plano por tipo de notificación (design D11, KB 12). Cada plantilla declara la lista blanca
 * de variables que acepta (barrera contra datos clínicos, RN-NO-05) y los propósitos de enlace que lleva.
 *
 * <p>Variables: {@code consultorio}, {@code prestacion} y {@code inicio} (instante ISO-8601 en UTC, se muestra
 * como fecha y hora en la zona de presentación). Todas las variables declaradas son obligatorias.
 */
public enum PlantillaNotificacion {

    CONFIRMAR_SOLICITUD(
            TipoNotificacion.CONFIRMAR_SOLICITUD,
            "Confirmá tu solicitud de turno en {consultorio}",
            """
            Hola:

            Recibimos tu solicitud de turno en {consultorio}:
              - Prestación: {prestacion}
              - Fecha: {fecha}
              - Hora: {hora}

            Para que el turno quede reservado tenés que confirmarlo antes de las {vence}. \
            Si no lo confirmás, el horario se libera.
            """,
            Set.of("consultorio", "inicio", "prestacion"),
            EnumSet.of(PropositoToken.CONFIRMAR_SOLICITUD)),

    CONFIRMAR_LISTA_ESPERA(
            TipoNotificacion.CONFIRMAR_LISTA_ESPERA,
            "Confirmá tu inscripción en la lista de espera de {consultorio}",
            """
            Hola:

            Recibimos tu pedido para la lista de espera de {consultorio} ({prestacion}).
            Confirmalo para que te avisemos cuando se libere un horario compatible.
            """,
            Set.of("consultorio", "prestacion"),
            EnumSet.of(PropositoToken.CONFIRMAR_LISTA_ESPERA)),

    CONFIRMACION_TURNO(
            TipoNotificacion.CONFIRMACION_TURNO,
            "Tu turno en {consultorio} está confirmado",
            """
            Hola:

            Tu turno en {consultorio} quedó confirmado:
              - Prestación: {prestacion}
              - Fecha: {fecha}
              - Hora: {hora}
            """,
            Set.of("consultorio", "inicio", "prestacion"),
            EnumSet.noneOf(PropositoToken.class)),

    RECORDATORIO(
            TipoNotificacion.RECORDATORIO,
            "Recordatorio: tu turno en {consultorio}",
            """
            Hola:

            Te recordamos tu turno en {consultorio}:
              - Prestación: {prestacion}
              - Fecha: {fecha}
              - Hora: {hora}

            Por favor, confirmá tu asistencia o cancelalo si no podés venir, así otro paciente puede usar el horario.
            """,
            Set.of("consultorio", "inicio", "prestacion"),
            EnumSet.of(PropositoToken.CONFIRMAR_TURNO, PropositoToken.CANCELAR_TURNO)),

    AVISO_HUECO(
            TipoNotificacion.AVISO_HUECO,
            "Se liberó un turno en {consultorio}",
            """
            Hola:

            Se liberó un horario en {consultorio} que coincide con tu pedido de la lista de espera:
              - Prestación: {prestacion}
              - Fecha: {fecha}
              - Hora: {hora}

            El aviso se envió a todas las personas en espera compatibles: el primero que acepta se queda con el turno.
            """,
            Set.of("consultorio", "inicio", "prestacion"),
            EnumSet.of(PropositoToken.ACEPTAR_AVISO)),

    CANCELACION(
            TipoNotificacion.CANCELACION,
            "Tu turno en {consultorio} fue cancelado",
            """
            Hola:

            Tu turno en {consultorio} fue cancelado:
              - Prestación: {prestacion}
              - Fecha: {fecha}
              - Hora: {hora}

            Podés pedir un nuevo turno desde {paginaPublica}
            """,
            Set.of("consultorio", "inicio", "prestacion"),
            EnumSet.noneOf(PropositoToken.class)),

    AUSENTE_REPROGRAMAR(
            TipoNotificacion.AUSENTE_REPROGRAMAR,
            "Te esperamos en {consultorio}: reprogramá tu turno",
            """
            Hola:

            No pudimos atenderte en tu turno en {consultorio}:
              - Prestación: {prestacion}
              - Fecha: {fecha}
              - Hora: {hora}

            Podés pedir un nuevo turno desde {paginaPublica}
            """,
            Set.of("consultorio", "inicio", "prestacion"),
            EnumSet.noneOf(PropositoToken.class));

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final String PIE = "\nEste es un mensaje automático de {consultorio}; no lo respondas.\n";

    private final TipoNotificacion tipo;
    private final String asunto;
    private final String cuerpo;
    private final Set<String> variables;
    private final Set<PropositoToken> propositosEnlace;

    PlantillaNotificacion(TipoNotificacion tipo, String asunto, String cuerpo, Set<String> variables,
            EnumSet<PropositoToken> propositosEnlace) {
        this.tipo = tipo;
        this.asunto = asunto;
        this.cuerpo = cuerpo;
        this.variables = Set.copyOf(variables);
        this.propositosEnlace = Set.copyOf(propositosEnlace);
    }

    public static PlantillaNotificacion de(TipoNotificacion tipo) {
        return valueOf(tipo.name());
    }

    public TipoNotificacion tipo() {
        return tipo;
    }

    public Set<String> variables() {
        return variables;
    }

    public Set<PropositoToken> propositosEnlace() {
        return propositosEnlace;
    }

    /** Rechaza variables no declaradas o faltantes y un {@code inicio} que no sea un instante ISO-8601. */
    public void validarDatos(Map<String, String> datos) {
        List<ErrorCampo> errores = new ArrayList<>();
        for (String clave : new TreeSet<>(datos.keySet())) {
            if (!variables.contains(clave)) {
                errores.add(new ErrorCampo("datos." + clave, "Variable no admitida por la plantilla " + tipo + "."));
            }
        }
        for (String variable : new TreeSet<>(variables)) {
            String valor = datos.get(variable);
            if (valor == null || valor.isBlank()) {
                errores.add(new ErrorCampo("datos." + variable, "Variable obligatoria."));
            }
        }
        if (variables.contains("inicio") && datos.get("inicio") != null && !esInstante(datos.get("inicio"))) {
            errores.add(new ErrorCampo("datos.inicio", "Debe ser un instante ISO-8601 en UTC."));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionDominioException(errores);
        }
    }

    /** Exige exactamente los propósitos de enlace de la plantilla. */
    public void validarEnlaces(Collection<PropositoToken> propositos) {
        Set<PropositoToken> recibidos = propositos.isEmpty()
                ? EnumSet.noneOf(PropositoToken.class) : EnumSet.copyOf(propositos);
        if (!recibidos.equals(propositosEnlace) || recibidos.size() != propositos.size()) {
            throw new ValidacionDominioException("enlaces",
                    "La plantilla " + tipo + " requiere exactamente los enlaces " + propositosEnlace + ".");
        }
    }

    /**
     * Arma asunto y cuerpo. Los enlaces se agregan al final del cuerpo con una etiqueta por propósito.
     *
     * @param urlPaginaPublica URL de la página pública ({@code APP_BASE_URL})
     * @param zona zona horaria de presentación ({@code APP_TIMEZONE})
     */
    public MensajeRenderizado renderizar(Map<String, String> datos, List<EnlaceRenderizado> enlaces,
            String urlPaginaPublica, ZoneId zona) {
        validarDatos(datos);
        validarEnlaces(enlaces.stream().map(EnlaceRenderizado::proposito).toList());

        StringBuilder texto = new StringBuilder(cuerpo);
        if (!enlaces.isEmpty()) {
            texto.append('\n');
            for (EnlaceRenderizado enlace : enlaces) {
                texto.append(etiqueta(enlace.proposito())).append(":\n  ").append(enlace.url()).append('\n');
            }
        }
        texto.append(PIE);

        String resultado = reemplazar(texto.toString(), datos, enlaces, urlPaginaPublica, zona);
        return new MensajeRenderizado(reemplazar(asunto, datos, enlaces, urlPaginaPublica, zona), resultado);
    }

    private static String reemplazar(String plantilla, Map<String, String> datos, List<EnlaceRenderizado> enlaces,
            String urlPaginaPublica, ZoneId zona) {
        String texto = plantilla
                .replace("{consultorio}", datos.getOrDefault("consultorio", ""))
                .replace("{prestacion}", datos.getOrDefault("prestacion", ""))
                .replace("{paginaPublica}", urlPaginaPublica.endsWith("/") ? urlPaginaPublica : urlPaginaPublica + "/");
        if (datos.containsKey("inicio")) {
            var local = Instant.parse(datos.get("inicio")).atZone(zona);
            texto = texto.replace("{fecha}", FECHA.format(local)).replace("{hora}", HORA.format(local));
        }
        if (!enlaces.isEmpty()) {
            texto = texto.replace("{vence}", HORA.format(enlaces.get(0).venceEn().atZone(zona)));
        }
        return texto;
    }

    private static String etiqueta(PropositoToken proposito) {
        return switch (proposito) {
            case CONFIRMAR_SOLICITUD -> "Confirmar la solicitud";
            case CONFIRMAR_TURNO -> "Confirmar asistencia";
            case CANCELAR_TURNO -> "Cancelar el turno";
            case ACEPTAR_AVISO -> "Quiero este turno";
            case CONFIRMAR_LISTA_ESPERA -> "Confirmar la inscripción";
        };
    }

    private static boolean esInstante(String valor) {
        try {
            Instant.parse(valor);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
