package ar.edu.odontogestion.api.auth;

import ar.edu.odontogestion.application.auth.AutenticacionService;
import ar.edu.odontogestion.application.auth.UsuarioAutenticado;
import ar.edu.odontogestion.application.perfil.PerfilService;
import ar.edu.odontogestion.application.perfil.PerfilVista;
import ar.edu.odontogestion.infrastructure.security.SesionHttp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login y consulta de sesión. El logout lo resuelve el {@code LogoutFilter} de Spring Security (design D2). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AutenticacionService autenticacion;
    private final PerfilService perfil;
    private final SesionHttp sesionHttp;

    public AuthController(AutenticacionService autenticacion, PerfilService perfil, SesionHttp sesionHttp) {
        this.autenticacion = autenticacion;
        this.perfil = perfil;
        this.sesionHttp = sesionHttp;
    }

    @PostMapping("/login")
    public UsuarioSesionResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
            HttpServletResponse respuesta) {
        UsuarioAutenticado usuario = autenticacion.autenticar(request.email(), request.password(),
                http.getRemoteAddr());
        sesionHttp.iniciar(usuario.authentication(), http, respuesta);
        return new UsuarioSesionResponse(usuario.email(), usuario.nombre(), usuario.apellido());
    }

    /** Lee el usuario de la base en cada llamada: refleja ediciones del perfil sin volver a iniciar sesión. */
    @GetMapping("/me")
    public UsuarioSesionResponse me(Authentication authentication) {
        PerfilVista vista = perfil.obtener(authentication.getName());
        return new UsuarioSesionResponse(vista.email(), vista.nombre(), vista.apellido());
    }
}
