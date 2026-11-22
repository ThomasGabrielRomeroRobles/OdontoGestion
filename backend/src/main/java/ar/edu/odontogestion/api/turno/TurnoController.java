package ar.edu.odontogestion.api.turno;

import ar.edu.odontogestion.application.turno.TurnoComandos;
import ar.edu.odontogestion.application.turno.TurnoDto;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Turnos del odontólogo (design D1). Toda la ruta exige sesión; las escrituras, CSRF (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoController {

    private final TurnoComandos comandos;

    public TurnoController(TurnoComandos comandos) {
        this.comandos = comandos;
    }

    @PostMapping
    public ResponseEntity<TurnoDto> crear(@Valid @RequestBody CrearTurnoRequest request) {
        TurnoDto creado = comandos.crear(request.pacienteId(), request.prestacionId(), request.inicio());
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }
}
