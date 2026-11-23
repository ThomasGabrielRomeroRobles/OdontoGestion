package ar.edu.odontogestion.api.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@code POST /api/v1/publico/lista-espera} de punta a punta (C-10 grupo 2). Cada test usa DNI, email e IP propios
 * (el limitador vive en el contexto compartido); los DNI {@code 38…} los borra la limpieza de la base.
 */
class InscripcionListaEsperaApiIT extends AbstractListaEsperaIT {

    private static final String URL = "/api/v1/publico/lista-espera";

    private static RequestPostProcessor desde(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String cuerpo(Long prestacion, String desde, String hasta, String dni, String email,
            String sitioWeb) {
        return """
                {"prestacionId":%d,"fechaDesde":"%s","fechaHasta":"%s","nombre":"Zoilo","apellido":"Quispe",
                 "dni":"%s","email":"%s","telefono":"+54 11 4321-8765","sitioWeb":%s}"""
                .formatted(prestacion, desde, hasta, dni, email, sitioWeb == null ? "null" : "\"" + sitioWeb + "\"");
    }

    private MockHttpServletResponse enviar(Cliente cliente, String ip, String cuerpo) throws Exception {
        return mockMvc.perform(cliente.firmar(post(URL)).with(desde(ip)).contentType("application/json")
                .content(cuerpo)).andReturn().getResponse();
    }

    private MockHttpServletResponse enviar(String ip, String cuerpo) throws Exception {
        return enviar(clienteAnonimo(), ip, cuerpo);
    }

    private int entradasDe(Long prestacion) {
        return jdbc.queryForObject("SELECT count(*) FROM entrada_lista_espera WHERE prestacion_id = ?",
                Integer.class, prestacion);
    }

    private int pacientesConDni(String dni) {
        return jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = ?", Integer.class, dni);
    }

    @Test
    void inscripcionValidaDa202GenericoYCreaLaEntradaPendiente() throws Exception {
        Long prestacion = crearPrestacion(30);

        MockHttpServletResponse r = enviar(ipNueva(),
                cuerpo(prestacion, "2026-11-23", "2026-12-02", "38.992.001", "le1@ejemplo.test", ""));

        assertThat(r.getStatus()).isEqualTo(202);
        assertThat(r.getContentAsString()).contains("mensaje").doesNotContain("\"id\"");
        assertThat(jdbc.queryForObject("SELECT estado FROM entrada_lista_espera WHERE prestacion_id = ?",
                String.class, prestacion)).isEqualTo("PENDIENTE_CONFIRMACION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo = 'CONFIRMAR_LISTA_ESPERA' "
                + "AND destinatario = 'le1@ejemplo.test'", Integer.class)).isEqualTo(1);
    }

