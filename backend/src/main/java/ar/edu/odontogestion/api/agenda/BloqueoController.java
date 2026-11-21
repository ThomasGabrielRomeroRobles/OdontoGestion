package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.BloqueoService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Bloqueos de agenda (US-012). Filtros de {@code GET} opcionales e independientes (design D12). */
@RestController
@RequestMapping("/api/v1/bloqueos")
public class BloqueoController {

    private final BloqueoService servicio;

    public BloqueoController(BloqueoService servicio) {
        this.servicio = servicio;
    }

    /** {@code desde}/{@code hasta} como texto: el servicio los valida y responde 422 en el campo (D11). */
    @GetMapping
    public List<BloqueoResponse> listar(@RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        return servicio.listar(desde, hasta).stream().map(BloqueoResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<BloqueoResponse> crear(@Valid @RequestBody BloqueoRequest request) {
        BloqueoResponse creado = BloqueoResponse.from(servicio.crear(request.aDatos()));
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    public BloqueoResponse actualizar(@PathVariable Long id, @Valid @RequestBody BloqueoRequest request) {
        return BloqueoResponse.from(servicio.actualizar(id, request.aDatos()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
