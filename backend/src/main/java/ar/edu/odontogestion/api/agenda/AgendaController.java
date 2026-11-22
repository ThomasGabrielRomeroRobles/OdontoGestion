package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.AgendaConsultaService;
import ar.edu.odontogestion.application.agenda.DisponibilidadService;
import ar.edu.odontogestion.application.turno.HuecoDto;
import ar.edu.odontogestion.application.turno.TurnoDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de agenda (design D1). Fechas {@code YYYY-MM-DD} inclusivas en la zona del consultorio, rango de hasta
 * 31 días. Parámetros faltantes o mal formados → 400; rango inválido → 422. La disponibilidad es orientativa.
 */
@RestController
@RequestMapping("/api/v1/agenda")
public class AgendaController {

    private final DisponibilidadService disponibilidad;
    private final AgendaConsultaService agenda;

    public AgendaController(DisponibilidadService disponibilidad, AgendaConsultaService agenda) {
        this.disponibilidad = disponibilidad;
        this.agenda = agenda;
    }

    @GetMapping("/disponibilidad")
    public List<HuecoDto> disponibilidad(@RequestParam Long prestacionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return disponibilidad.huecosLibres(prestacionId, desde, hasta).stream().map(HuecoDto::de).toList();
    }

    @GetMapping
    public List<TurnoDto> agenda(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return agenda.agenda(desde, hasta);
    }
}
