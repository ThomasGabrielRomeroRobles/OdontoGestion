package ar.edu.odontogestion.api.notificacion;

import ar.edu.odontogestion.application.notificacion.NotificationService;
import ar.edu.odontogestion.domain.notificacion.EstadoNotificacion;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro de envíos del odontólogo (design D13). Privado por defecto (sesión); el reintento exige CSRF.
 * Filtros desconocidos o paginación fuera de rango responden {@code 400}.
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionController {

    private final NotificationService servicio;

    public NotificacionController(NotificationService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public PaginaResponse<NotificacionResponse> listar(
            @RequestParam(required = false) EstadoNotificacion estado,
            @RequestParam(required = false) TipoNotificacion tipo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PaginaResponse.de(servicio.listar(estado, tipo, page, size), NotificacionResponse::from);
    }

    @PostMapping("/{id}/reintentar")
    public NotificacionResponse reintentar(@PathVariable Long id) {
        return NotificacionResponse.from(servicio.reintentar(id));
    }
}
