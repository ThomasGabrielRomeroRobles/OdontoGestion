package ar.edu.odontogestion.api.acciones;

import ar.edu.odontogestion.application.acciones.AccionTokenService;
import ar.edu.odontogestion.application.acciones.ResumenAccionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acciones del paciente por enlace de email (C-09 D6): sin sesión (SecurityConfig), el {@code POST} exige CSRF. El
 * {@code GET} muestra la acción sin consumir el token; el {@code POST} la ejecuta. El valor del token viaja solo en
 * la ruta: no se loguea ni se devuelve en ningún cuerpo de error (C-09 D8).
 */
@RestController
@RequestMapping("/api/v1/acciones")
public class AccionesController {

    private final AccionTokenService acciones;

    public AccionesController(AccionTokenService acciones) {
        this.acciones = acciones;
    }

    @GetMapping("/{token}")
    public ResumenAccionResponse resumen(@PathVariable String token) {
        return acciones.resumen(token);
    }

    @PostMapping("/{token}")
    public ResumenAccionResponse ejecutar(@PathVariable String token,
            @Valid @RequestBody EjecutarAccionRequest request) {
        return acciones.ejecutar(token, request.accion());
    }
}
