package ar.edu.odontogestion.api.historia;

import ar.edu.odontogestion.api.historia.HistoriaResponses.Anamnesis;
import ar.edu.odontogestion.api.historia.HistoriaResponses.Evolucion;
import ar.edu.odontogestion.api.historia.HistoriaResponses.Historia;
import ar.edu.odontogestion.api.historia.HistoriaResponses.Pieza;
import ar.edu.odontogestion.application.historia.HistoriaClinicaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historia clínica del paciente (C-05). Privada por defecto (sesión + CSRF en escrituras, RN-HC-02). Sin lógica ni
 * transacciones: delega al servicio. El autor sale de la sesión, nunca del cuerpo.
 */
@RestController
@RequestMapping("/api/v1/pacientes/{pacienteId}/historia")
public class HistoriaClinicaController {

    private final HistoriaClinicaService servicio;

    public HistoriaClinicaController(HistoriaClinicaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public Historia consultar(@PathVariable Long pacienteId, Authentication authentication) {
        return Historia.from(servicio.consultar(pacienteId, authentication.getName()));
    }

    @PutMapping("/anamnesis")
    public Anamnesis guardarAnamnesis(@PathVariable Long pacienteId, @Valid @RequestBody AnamnesisRequest request,
            Authentication authentication) {
        return Anamnesis.from(servicio.guardarAnamnesis(pacienteId, request.aDatos(), authentication.getName()));
    }

    @PutMapping("/odontograma/piezas/{fdi}")
    public Pieza registrarPieza(@PathVariable Long pacienteId, @PathVariable int fdi,
            @Valid @RequestBody PiezaRequest request, Authentication authentication) {
        return Pieza.from(servicio.registrarPieza(pacienteId, fdi, request.estado(), request.observacion(),
                authentication.getName()));
    }

    @GetMapping("/evoluciones")
    public List<Evolucion> listarEvoluciones(@PathVariable Long pacienteId, Authentication authentication) {
        return servicio.listarEvoluciones(pacienteId, authentication.getName()).stream().map(Evolucion::from)
                .toList();
    }

    @PostMapping("/evoluciones")
    public ResponseEntity<Evolucion> registrarEvolucion(@PathVariable Long pacienteId,
            @Valid @RequestBody EvolucionRequest request, Authentication authentication) {
        Evolucion creada = Evolucion.from(servicio.registrarEvolucion(pacienteId, request.aDatos(),
                authentication.getName()));
        URI ubicacion = URI.create("/api/v1/pacientes/" + pacienteId + "/historia/evoluciones/" + creada.id());
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PutMapping("/evoluciones/{evolucionId}")
    public Evolucion editarEvolucion(@PathVariable Long pacienteId, @PathVariable Long evolucionId,
            @Valid @RequestBody EdicionEvolucionRequest request, Authentication authentication) {
        return Evolucion.from(servicio.editarEvolucion(pacienteId, evolucionId, request.aDatos(),
                authentication.getName()));
    }
}
