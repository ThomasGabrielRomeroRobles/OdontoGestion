package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.PrestacionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Prestaciones (US-011). Sin {@code DELETE}: se desactivan para no perder historial (405 si se intenta). */
@RestController
@RequestMapping("/api/v1/prestaciones")
public class PrestacionController {

    private final PrestacionService servicio;

    public PrestacionController(PrestacionService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<PrestacionResponse> listar() {
        return servicio.listar().stream().map(PrestacionResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<PrestacionResponse> crear(@Valid @RequestBody PrestacionRequest request) {
        PrestacionResponse creada = PrestacionResponse.from(servicio.crear(request.aDatos()));
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PutMapping("/{id}")
    public PrestacionResponse actualizar(@PathVariable Long id, @Valid @RequestBody PrestacionRequest request) {
        return PrestacionResponse.from(servicio.actualizar(id, request.aDatos()));
    }

    @PostMapping("/{id}/desactivar")
    public PrestacionResponse desactivar(@PathVariable Long id) {
        return PrestacionResponse.from(servicio.desactivar(id));
    }

    @PostMapping("/{id}/activar")
    public PrestacionResponse activar(@PathVariable Long id) {
        return PrestacionResponse.from(servicio.activar(id));
    }
}