    @Test
    void elDuplicadoYElPacienteDeBajaTambienDan202ConElMismoCuerpo() throws Exception {
        Long prestacion = crearPrestacion(30);
        Long deBaja = crearPacienteDeBaja();
        String dniBaja = jdbc.queryForObject("SELECT dni FROM paciente WHERE id = ?", String.class, deBaja);

        MockHttpServletResponse primera = enviar(ipNueva(),
                cuerpo(prestacion, "2026-11-23", "2026-12-02", "38992002", "le2@ejemplo.test", null));
        MockHttpServletResponse duplicada = enviar(ipNueva(),
                cuerpo(prestacion, "2026-11-23", "2026-12-02", "38992002", "le2@ejemplo.test", null));
        MockHttpServletResponse baja = enviar(ipNueva(),
                cuerpo(prestacion, "2026-11-23", "2026-12-02", dniBaja, "le2b@ejemplo.test", null));

        assertThat(List.of(primera, duplicada, baja)).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(202));
        assertThat(duplicada.getContentAsByteArray()).isEqualTo(primera.getContentAsByteArray());
        assertThat(baja.getContentAsByteArray()).isEqualTo(primera.getContentAsByteArray());
        assertThat(entradasDe(prestacion)).isEqualTo(1);
    }

    @Test
    void elSenueloDa202SinCrearNadaPeroCuentaParaLaIp() throws Exception {
        Long prestacion = crearPrestacion(30);

        MockHttpServletResponse r = enviar(ipNueva(),
                cuerpo(prestacion, "2026-11-23", "2026-12-02", "38992003", "le3@ejemplo.test", "http://spam.test"));

        assertThat(r.getStatus()).isEqualTo(202);
        assertThat(entradasDe(prestacion)).isZero();
        assertThat(pacientesConDni("38992003")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE destinatario = "
                + "'le3@ejemplo.test'", Integer.class)).isZero();
    }

    @Test
    void laSextaInscripcionDeLaMismaIpDa429ConRetryAfter() throws Exception {
        Long prestacion = crearPrestacion(30);
        String ip = ipNueva();
        List<MockHttpServletResponse> rs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            rs.add(enviar(ip, cuerpo(prestacion, "2026-11-23", "2026-12-02", "3899210" + i,
                    "leip" + i + "@ejemplo.test", null)));
        }
        assertThat(rs.subList(0, 5)).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(202));
        assertThat(rs.get(5).getStatus()).isEqualTo(429);
        assertThat(rs.get(5).getHeader("Retry-After")).isEqualTo("3600");
        assertThat(entradasDe(prestacion)).isEqualTo(5);
    }

    @Test
    void validacionesDan400YRangoOPrestacionInvalidosNoCreanNada() throws Exception {
        Long prestacion = crearPrestacion(30);
        String ip = ipNueva();

        MockHttpServletResponse dniMalo = enviar(ip,
                cuerpo(prestacion, "2026-11-23", "2026-12-02", "12AB", "le5@ejemplo.test", null));
        MockHttpServletResponse sinFechas = enviar(ip, """
                {"prestacionId":%d,"nombre":"Z","apellido":"Q","dni":"38992005","email":"le5@ejemplo.test",
                 "telefono":"1"}""".formatted(prestacion));
        MockHttpServletResponse invertido = enviar(ip,
                cuerpo(prestacion, "2026-12-02", "2026-11-23", "38992005", "le5@ejemplo.test", null));
        MockHttpServletResponse pasado = enviar(ip,
                cuerpo(prestacion, "2026-11-01", "2026-11-10", "38992005", "le5@ejemplo.test", null));
        MockHttpServletResponse noExiste = enviar(ip,
                cuerpo(999_999_999L, "2026-11-23", "2026-12-02", "38992005", "le5@ejemplo.test", null));

        assertThat(dniMalo.getStatus()).isEqualTo(400);
        assertThat(dniMalo.getContentAsString()).contains("dni");
        assertThat(sinFechas.getStatus()).isEqualTo(400);
        assertThat(invertido.getStatus()).isEqualTo(422);
        assertThat(invertido.getContentAsString()).contains("fechaHasta");
        assertThat(pasado.getStatus()).isEqualTo(422);
        assertThat(noExiste.getStatus()).isEqualTo(404);
        assertThat(entradasDe(prestacion)).isZero();
    }

    @Test
    void exigeCsrfPeroNoSesion() throws Exception {
        Long prestacion = crearPrestacion(30);

        MockHttpServletResponse sinCsrf = mockMvc.perform(post(URL).contentType("application/json")
                .content(cuerpo(prestacion, "2026-11-23", "2026-12-02", "38992006", "le6@ejemplo.test", null)))
                .andReturn().getResponse();
        MockHttpServletResponse sinSesion = enviar(ipNueva(),
                cuerpo(prestacion, "2026-11-23", "2026-12-02", "38992006", "le6@ejemplo.test", null));

        assertThat(sinCsrf.getStatus()).isEqualTo(403);
        assertThat(sinSesion.getStatus()).isEqualTo(202);
    }
}
