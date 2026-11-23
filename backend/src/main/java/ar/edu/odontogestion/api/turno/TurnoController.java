package ar.edu.odontogestion.api.turno;

import ar.edu.odontogestion.application.agenda.AgendaConsultaService;
import ar.edu.odontogestion.application.turno.TurnoComandos;
import ar.edu.odontogestion.application.turno.TurnoDto;
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

/** Turnos del odontólogo (design D1). Toda la ruta exige sesión; las escrituras, CSRF (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoController {

    private final TurnoComandos comandos;
    private final AgendaConsultaService agenda;

    public TurnoController(TurnoComandos comandos, AgendaConsultaService agenda) {
        this.comandos = comandos;
        this.agenda = agenda;
    }

    /** Turnos de hoy (zona del consultorio) sin cancelados ni expirados. */
    @GetMapping("/hoy")
    public List<TurnoDto> hoy() {
        return agenda.hoy();
    }

    @PostMapping
    public ResponseEntity<TurnoDto> crear(@Valid @RequestBody CrearTurnoRequest request) {
        TurnoDto creado = comandos.crear(request.pacienteId(), request.prestacionId(), request.inicio());
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    /** Cambia el inicio conservando duración y estado; un choque de agenda responde 409 con alternativas. */
    @PutMapping("/{id}/mover")
    public TurnoDto mover(@PathVariable Long id, @Valid @RequestBody MoverTurnoRequest request) {
        return comandos.mover(id, request.inicio());
    }

    /** El cuerpo es opcional: sin él (o sin {@code motivo}) el turno se cancela sin motivo. */
    @PostMapping("/{id}/cancelar")
    public TurnoDto cancelar(@PathVariable Long id,
            @Valid @RequestBody(required = false) CancelarTurnoRequest request) {
        return comandos.cancelar(id, request == null ? null : request.motivo());
    }

    @PostMapping("/{id}/atendido")
    public TurnoDto atendido(@PathVariable Long id) {
        return comandos.marcarAtendido(id);
    }

    /** Marca ausente pasada la tolerancia (C-08, RN-AS-01). {@code 422} antes de la tolerancia o por transición. */
    @PostMapping("/{id}/ausente")
    public TurnoDto ausente(@PathVariable Long id) {
        return comandos.marcarAusente(id);
    }

    /** Revierte la ausencia dentro de las 24 h (C-08, RN-AS-06): devuelve el turno en su estado previo. */
    @PostMapping("/{id}/revertir-ausencia")
    public TurnoDto revertirAusencia(@PathVariable Long id) {
        return comandos.revertirAusencia(id);
    }
}
