package ar.edu.odontogestion.api.perfil;

import ar.edu.odontogestion.application.perfil.PerfilService;
import jakarta.validation.Valid;
import ar.edu.odontogestion.infrastructure.security.SesionHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/perfil")
public class PerfilController {

    private final PerfilService servicio;
    private final SesionHttp sesionHttp;

    public PerfilController(PerfilService servicio, SesionHttp sesionHttp) {
        this.servicio = servicio;
        this.sesionHttp = sesionHttp;
    }

    @GetMapping
    public PerfilResponse obtener(Authentication authentication) {
        return PerfilResponse.from(servicio.obtener(authentication.getName()));
    }

    @PutMapping
    public PerfilResponse actualizar(Authentication authentication, @Valid @RequestBody PerfilRequest request) {
        return PerfilResponse.from(servicio.actualizar(authentication.getName(), request.aDatos()));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(Authentication authentication, @Valid @RequestBody CambioPasswordRequest request,
            HttpServletRequest http) {
        servicio.cambiarPassword(authentication.getName(), request.passwordActual(), request.passwordNueva());
        sesionHttp.rotar(http);
    }
}
