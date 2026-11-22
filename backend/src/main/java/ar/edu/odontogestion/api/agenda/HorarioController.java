package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.HorarioService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Horarios semanales de atención (US-012). */
@RestController
@RequestMapping("/api/v1/horarios")
public class HorarioController {

    private final HorarioService servicio;

    public HorarioController(HorarioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<HorarioResponse> listar() {
        return servicio.listar().stream().map(HorarioResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<HorarioResponse> crear(@Valid @RequestBody HorarioRequest request) {
        HorarioResponse creado = HorarioResponse.from(servicio.crear(request.aDatos()));
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    public HorarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody HorarioRequest request) {
        return HorarioResponse.from(servicio.actualizar(id, request.aDatos()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
