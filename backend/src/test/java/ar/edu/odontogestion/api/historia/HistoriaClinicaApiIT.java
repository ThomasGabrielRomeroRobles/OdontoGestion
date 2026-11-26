package ar.edu.odontogestion.api.historia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.support.AbstractHistoriaIT;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * API de historia clínica (C-05) por HTTP, con la cadena de seguridad real. Reloj fijo en 2026-11-20T12:00Z
 * (hoy = 2026-11-20 en el consultorio). Datos clínicos ficticios.
 */
class HistoriaClinicaApiIT extends AbstractHistoriaIT {

    private static final String PROBLEMA = "https://odontogestion.example/problemas/";

    private Cliente c;
    private Long paciente;

    @BeforeEach
    void sesion() throws Exception {
        c = iniciarSesionSeed();
        paciente = crearPaciente();
    }

    private String base() {
        return "/api/v1/pacientes/" + paciente + "/historia";
    }

    private ResultActions putJson(String url, String json) throws Exception {
        return mockMvc.perform(c.firmar(put(url)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(c.firmar(post(url)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long altaEvolucion(String nota) throws Exception {
        String cuerpo = postJson(base() + "/evoluciones", "{\"nota\":\"" + nota + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(cuerpo.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private int historias() {
        return jdbc.queryForObject("SELECT count(*) FROM historia_clinica WHERE paciente_id = ?", Integer.class,
                paciente);
    }

    @Test
    void fichaVaciaSinCrearLaHistoria() throws Exception {
        mockMvc.perform(get(base()).session(c.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paciente.id").value(paciente))
                .andExpect(jsonPath("$.paciente.dni").doesNotExist())
                .andExpect(jsonPath("$.anamnesis.alergias").value(""))
                .andExpect(jsonPath("$.anamnesis.actualizadoPor").value(nullValue()))
                .andExpect(jsonPath("$.piezas", hasSize(32)))
                .andExpect(jsonPath("$.piezas[0].fdi").value(11))
                .andExpect(jsonPath("$.piezas[0].estado").value(nullValue()))
                .andExpect(jsonPath("$.evoluciones", hasSize(0)));
        assertThat(historias()).isZero();
    }

    @Test
    void pacienteInexistenteEs404() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes/999999999/historia").session(c.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMA + "recurso-no-encontrado"));
    }

    @Test
    void sinSesionEs401SinDatosClinicos() throws Exception {
        putJson(base() + "/anamnesis", "{\"alergias\":\"AlergiaFicticiaXYZ\"}").andExpect(status().isOk());

        String cuerpo = mockMvc.perform(get(base())).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("AlergiaFicticiaXYZ");
    }

    @Test
    void escrituraSinCsrfEs403YNoCambiaNada() throws Exception {
        mockMvc.perform(put(base() + "/anamnesis").session(c.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alergias\":\"Penicilina\"}"))
                .andExpect(status().isForbidden());
        assertThat(historias()).isZero();
    }

    @Test
    void guardarYReemplazarAnamnesis() throws Exception {
        putJson(base() + "/anamnesis", "{\"alergias\":\"Penicilina\",\"medicacionActual\":\"Ibuprofeno\","
                + "\"embarazo\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alergias").value("Penicilina"))
                .andExpect(jsonPath("$.actualizadoPor.nombre").isNotEmpty())
                .andExpect(jsonPath("$.actualizadoPor.email").doesNotExist())
                .andExpect(jsonPath("$.actualizadoEn").value("2026-11-20T12:00:00Z"));

        putJson(base() + "/anamnesis", "{\"alergias\":\"Ninguna\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alergias").value("Ninguna"))
                .andExpect(jsonPath("$.medicacionActual").value(""));
    }

    @Test
    void anamnesisDemasiadoLargaEs400() throws Exception {
        putJson(base() + "/anamnesis", "{\"observaciones\":\"" + "x".repeat(2001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("observaciones"));
        assertThat(historias()).isZero();
    }

    @Test
    void registrarYActualizarPieza() throws Exception {
        putJson(base() + "/odontograma/piezas/16", "{\"estado\":\"CARIES\",\"observacion\":\"Oclusal\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fdi").value(16))
                .andExpect(jsonPath("$.estado").value("CARIES"))
                .andExpect(jsonPath("$.observacion").value("Oclusal"))
                .andExpect(jsonPath("$.actualizadoEn").value("2026-11-20T12:00:00Z"));
        putJson(base() + "/odontograma/piezas/16", "{\"estado\":\"OBTURADA\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OBTURADA"));

        mockMvc.perform(get(base()).session(c.session()))
                .andExpect(jsonPath("$.piezas[5].fdi").value(16))
                .andExpect(jsonPath("$.piezas[5].estado").value("OBTURADA"));
    }

    @Test
    void piezaFueraDelConjuntoFdiEs422() throws Exception {
        for (String fdi : new String[] {"19", "51", "10", "49", "20"}) {
            putJson(base() + "/odontograma/piezas/" + fdi, "{\"estado\":\"SANA\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.type").value(PROBLEMA + "pieza-fdi-invalida"));
        }
        assertThat(historias()).isZero();
    }

    @Test
    void piezaConDatosMalFormadosEs400() throws Exception {
        putJson(base() + "/odontograma/piezas/16", "{\"estado\":\"ROTA\"}").andExpect(status().isBadRequest());
        putJson(base() + "/odontograma/piezas/16", "{}").andExpect(status().isBadRequest());
        putJson(base() + "/odontograma/piezas/abc", "{\"estado\":\"SANA\"}").andExpect(status().isBadRequest());
        putJson(base() + "/odontograma/piezas/21", "{\"estado\":\"SANA\",\"observacion\":\"" + "x".repeat(501)
                + "\"}").andExpect(status().isBadRequest());
        assertThat(historias()).isZero();
    }

    @Test
    void altaDeEvolucionConTurnoYLocation() throws Exception {
        Long turno = insertarTurno(paciente, crearPrestacion(30), EstadoTurno.ATENDIDO, dia(-1, "10:00"),
                dia(-1, "10:30"));

        postJson(base() + "/evoluciones", "{\"nota\":\"Obturación pieza 16\",\"turnoId\":" + turno + "}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/historia/evoluciones/"
                        + jdbc.queryForObject("SELECT max(id) FROM evolucion_clinica", Long.class))))
                .andExpect(jsonPath("$.turnoId").value(turno))
                .andExpect(jsonPath("$.fecha").value("2026-11-20"))
                .andExpect(jsonPath("$.creadaEn").value("2026-11-20T12:00:00Z"))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    void erroresDeNegocioDeEvolucionSon422() throws Exception {
        Long otro = crearPaciente();
        Long turnoAjeno = insertarTurno(otro, crearPrestacion(30), EstadoTurno.ATENDIDO, dia(-1, "10:00"),
                dia(-1, "10:30"));

        postJson(base() + "/evoluciones", "{\"nota\":\"Nota\",\"turnoId\":" + turnoAjeno + "}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(PROBLEMA + "turno-de-otro-paciente"));
        postJson(base() + "/evoluciones", "{\"nota\":\"Nota\",\"fecha\":\"2026-11-21\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(PROBLEMA + "fecha-evolucion-futura"));
        assertThat(historias()).isZero();
    }

    @Test
    void notaEnBlancoODemasiadoLargaEs400() throws Exception {
        postJson(base() + "/evoluciones", "{\"nota\":\"   \"}").andExpect(status().isBadRequest());
        postJson(base() + "/evoluciones", "{\"nota\":\"" + "x".repeat(4001) + "\"}")
                .andExpect(status().isBadRequest());
        assertThat(historias()).isZero();
    }

    @Test
    void listadoCronologico() throws Exception {
        postJson(base() + "/evoluciones", "{\"nota\":\"B\",\"fecha\":\"2026-11-20\"}").andExpect(status().isCreated());
        reloj.advance(Duration.ofMinutes(1));
        postJson(base() + "/evoluciones", "{\"nota\":\"A\",\"fecha\":\"2026-10-05\"}").andExpect(status().isCreated());
        reloj.advance(Duration.ofMinutes(1));
        postJson(base() + "/evoluciones", "{\"nota\":\"C\",\"fecha\":\"2026-11-20\"}").andExpect(status().isCreated());

        mockMvc.perform(get(base() + "/evoluciones").session(c.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nota").value("A"))
                .andExpect(jsonPath("$[1].nota").value("B"))
                .andExpect(jsonPath("$[2].nota").value("C"));
    }

    @Test
    void edicionDentroYFueraDeLaVentana() throws Exception {
        Long id = altaEvolucion("Nota");
        reloj.advance(Duration.ofHours(10));

        putJson(base() + "/evoluciones/" + id, "{\"nota\":\"Nota corregida\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").value("Nota corregida"))
                .andExpect(jsonPath("$.creadaEn").value("2026-11-20T12:00:00Z"))
                .andExpect(jsonPath("$.editadaEn").value("2026-11-20T22:00:00Z"));

        reloj.advance(Duration.ofHours(14).plusSeconds(1));
        putJson(base() + "/evoluciones/" + id, "{\"nota\":\"Tarde\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(PROBLEMA + "evolucion-fuera-de-ventana"));
    }

    @Test
    void edicionPorOtroUsuarioEs403() throws Exception {
        String otro = crearUsuario("it-hc", "Ficticia-Otro-123", true);
        Cliente otroCliente = iniciarSesion(otro, "Ficticia-Otro-123");
        String cuerpo = mockMvc.perform(otroCliente.firmar(post(base() + "/evoluciones"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"Nota ajena\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(cuerpo.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get(base() + "/evoluciones").session(c.session()))
                .andExpect(jsonPath("$[0].editable").value(false));
        putJson(base() + "/evoluciones/" + id, "{\"nota\":\"Nueva\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMA + "no-es-autor"));
    }

    @Test
    void evolucionDeOtroPacienteEs404() throws Exception {
        Long otro = crearPaciente();
        String cuerpo = postJson("/api/v1/pacientes/" + otro + "/historia/evoluciones", "{\"nota\":\"Nota\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long ajena = Long.valueOf(cuerpo.replaceAll(".*\"id\":(\\d+).*", "$1"));

        putJson(base() + "/evoluciones/" + ajena, "{\"nota\":\"Nueva\"}").andExpect(status().isNotFound());
    }
}
