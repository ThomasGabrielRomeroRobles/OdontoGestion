package ar.edu.odontogestion.api.notificacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.api.error.GlobalExceptionHandler;
import ar.edu.odontogestion.application.notificacion.NotificacionVista;
import ar.edu.odontogestion.application.notificacion.NotificationService;
import ar.edu.odontogestion.domain.notificacion.CanalNotificacion;
import ar.edu.odontogestion.domain.notificacion.EstadoNotificacion;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.infrastructure.security.ProblemDetailAccessDeniedHandler;
import ar.edu.odontogestion.infrastructure.security.ProblemDetailAuthenticationEntryPoint;
import ar.edu.odontogestion.infrastructure.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP del registro de envíos con la seguridad real (sesión + CSRF) y el servicio simulado. */
@WebMvcTest(NotificacionController.class)
@Import({SecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class, GlobalExceptionHandler.class})
class NotificacionControllerWebMvcTest {

    private static final String URL = "/api/v1/notificaciones";
    private static final Instant CREADA = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService service;

    private static NotificacionVista vista() {
        return new NotificacionVista(7L, TipoNotificacion.RECORDATORIO, CanalNotificacion.EMAIL,
                "paciente@ejemplo.test", EstadoNotificacion.FALLIDA, 3, 0, "SMTP caído",
                new ReferenciaToken.Turno(101), CREADA.plusSeconds(360), null, CREADA);
    }

    @Test
    void consultaSinSesionDevuelve401ProblemDetail() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(service);
    }

    @Test
    void conSesionDevuelveLaPaginaConLosCamposDelContratoSinCuerpoNiTokens() throws Exception {
        when(service.listar(isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(vista()), PageRequest.of(0, 20), 21));

        mockMvc.perform(get(URL).with(user("odontologo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.totalElementos").value(21))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido[0].id").value(7))
                .andExpect(jsonPath("$.contenido[0].tipo").value("RECORDATORIO"))
                .andExpect(jsonPath("$.contenido[0].canal").value("EMAIL"))
                .andExpect(jsonPath("$.contenido[0].destinatario").value("paciente@ejemplo.test"))
                .andExpect(jsonPath("$.contenido[0].estado").value("FALLIDA"))
                .andExpect(jsonPath("$.contenido[0].intentos").value(3))
                .andExpect(jsonPath("$.contenido[0].reintentosManuales").value(0))
                .andExpect(jsonPath("$.contenido[0].error").value("SMTP caído"))
                .andExpect(jsonPath("$.contenido[0].referencia.tipo").value("TURNO"))
                .andExpect(jsonPath("$.contenido[0].referencia.id").value(101))
                .andExpect(jsonPath("$.contenido[0].programadaPara").value("2026-11-20T12:06:00Z"))
                .andExpect(jsonPath("$.contenido[0].enviadaEn").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].creadoEn").value("2026-11-20T12:00:00Z"))
                .andExpect(jsonPath("$.contenido[0].cuerpo").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].datos").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].enlaces").doesNotExist());
    }

    @Test
    void losFiltrosYLaPaginacionLleganAlServicio() throws Exception {
        when(service.listar(any(), any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(URL).param("estado", "FALLIDA").param("tipo", "RECORDATORIO")
                        .param("page", "2").param("size", "50").with(user("odontologo")))
                .andExpect(status().isOk());

        verify(service).listar(EstadoNotificacion.FALLIDA, TipoNotificacion.RECORDATORIO, 2, 50);
    }

    @Test
    void filtroDesconocidoDevuelve400ProblemDetail() throws Exception {
        for (String[] filtro : new String[][] {{"estado", "PERDIDA"}, {"tipo", "OTRO"}}) {
            mockMvc.perform(get(URL).param(filtro[0], filtro[1]).with(user("odontologo")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(service);
    }

    @Test
    void tamanioYPaginaFueraDeRangoDevuelven400() throws Exception {
        for (String[] param : new String[][] {{"size", "500"}, {"size", "0"}, {"page", "-1"}}) {
            mockMvc.perform(get(URL).param(param[0], param[1]).with(user("odontologo")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(service);
    }

    @Test
    void reintentoSinCsrfDevuelve403YNoLlegaAlServicio() throws Exception {
        mockMvc.perform(post(URL + "/7/reintentar").with(user("odontologo")))
                .andExpect(status().isForbidden());
        verify(service, never()).reintentar(any());
    }

    @Test
    void reintentoConSesionYCsrfDevuelveLaNotificacion() throws Exception {
        when(service.reintentar(7L)).thenReturn(vista());

        mockMvc.perform(post(URL + "/7/reintentar").with(user("odontologo")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }
}
