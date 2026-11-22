package ar.edu.odontogestion.api.publico;

import ar.edu.odontogestion.application.agenda.DisponibilidadService;
import ar.edu.odontogestion.application.agenda.PrestacionService;
import ar.edu.odontogestion.application.turno.HuecoDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Página pública de turnos (C-09 D1): sin sesión (SecurityConfig); las escrituras exigen CSRF. Las respuestas
 * contienen solo lo que el público necesita: nunca datos de turnos ni de pacientes.
 */
@RestController
@RequestMapping("/api/v1/publico")
public class PublicoController {

    private final PrestacionService prestaciones;
    private final DisponibilidadService disponibilidad;

    public PublicoController(PrestacionService prestaciones, DisponibilidadService disponibilidad) {
        this.prestaciones = prestaciones;
        this.disponibilidad = disponibilidad;
    }

    @GetMapping("/prestaciones")
    public List<PrestacionPublicaResponse> prestaciones() {
        return prestaciones.listarActivas().stream()
                .map(p -> new PrestacionPublicaResponse(p.id(), p.nombre(), p.duracionMin())).toList();
    }

    /** Fechas {@code YYYY-MM-DD} inclusivas, rango de hasta 31 días; con la anticipación mínima pública. */
    @GetMapping("/disponibilidad")
    public List<HuecoDto> disponibilidad(@RequestParam Long prestacionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return disponibilidad.huecosLibresPublicos(prestacionId, desde, hasta).stream().map(HuecoDto::de).toList();
    }
}
