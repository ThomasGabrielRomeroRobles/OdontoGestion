package ar.edu.odontogestion.application.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.PropositoToken;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Plantillas por tipo (design D11). Datos ficticios. */
class PlantillaNotificacionTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String BASE = "http://localhost:8080";
    private static final Instant INICIO = Instant.parse("2026-11-24T13:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-24T12:30:00Z");

    private static Map<String, String> datosPara(PlantillaNotificacion plantilla) {
        Map<String, String> datos = new HashMap<>();
        for (String variable : plantilla.variables()) {
            datos.put(variable, switch (variable) {
                case "consultorio" -> "Consultorio Ficticio Sonrisa";
                case "inicio" -> INICIO.toString();
                case "prestacion" -> "Limpieza ficticia";
                default -> throw new IllegalStateException(variable);
            });
        }
        return datos;
    }

    private static List<EnlaceRenderizado> enlacesPara(PlantillaNotificacion plantilla) {
        return plantilla.propositosEnlace().stream()
                .map(p -> new EnlaceRenderizado(p, BASE + "/acciones/token-ficticio-" + p.name(), VENCE))
                .toList();
    }

    @ParameterizedTest
    @EnumSource(TipoNotificacion.class)
    void cadaTipoTienePlantillaConAsuntoYCuerpoConLosDatos(TipoNotificacion tipo) {
        PlantillaNotificacion plantilla = PlantillaNotificacion.de(tipo);

        MensajeRenderizado mensaje = plantilla.renderizar(datosPara(plantilla), enlacesPara(plantilla), BASE, ZONA);

        assertThat(plantilla.tipo()).isEqualTo(tipo);
        assertThat(mensaje.asunto()).isNotBlank();
        assertThat(mensaje.cuerpo()).isNotBlank().contains("Consultorio Ficticio Sonrisa");
        if (plantilla.variables().contains("prestacion")) {
            assertThat(mensaje.cuerpo()).contains("Limpieza ficticia");
        }
        enlacesPara(plantilla).forEach(e -> assertThat(mensaje.cuerpo()).contains(e.url()));
    }

    @Test
    void fechaYHoraSeMuestranEnLaZonaDePresentacion() {
        PlantillaNotificacion plantilla = PlantillaNotificacion.de(TipoNotificacion.CONFIRMACION_TURNO);

        MensajeRenderizado mensaje = plantilla.renderizar(datosPara(plantilla), List.of(), BASE, ZONA);

        assertThat(mensaje.cuerpo()).contains("24/11/2026").contains("10:00").doesNotContain("13:00");
    }

    @Test
    void recordatorioIncluyeEnlacesDeConfirmarYCancelar() {
        PlantillaNotificacion plantilla = PlantillaNotificacion.de(TipoNotificacion.RECORDATORIO);

        assertThat(plantilla.propositosEnlace())
                .isEqualTo(EnumSet.of(PropositoToken.CONFIRMAR_TURNO, PropositoToken.CANCELAR_TURNO));
    }

    @Test
    void confirmarSolicitudMuestraElVencimientoDelEnlace() {
        PlantillaNotificacion plantilla = PlantillaNotificacion.de(TipoNotificacion.CONFIRMAR_SOLICITUD);

        MensajeRenderizado mensaje = plantilla.renderizar(datosPara(plantilla), enlacesPara(plantilla), BASE, ZONA);

        assertThat(mensaje.cuerpo()).contains("09:30");
    }

    @Test
    void cancelacionYAusenteInvitanAReservarDesdeLaPaginaPublica() {
        for (TipoNotificacion tipo : List.of(TipoNotificacion.CANCELACION, TipoNotificacion.AUSENTE_REPROGRAMAR)) {
            PlantillaNotificacion plantilla = PlantillaNotificacion.de(tipo);
            assertThat(plantilla.renderizar(datosPara(plantilla), List.of(), BASE, ZONA).cuerpo())
                    .as(tipo.name()).contains(BASE + "/");
        }
    }

    @Test
    void variableNoDeclaradaEsRechazada() {
        PlantillaNotificacion plantilla = PlantillaNotificacion.de(TipoNotificacion.RECORDATORIO);
        Map<String, String> datos = datosPara(plantilla);
        datos.put("diagnostico", "dato clinico ficticio");

        assertThatThrownBy(() -> plantilla.validarDatos(datos))
                .isInstanceOf(ValidacionDominioException.class);
        assertThatThrownBy(() -> plantilla.renderizar(datos, enlacesPara(plantilla), BASE, ZONA))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void variableFaltanteOInicioMalFormadoEsRechazado() {
        PlantillaNotificacion plantilla = PlantillaNotificacion.de(TipoNotificacion.CONFIRMACION_TURNO);
        Map<String, String> sinPrestacion = datosPara(plantilla);
        sinPrestacion.remove("prestacion");
        Map<String, String> inicioInvalido = datosPara(plantilla);
        inicioInvalido.put("inicio", "mañana a las diez");

        assertThatThrownBy(() -> plantilla.validarDatos(sinPrestacion)).isInstanceOf(ValidacionDominioException.class);
        assertThatThrownBy(() -> plantilla.validarDatos(inicioInvalido))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void propositosDeEnlaceDistintosALosDeLaPlantillaSonRechazados() {
        PlantillaNotificacion recordatorio = PlantillaNotificacion.de(TipoNotificacion.RECORDATORIO);
        PlantillaNotificacion confirmacion = PlantillaNotificacion.de(TipoNotificacion.CONFIRMACION_TURNO);

        assertThatThrownBy(() -> recordatorio.validarEnlaces(Set.of(PropositoToken.CONFIRMAR_TURNO)))
                .isInstanceOf(ValidacionDominioException.class);
        assertThatThrownBy(() -> confirmacion.validarEnlaces(Set.of(PropositoToken.ACEPTAR_AVISO)))
                .isInstanceOf(ValidacionDominioException.class);
        recordatorio.validarEnlaces(EnumSet.of(PropositoToken.CONFIRMAR_TURNO, PropositoToken.CANCELAR_TURNO));
        confirmacion.validarEnlaces(Set.of());
    }
}
