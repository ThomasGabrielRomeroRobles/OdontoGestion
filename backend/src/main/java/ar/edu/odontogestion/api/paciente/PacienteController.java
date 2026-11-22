package ar.edu.odontogestion.api.paciente;

import ar.edu.odontogestion.application.paciente.PacienteService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pacientes del consultorio (solo con sesión del odontólogo). Sin lógica ni transacciones: delega al servicio. */
@RestController
@RequestMapping("/api/v1/pacientes")
public class PacienteController {

    private final PacienteService servicio;

    public PacienteController(PacienteService servicio) {
        this.servicio = servicio;
    }

    /** Con {@code dni} manda la búsqueda exacta y {@code q} se ignora; como máximo 20 resultados. */
    @GetMapping
    public List<PacienteResponse> buscar(@Valid @ModelAttribute BusquedaPacienteRequest filtro) {
        return servicio.buscar(filtro.dni(), filtro.q()).stream().map(PacienteResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<PacienteResponse> crear(@Valid @RequestBody PacienteRequest request) {
        PacienteResponse creado = PacienteResponse.from(servicio.crear(request.aDatos()));
        return ResponseEntity.created(URI.create("/api/v1/pacientes/" + creado.id())).body(creado);
    }

    @GetMapping("/{id}")
    public PacienteResponse obtener(@PathVariable Long id) {
        return PacienteResponse.from(servicio.obtener(id));
    }

    @PutMapping("/{id}")
    public PacienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody PacienteRequest request) {
        return PacienteResponse.from(servicio.actualizar(id, request.aDatos()));
    }
}
