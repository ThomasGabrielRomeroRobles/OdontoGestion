package ar.edu.odontogestion.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailsRutaPorTokenTest {

    @Test
    void reconoceLasRutasQueLlevanToken() {
        assertThat(ProblemDetails.esRutaPorToken("/api/v1/acciones/abc")).isTrue();
        assertThat(ProblemDetails.esRutaPorToken("/api/v1/avisos/abc")).isTrue();
        assertThat(ProblemDetails.esRutaPorToken("/api/v1/turnos")).isFalse();
        assertThat(ProblemDetails.esRutaPorToken("/api/v1/acciones")).isFalse();
        assertThat(ProblemDetails.esRutaPorToken(null)).isFalse();
    }

    @Test
    void instanceDeEsConstanteSoloParaLasRutasPorToken() {
        assertThat(ProblemDetails.instanceDe("/api/v1/acciones/abc")).isEqualTo("urn:odontogestion:ruta-por-token");
        assertThat(ProblemDetails.instanceDe("/api/v1/avisos/xyz")).isEqualTo("urn:odontogestion:ruta-por-token");
        assertThat(ProblemDetails.instanceDe("/api/v1/turnos/7")).isEqualTo("/api/v1/turnos/7");
    }

    @Test
    void sinTokenReemplazaLaRutaYElSegmentoDelTokenEnElDetalle() {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "No static resource api/v1/acciones/SECRETO/otra.");
        p.setInstance(URI.create("/api/v1/acciones/SECRETO/otra"));

        ProblemDetails.sinToken(p, "/api/v1/acciones/SECRETO/otra");

        assertThat(p.getInstance()).hasToString("urn:odontogestion:ruta-por-token");
        assertThat(p.getDetail()).doesNotContain("SECRETO");
    }

    @Test
    void sinTokenConservaUnaUrnConstanteYNoTocaOtrasRutas() {
        ProblemDetail gone = ProblemDetails.crear(HttpStatus.GONE, "Enlace no disponible", "token-invalido", "x",
                "urn:odontogestion:enlace-no-disponible");
        ProblemDetails.sinToken(gone, "/api/v1/acciones/SECRETO");
        ProblemDetail otra = ProblemDetails.crear(HttpStatus.CONFLICT, "t", "c", "detalle", "/api/v1/turnos");
        ProblemDetails.sinToken(otra, "/api/v1/turnos");

        assertThat(gone.getInstance()).hasToString("urn:odontogestion:enlace-no-disponible");
        assertThat(otra.getInstance()).hasToString("/api/v1/turnos");
    }
}
