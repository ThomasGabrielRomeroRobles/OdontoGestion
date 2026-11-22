package ar.edu.odontogestion.api.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.support.AbstractWebIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * API de pacientes con la cadena de seguridad real. Todos los DNIs del test empiezan con 3044 y se borran en
 * {@code @AfterEach} (el contenedor se reutiliza entre ejecuciones).
 */
class PacienteApiIT extends AbstractWebIntegrationTest {

    private static final String URL = "/api/v1/pacientes";

    @Autowired
    private ObjectMapper json;

    private Cliente cliente;

    @BeforeEach
    void iniciarSesion() throws Exception {
        limpiar();
        cliente = iniciarSesionSeed();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM paciente WHERE dni LIKE '3044%'");
        jdbc.update("DELETE FROM paciente WHERE consultorio_id IN "
                + "(SELECT id FROM consultorio WHERE nombre = 'Otro C-04 API')");
        jdbc.update("DELETE FROM consultorio WHERE nombre = 'Otro C-04 API'");
    }

    // ---- helpers

    protected ObjectNode valido(String dni) {
        ObjectNode n = json.createObjectNode();
        n.put("nombre", "Ana");
        n.put("apellido", "Pérez");
        n.put("dni", dni);
        n.put("telefono", "+54 11 5555-0001");
        n.put("email", " Ana.Perez@Ejemplo.TEST ");
        return n;
    }

