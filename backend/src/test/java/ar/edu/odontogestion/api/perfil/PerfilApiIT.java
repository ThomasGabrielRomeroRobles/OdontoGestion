package ar.edu.odontogestion.api.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletResponse;

/** Usa un usuario propio por test (nunca el seed): el cambio de contraseña no debe afectar a otros ITs. */
@ExtendWith(OutputCaptureExtension.class)
class PerfilApiIT extends AbstractWebIntegrationTest {

    private static final String PASSWORD = "Ficticia-Perfil-1";

    private String email;
    private Cliente cliente;

    @BeforeEach
    void crearUsuarioYSesion() throws Exception {
        email = crearUsuario("perfil", PASSWORD, true);
        cliente = iniciarSesion(email, PASSWORD);
    }

    @AfterEach
    void limpiar() {
        borrarUsuario(email);
    }

    @Test
    void getDevuelveSoloLosCuatroCampos() throws Exception {
        mockMvc.perform(get("/api/v1/perfil").session(cliente.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nombre").value("Prueba"))
                .andExpect(jsonPath("$.apellido").value("Ficticia"))
                .andExpect(jsonPath("$.matricula").value(""))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void putValidoActualizaYPersiste() throws Exception {
        mockMvc.perform(cliente.firmar(put("/api/v1/perfil")).contentType("application/json")
                        .content("{\"nombre\":\"Beatriz\",\"apellido\":\"Gomez\",\"matricula\":\"MP 9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Beatriz"))
                .andExpect(jsonPath("$.matricula").value("MP 9"));

        mockMvc.perform(get("/api/v1/perfil").session(cliente.session()))
                .andExpect(jsonPath("$.apellido").value("Gomez"));
    }

    @Test
    void elEmailEnElPutSeIgnora() throws Exception {
        mockMvc.perform(cliente.firmar(put("/api/v1/perfil")).contentType("application/json")
                        .content("{\"email\":\"otro@ejemplo.test\",\"nombre\":\"B\",\"apellido\":\"G\","
                                + "\"matricula\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void nombreVacioDa400() throws Exception {
        mockMvc.perform(cliente.firmar(put("/api/v1/perfil")).contentType("application/json")
                        .content("{\"nombre\":\"\",\"apellido\":\"G\",\"matricula\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='nombre')]").exists());
    }

    @Test
    void matriculaVaciaEsValida() throws Exception {
        mockMvc.perform(cliente.firmar(put("/api/v1/perfil")).contentType("application/json")
                        .content("{\"nombre\":\"B\",\"apellido\":\"G\",\"matricula\":\"\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void meReflejaLosDatosFrescosTrasEditarElPerfil() throws Exception {
        mockMvc.perform(cliente.firmar(put("/api/v1/perfil")).contentType("application/json")
                        .content("{\"nombre\":\"Beatriz\",\"apellido\":\"Gomez\",\"matricula\":\"MP 9\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").session(cliente.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nombre").value("Beatriz"))
                .andExpect(jsonPath("$.apellido").value("Gomez"));
    }

    @Test
    void elCambioDePasswordRotaElIdDeSesionYConservaLaSesion() throws Exception {
        String idAntes = cliente.session().getId();

        mockMvc.perform(cliente.firmar(post("/api/v1/perfil/password")).contentType("application/json")
                        .content("{\"passwordActual\":\"" + PASSWORD + "\",\"passwordNueva\":\"Ficticia-Nueva-8\"}"))
                .andExpect(status().isNoContent());

        assertThat(cliente.session().getId()).isNotEqualTo(idAntes);
        mockMvc.perform(get("/api/v1/auth/me").session(cliente.session())).andExpect(status().isOk());
    }

    @Test
    void elCambioDePasswordRechazadoNoRotaLaSesion() throws Exception {
        String idAntes = cliente.session().getId();

        mockMvc.perform(cliente.firmar(post("/api/v1/perfil/password")).contentType("application/json")
                        .content("{\"passwordActual\":\"Incorrecta-123\",\"passwordNueva\":\"Ficticia-Nueva-8\"}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(cliente.session().getId()).isEqualTo(idAntes);
    }

    @Test
    void cambioDePasswordValidoDa204LaSesionSigueYLaNuevaFuncionaYLaViejaNo(CapturedOutput output)
            throws Exception {
        mockMvc.perform(cliente.firmar(post("/api/v1/perfil/password")).contentType("application/json")
                        .content("{\"passwordActual\":\"" + PASSWORD + "\",\"passwordNueva\":\"Ficticia-Nueva-9\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").session(cliente.session())).andExpect(status().isOk());
        Cliente conNueva = clienteAnonimo();
        assertThat(login(conNueva, email, "Ficticia-Nueva-9", ipNueva()).getStatus()).isEqualTo(200);
        Cliente conVieja = clienteAnonimo();
        assertThat(login(conVieja, email, PASSWORD, ipNueva()).getStatus()).isEqualTo(401);
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("Ficticia-Nueva-9");
    }

    @Test
    void passwordActualIncorrectaDa422EnPasswordActual() throws Exception {
        mockMvc.perform(cliente.firmar(post("/api/v1/perfil/password")).contentType("application/json")
                        .content("{\"passwordActual\":\"Incorrecta-123\",\"passwordNueva\":\"Ficticia-Nueva-9\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='passwordActual')]").exists());
    }

    @Test
    void passwordNuevaCortaDa422EnPasswordNueva() throws Exception {
        mockMvc.perform(cliente.firmar(post("/api/v1/perfil/password")).contentType("application/json")
                        .content("{\"passwordActual\":\"" + PASSWORD + "\",\"passwordNueva\":\"corta\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='passwordNueva')]").exists());
    }

    @Test
    void sinPasswordNuevaDa400() throws Exception {
        mockMvc.perform(cliente.firmar(post("/api/v1/perfil/password")).contentType("application/json")
                        .content("{\"passwordActual\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='passwordNueva')]").exists());
    }

    @Test
    void sinCsrfDa403() throws Exception {
        mockMvc.perform(post("/api/v1/perfil/password").session(cliente.session()).cookie(cliente.xsrf())
                        .contentType("application/json")
                        .content("{\"passwordActual\":\"" + PASSWORD + "\",\"passwordNueva\":\"Ficticia-Nueva-9\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/perfil").session(cliente.session()).cookie(cliente.xsrf())
                        .contentType("application/json")
                        .content("{\"nombre\":\"B\",\"apellido\":\"G\",\"matricula\":\"\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinSesionDa401() throws Exception {
        mockMvc.perform(get("/api/v1/perfil")).andExpect(status().isUnauthorized());
    }

    @Test
    void noExisteRegistroDeUsuariosNiPorAuthNiPorUsuarios() throws Exception {
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class);

        for (String ruta : new String[] {"/api/v1/auth/register", "/api/v1/usuarios"}) {
            MockHttpServletResponse r = mockMvc.perform(cliente.firmar(post(ruta))
                            .contentType("application/json")
                            .content("{\"email\":\"intruso@ejemplo.test\",\"password\":\"Ficticia-Intruso-1\"}"))
                    .andReturn().getResponse();
            assertThat(r.getStatus()).as(ruta).isIn(404, 405);
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class)).isEqualTo(antes);
    }
}
