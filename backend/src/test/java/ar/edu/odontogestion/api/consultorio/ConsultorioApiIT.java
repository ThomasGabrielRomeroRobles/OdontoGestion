package ar.edu.odontogestion.api.consultorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;
import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConsultorioApiIT extends AbstractWebIntegrationTest {

    private static final List<String> PARAMETROS = List.of("granularidadMin", "anticipacionMinReservaMin",
            "limiteCancelacionHoras", "recordatorioHorasAntes", "vencimientoAvisoMin", "vencimientoPendienteMin",
            "toleranciaAusenteMin");

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ConsultorioService consultorioService;

    @BeforeEach
    @AfterEach
    void restaurarElSeed() {
        jdbc.update("UPDATE consultorio SET nombre = 'Consultorio Demo', direccion = '', telefono = '', "
                + "email_contacto = '', granularidad_min = 15, anticipacion_min_reserva_min = 120, "
                + "limite_cancelacion_horas = 2, recordatorio_horas_antes = 24, vencimiento_aviso_min = 60, "
                + "vencimiento_pendiente_min = 30, tolerancia_ausente_min = 15");
    }

    private ObjectNode valido() {
        ObjectNode n = json.createObjectNode();
        n.put("nombre", "Consultorio Demo");
        n.put("direccion", "Calle Falsa 123");
        n.put("telefono", "+54 11 5555-0000");
        n.put("emailContacto", "contacto@consultorio.example");
        ObjectNode p = n.putObject("parametros");
        p.put("granularidadMin", 15);
        p.put("anticipacionMinReservaMin", 120);
        p.put("limiteCancelacionHoras", 2);
        p.put("recordatorioHorasAntes", 24);
        p.put("vencimientoAvisoMin", 60);
        p.put("vencimientoPendienteMin", 30);
        p.put("toleranciaAusenteMin", 15);
        return n;
    }

    private static ObjectNode parametros(ObjectNode cuerpo) {
        return (ObjectNode) cuerpo.get("parametros");
    }

    @Test
    void getDevuelveLosDatosYElObjetoParametrosSinId() throws Exception {
        Cliente c = iniciarSesionSeed();

        mockMvc.perform(get("/api/v1/consultorio").session(c.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.nombre").value("Consultorio Demo"))
                .andExpect(jsonPath("$.direccion").value(""))
                .andExpect(jsonPath("$.telefono").value(""))
                .andExpect(jsonPath("$.emailContacto").value(""))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Argentina/Buenos_Aires"))
                .andExpect(jsonPath("$.parametros.length()").value(7))
                .andExpect(jsonPath("$.parametros.granularidadMin").value(15))
                .andExpect(jsonPath("$.parametros.anticipacionMinReservaMin").value(120))
                .andExpect(jsonPath("$.parametros.limiteCancelacionHoras").value(2))
                .andExpect(jsonPath("$.parametros.recordatorioHorasAntes").value(24))
                .andExpect(jsonPath("$.parametros.vencimientoAvisoMin").value(60))
                .andExpect(jsonPath("$.parametros.vencimientoPendienteMin").value(30))
                .andExpect(jsonPath("$.parametros.toleranciaAusenteMin").value(15));
    }

    @Test
    void putValidoActualizaYElGetSiguienteLoReflejaConElRecordatorioEn48() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        parametros(cuerpo).put("recordatorioHorasAntes", 48);

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros.recordatorioHorasAntes").value(48))
                .andExpect(jsonPath("$.direccion").value("Calle Falsa 123"));

        mockMvc.perform(get("/api/v1/consultorio").session(c.session()))
                .andExpect(jsonPath("$.parametros.recordatorioHorasAntes").value(48))
                .andExpect(jsonPath("$.emailContacto").value("contacto@consultorio.example"));
    }

    @Test
    void putConOtrosValoresLosDevuelveYLosPersiste() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        cuerpo.put("nombre", "Consultorio Sonrisa");
        cuerpo.put("emailContacto", "turnos@sonrisa.example");
        parametros(cuerpo).put("granularidadMin", 30);

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Consultorio Sonrisa"))
                .andExpect(jsonPath("$.parametros.granularidadMin").value(30));

        mockMvc.perform(get("/api/v1/consultorio").session(c.session()))
                .andExpect(jsonPath("$.emailContacto").value("turnos@sonrisa.example"))
                .andExpect(jsonPath("$.parametros.granularidadMin").value(30));
    }

    @Test
    void laZonaHorariaEnelPutSeIgnora() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        cuerpo.put("zonaHoraria", "UTC");

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonaHoraria").value("America/Argentina/Buenos_Aires"));
    }

    @Test
    void cadaParametroAusenteDa400ConElCampoAnidado() throws Exception {
        Cliente c = iniciarSesionSeed();
        for (String parametro : PARAMETROS) {
            ObjectNode cuerpo = valido();
            parametros(cuerpo).remove(parametro);

            mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                            .content(cuerpo.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='parametros." + parametro + "')]").exists());
        }
    }

    @Test
    void sinElObjetoParametrosDa400() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        cuerpo.remove("parametros");

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='parametros')]").exists());
    }

    @Test
    void parametrosNoPositivosDan422ConElCampoYNoCambiaNada() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        parametros(cuerpo).put("granularidadMin", 0);
        parametros(cuerpo).put("vencimientoPendienteMin", -5);
        cuerpo.put("nombre", "No debe guardarse");

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='parametros.granularidadMin')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='parametros.vencimientoPendienteMin')]").exists());

        mockMvc.perform(get("/api/v1/consultorio").session(c.session()))
                .andExpect(jsonPath("$.nombre").value("Consultorio Demo"))
                .andExpect(jsonPath("$.parametros.granularidadMin").value(15));
    }

    @Test
    void limiteDeCancelacionNegativoDa422() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        parametros(cuerpo).put("limiteCancelacionHoras", -1);

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='parametros.limiteCancelacionHoras')]").exists());
    }

    @Test
    void todosLosParametrosEnUnoSonValidos() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        PARAMETROS.forEach(p -> parametros(cuerpo).put(p, 1));

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void losParametrosVigentesReflejanElPutSinReiniciar() throws Exception {
        Cliente c = iniciarSesionSeed();
        assertThat(consultorioService.parametrosVigentes().granularidadMin()).isEqualTo(15);
        ObjectNode cuerpo = valido();
        parametros(cuerpo).put("granularidadMin", 30);

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isOk());

        ParametrosConsultorio vigentes = consultorioService.parametrosVigentes();
        assertThat(vigentes).isEqualTo(new ParametrosConsultorio(30, 120, 2, 24, 60, 30, 15));
    }

    @Test
    void modificarElConsultorioNoTocaElPerfilDelOdontologo() throws Exception {
        Cliente c = iniciarSesionSeed();
        String antes = jdbc.queryForObject("SELECT nombre || '|' || apellido || '|' || matricula FROM usuario "
                + "WHERE email = ?::citext", String.class, EMAIL_SEED);

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(valido().toString()))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT nombre || '|' || apellido || '|' || matricula FROM usuario "
                + "WHERE email = ?::citext", String.class, EMAIL_SEED)).isEqualTo(antes);
    }

    @Test
    void nombreVacioDa400ConElCampo() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        cuerpo.put("nombre", "");

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='nombre')]").exists());
    }

    @Test
    void emailDeContactoInvalidoDa400ConElCampo() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        cuerpo.put("emailContacto", "esto-no-es-un-email");

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='emailContacto')]").exists());
    }

    @Test
    void emailDeContactoVacioEsValido() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        cuerpo.put("emailContacto", "");

        mockMvc.perform(c.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(cuerpo.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void putSinCsrfDa403YNoCambiaNada() throws Exception {
        Cliente c = iniciarSesionSeed();
        ObjectNode cuerpo = valido();
        cuerpo.put("nombre", "Sin CSRF");

        mockMvc.perform(put("/api/v1/consultorio").session(c.session()).cookie(c.xsrf())
                        .contentType("application/json").content(cuerpo.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/consultorio").session(c.session()))
                .andExpect(jsonPath("$.nombre").value("Consultorio Demo"));
    }

    @Test
    void sinSesionDa401EnGetYPut() throws Exception {
        Cliente anonimo = clienteAnonimo();

        mockMvc.perform(get("/api/v1/consultorio")).andExpect(status().isUnauthorized());
        mockMvc.perform(anonimo.firmar(put("/api/v1/consultorio")).contentType("application/json")
                        .content(valido().toString()))
                .andExpect(status().isUnauthorized());
    }
}