    protected MockHttpServletResponse alta(ObjectNode cuerpo) throws Exception {
        return mockMvc.perform(cliente.firmar(post(URL)).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.toString())).andReturn().getResponse();
    }

    protected long altaOk(ObjectNode cuerpo) throws Exception {
        MockHttpServletResponse r = alta(cuerpo);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return json.readTree(r.getContentAsString()).get("id").asLong();
    }

    protected int cantidad(String dni) {
        return jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = ?", Integer.class, dni);
    }

    // ---- alta y consulta

    @Test
    void altaValidaDevuelve201ConLocationYLaFichaNormalizada() throws Exception {
        MockHttpServletResponse r = alta(valido("30.440.001"));

        assertThat(r.getStatus()).isEqualTo(201);
        JsonNode cuerpo = json.readTree(r.getContentAsString());
        long id = cuerpo.get("id").asLong();
        assertThat(r.getHeader("Location")).isEqualTo("/api/v1/pacientes/" + id);
        assertThat(cuerpo.get("dni").asText()).isEqualTo("30440001");
        assertThat(cuerpo.get("email").asText()).isEqualTo("ana.perez@ejemplo.test");
        assertThat(cuerpo.get("cantidadAusencias").asInt()).isZero();
        assertThat(cuerpo.get("activo").asBoolean()).isTrue();
        assertThat(r.getContentAsString()).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("hash")
                .doesNotContainIgnoringCase("token");
    }

    @Test
    void altaQuedaVinculadaAlConsultorioActual() throws Exception {
        long id = altaOk(valido("30440002"));

        Long consultorio = jdbc.queryForObject("SELECT consultorio_id FROM paciente WHERE id = ?", Long.class, id);
        assertThat(consultorio).isEqualTo(jdbc.queryForObject("SELECT min(id) FROM consultorio", Long.class));
    }

    @Test
    void altaSeguidaDeConsultaDevuelveExactamenteLosDiezCampos() throws Exception {
        ObjectNode cuerpo = valido("30440003");
        cuerpo.put("fechaNacimiento", "1990-05-01");
        cuerpo.put("direccion", " Calle Ficticia 1 ");
        long id = altaOk(cuerpo);

        mockMvc.perform(get(URL + "/" + id).session(cliente.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.apellido").value("Pérez"))
                .andExpect(jsonPath("$.dni").value("30440003"))
                .andExpect(jsonPath("$.fechaNacimiento").value("1990-05-01"))
                .andExpect(jsonPath("$.telefono").value("+54 11 5555-0001"))
                .andExpect(jsonPath("$.email").value("ana.perez@ejemplo.test"))
                .andExpect(jsonPath("$.direccion").value("Calle Ficticia 1"))
                .andExpect(jsonPath("$.cantidadAusencias").value(0))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void altaSinOpcionalesDevuelveFechaNulaYDireccionVacia() throws Exception {
        MockHttpServletResponse r = alta(valido("30440004"));

        assertThat(r.getStatus()).isEqualTo(201);
        JsonNode cuerpo = json.readTree(r.getContentAsString());
        assertThat(cuerpo.get("fechaNacimiento").isNull()).isTrue();
        assertThat(cuerpo.get("direccion").asText()).isEmpty();
    }

    // ---- validación y duplicado

    private void esperar400ConCampo(ObjectNode cuerpo, String campo, String dniParaContar) throws Exception {
        MockHttpServletResponse r = alta(cuerpo);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(400);
        assertThat(r.getContentType()).contains("application/problem+json");
        assertThat(json.readTree(r.getContentAsString()).get("errors").findValuesAsText("field")).contains(campo);
        assertThat(cantidad(dniParaContar)).isZero();
    }

    @Test
    void faltaElEmailEs400ConCampo() throws Exception {
        ObjectNode cuerpo = valido("30440010");
        cuerpo.remove("email");
        esperar400ConCampo(cuerpo, "email", "30440010");
    }

    @Test
    void emailInvalidoEs400ConCampo() throws Exception {
        ObjectNode cuerpo = valido("30440011");
        cuerpo.put("email", "no-es-email");
        esperar400ConCampo(cuerpo, "email", "30440011");
    }

    @Test
    void telefonoVacioEs400ConCampo() throws Exception {
        ObjectNode cuerpo = valido("30440012");
        cuerpo.put("telefono", "  ");
        esperar400ConCampo(cuerpo, "telefono", "30440012");
    }

    @Test
    void dniConLetrasOCortoEs400ConCampo() throws Exception {
        esperar400ConCampo(valido("12AB"), "dni", "12AB");
        esperar400ConCampo(valido("304401"), "dni", "304401");
    }

    @Test
    void fechaDeNacimientoFuturaEs400ConCampo() throws Exception {
        ObjectNode cuerpo = valido("30440013");
        cuerpo.put("fechaNacimiento", "2999-01-01");
        esperar400ConCampo(cuerpo, "fechaNacimiento", "30440013");
    }

    @Test
    void altaConDniExistenteEnOtroFormatoEs409SinElDniYSinDuplicar() throws Exception {
        altaOk(valido("30440014"));

        MockHttpServletResponse r = alta(valido("30.440.014"));

        assertThat(r.getStatus()).isEqualTo(409);
        assertThat(r.getContentType()).contains("application/problem+json");
        assertThat(json.readTree(r.getContentAsString()).get("detail").asText()).doesNotContain("30440014")
                .doesNotContain("30.440.014");
        assertThat(cantidad("30440014")).isEqualTo(1);
    }

    // ---- edición

    private MockHttpServletResponse editar(long id, ObjectNode cuerpo) throws Exception {
        return mockMvc.perform(cliente.firmar(put(URL + "/" + id)).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.toString())).andReturn().getResponse();
    }

    private JsonNode consultar(long id) throws Exception {
        return json.readTree(mockMvc.perform(get(URL + "/" + id).session(cliente.session()))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void edicionValidaCambiaElTelefono() throws Exception {
        long id = altaOk(valido("30440020"));
        ObjectNode cuerpo = valido("30440020");
        cuerpo.put("telefono", "+54 11 5555-0002");

        assertThat(editar(id, cuerpo).getStatus()).isEqualTo(200);
        assertThat(consultar(id).get("telefono").asText()).isEqualTo("+54 11 5555-0002");
    }

    @Test
    void cambiarAUnDniDeOtroPacienteEs409YConservaElOriginal() throws Exception {
        altaOk(valido("30440021"));
        long b = altaOk(valido("30440022"));

        assertThat(editar(b, valido("30440021")).getStatus()).isEqualTo(409);
        assertThat(consultar(b).get("dni").asText()).isEqualTo("30440022");
    }

    @Test
    void conservarElPropioDniEnOtroFormatoEs200() throws Exception {
        long id = altaOk(valido("30440023"));

        assertThat(editar(id, valido("30.440.023")).getStatus()).isEqualTo(200);
    }

    @Test
    void losCamposDeSoloLecturaSeIgnoran() throws Exception {
        long id = altaOk(valido("30440024"));
        jdbc.update("UPDATE paciente SET cantidad_ausencias = 2 WHERE id = ?", id);
        ObjectNode cuerpo = valido("30440024");
        cuerpo.put("cantidadAusencias", 0);
        cuerpo.put("activo", false);

        MockHttpServletResponse r = editar(id, cuerpo);

        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode respuesta = json.readTree(r.getContentAsString());
        assertThat(respuesta.get("cantidadAusencias").asInt()).isEqualTo(2);
        assertThat(respuesta.get("activo").asBoolean()).isTrue();
    }

    @Test
    void editarUnIdInexistenteEs404() throws Exception {
        assertThat(editar(999999999L, valido("30440025")).getStatus()).isEqualTo(404);
    }

    @Test
    void edicionConDatosInvalidosEs400() throws Exception {
        long id = altaOk(valido("30440026"));
        ObjectNode cuerpo = valido("30440026");
        cuerpo.put("email", "");

        assertThat(editar(id, cuerpo).getStatus()).isEqualTo(400);
        assertThat(consultar(id).get("email").asText()).isEqualTo("ana.perez@ejemplo.test");
    }

    // ---- búsqueda

    private JsonNode buscar(String query) throws Exception {
        MockHttpServletResponse r = mockMvc.perform(get(URL + query).session(cliente.session()))
                .andReturn().getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private ObjectNode persona(String nombre, String apellido, String dni) {
        ObjectNode n = valido(dni);
        n.put("nombre", nombre);
        n.put("apellido", apellido);
        return n;
    }

    @Test
    void busquedaExactaPorDniConPuntos() throws Exception {
        altaOk(valido("30440030"));
        altaOk(valido("30440031"));

        JsonNode lista = buscar("?dni=30.440.030");

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("dni").asText()).isEqualTo("30440030");
    }

    @Test
    void dniSinCoincidenciasDevuelveListaVacia() throws Exception {
        assertThat(buscar("?dni=30440039")).isEmpty();
    }

    @Test
    void busquedaPorTextoSinDistinguirMayusculas() throws Exception {
        altaOk(persona("Ana", "Pérezapi", "30440032"));
        altaOk(persona("Luis", "Gómezapi", "30440033"));

        JsonNode lista = buscar("?q=PÉREZAPI");

        assertThat(lista.findValuesAsText("apellido")).contains("Pérezapi").doesNotContain("Gómezapi");
    }

    @Test
    void conDniYQueryMandaElDni() throws Exception {
        altaOk(persona("Ana", "Pérezapi", "30440034"));
        altaOk(persona("Luis", "Gómezapi", "30440035"));

        JsonNode lista = buscar("?dni=30440035&q=pérezapi");

        assertThat(lista.findValuesAsText("apellido")).containsExactly("Gómezapi");
    }

    @Test
    void sinParametrosDevuelveComoMaximoVeinte() throws Exception {
        altaOk(valido("30440036"));

        assertThat(buscar("").size()).isBetween(1, 20);
    }

    @Test
    void dniDeBusquedaInvalidoEs400ConCampo() throws Exception {
        MockHttpServletResponse r = mockMvc.perform(get(URL + "?dni=abc").session(cliente.session()))
                .andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(json.readTree(r.getContentAsString()).get("errors").findValuesAsText("field")).contains("dni");
    }

    @Test
    void queryDemasiadoLargaEs400ConCampo() throws Exception {
        MockHttpServletResponse r = mockMvc.perform(get(URL).param("q", "a".repeat(101))
                .session(cliente.session())).andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(json.readTree(r.getContentAsString()).get("errors").findValuesAsText("field")).contains("q");
    }

    // ---- seguridad y logs

    @Test
    void busquedaSinSesionEs401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void altaSinSesionEs401YNoCrea() throws Exception {
        Cliente anonimo = clienteAnonimo();

        mockMvc.perform(anonimo.firmar(post(URL)).contentType(MediaType.APPLICATION_JSON)
                        .content(valido("30440040").toString()))
                .andExpect(status().isUnauthorized());
        assertThat(cantidad("30440040")).isZero();
    }

    @Test
    void altaSinTokenCsrfEs403YNoCrea() throws Exception {
        mockMvc.perform(post(URL).session(cliente.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(valido("30440041").toString()))
                .andExpect(status().isForbidden());
        assertThat(cantidad("30440041")).isZero();
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void elIntentoDeDuplicadoNoDejaDatosPersonalesEnLosLogs(
            org.springframework.boot.test.system.CapturedOutput salida) throws Exception {
        ObjectNode cuerpo = valido("30440042");
        cuerpo.put("email", "duplicado.log@ejemplo.test");
        altaOk(cuerpo);

        assertThat(alta(cuerpo).getStatus()).isEqualTo(409);
        assertThat(salida.getAll()).doesNotContain("30440042").doesNotContain("duplicado.log@ejemplo.test");
    }

    @Test
    void consultaDeIdInexistenteEs404ProblemDetail() throws Exception {
        mockMvc.perform(get(URL + "/999999999").session(cliente.session()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void consultaDePacienteDeOtroConsultorioEs404() throws Exception {
        Long otro = jdbc.queryForObject("INSERT INTO consultorio (nombre) VALUES ('Otro C-04 API') RETURNING id",
                Long.class);
        Long ajeno = jdbc.queryForObject("INSERT INTO paciente (consultorio_id, nombre, apellido, dni, telefono, "
                + "email) VALUES (?, 'Luis', 'Ajeno', '30440005', '1', 'luis@ejemplo.test') RETURNING id",
                Long.class, otro);

        mockMvc.perform(get(URL + "/" + ajeno).session(cliente.session()))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"));
    }
}
