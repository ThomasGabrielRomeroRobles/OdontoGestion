package ar.edu.odontogestion.api.consultorio;

import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/consultorio")
public class ConsultorioController {

    private final ConsultorioService servicio;

    public ConsultorioController(ConsultorioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ConsultorioResponse obtener() {
        return ConsultorioResponse.from(servicio.obtener());
    }

    @PutMapping
    public ConsultorioResponse actualizar(@Valid @RequestBody ConsultorioRequest request) {
        return ConsultorioResponse.from(servicio.actualizar(request.aDatos()));
    }
}
