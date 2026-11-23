package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.TurnosAfectadosService;
import ar.edu.odontogestion.application.turno.TurnoDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Turnos afectados por un bloqueo (design D1). Cuelga de {@code /bloqueos/{id}} sin tocar el controller de C-06. */
@RestController
@RequestMapping("/api/v1/bloqueos/{id}")
public class BloqueoTurnosController {

    private final TurnosAfectadosService afectados;

    public BloqueoTurnosController(TurnosAfectadosService afectados) {
        this.afectados = afectados;
    }

    @GetMapping("/turnos-afectados")
    public List<TurnoDto> turnosAfectados(@PathVariable Long id) {
        return afectados.afectadosPor(id);
    }
}
