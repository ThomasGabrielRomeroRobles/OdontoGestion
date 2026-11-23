package ar.edu.odontogestion.application.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.listaespera.EntradaDuplicadaException;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.support.AbstractListaEsperaIT;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Inscripción (C-10 D2). "Ahora" = viernes 2026-11-20 09:00 BA. */
class ListaEsperaServiceInscribirIT extends AbstractListaEsperaIT {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 20);
    private static final LocalDate DESDE = LocalDate.of(2026, 11, 23);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 2);

    @Autowired
    private ListaEsperaService servicio;

    private static DatosPaciente paciente(String dni, String telefono, String email) {
        return new DatosPaciente("Ana", "Ficticia", dni, null, telefono, email, null);
    }

    private DatosInscripcion datos(Long prestacionId, String dni) {
        return new DatosInscripcion(prestacionId, DESDE, HASTA, paciente(dni, "1111", dni + "@ejemplo.test"));
    }

    private List<Map<String, Object>> entradasDe(String dni) {
        return jdbc.queryForList("SELECT e.* FROM entrada_lista_espera e JOIN paciente p ON p.id = e.paciente_id "
                + "WHERE p.dni = ? ORDER BY e.id", dni);
    }

    @Test
    void creaLaEntradaPendienteConPlazoDe24HorasYEncolaLaConfirmacion() {
        Long prestacion = crearPrestacion(30);

        ResultadoInscripcion r = servicio.inscribir(datos(prestacion, "38100001"));

        assertThat(r).isEqualTo(ResultadoInscripcion.CREADA);
        Map<String, Object> e = entradasDe("38100001").get(0);
        assertThat(e.get("estado")).isEqualTo("PENDIENTE_CONFIRMACION");
        assertThat(e.get("origen")).isEqualTo("PUBLICA");
        assertThat(jdbc.queryForObject("SELECT pendiente_hasta = ?::timestamptz FROM entrada_lista_espera "
                + "WHERE id = ?", Boolean.class, reloj.instant().plusSeconds(86400).toString(), e.get("id")))
                .isTrue();
        Map<String, Object> n = jdbc.queryForMap("SELECT tipo, estado, enlaces::text AS enlaces FROM notificacion "
                + "WHERE entrada_lista_espera_id = ?", e.get("id"));
        assertThat(n.get("tipo")).isEqualTo("CONFIRMAR_LISTA_ESPERA");
        assertThat(n.get("estado")).isEqualTo("PENDIENTE");
        assertThat((String) n.get("enlaces")).contains("CONFIRMAR_LISTA_ESPERA").contains("2026-11-21T12:00:00Z");
    }

    @Test
    void unPacienteExistenteNoSeModificaYSeAsociaLaEntrada() {
        Long prestacion = crearPrestacion(30);
        servicio.inscribir(datos(prestacion, "38100002"));
        Long otra = crearPrestacion(30);

        servicio.inscribir(new DatosInscripcion(otra, DESDE, HASTA,
                new DatosPaciente("Otra", "Persona", "38100002", null, "2222", "otro@ejemplo.test", null)));

        Map<String, Object> p = jdbc.queryForMap("SELECT nombre, telefono FROM paciente WHERE dni = '38100002'");
        assertThat(p.get("nombre")).isEqualTo("Ana");
        assertThat(p.get("telefono")).isEqualTo("1111");
        assertThat(entradasDe("38100002")).hasSize(2);
    }

    @Test
    void unPacienteDeBajaNoCreaEntradaNiNotificacion() {
        Long prestacion = crearPrestacion(30);
        jdbc.update("INSERT INTO paciente (consultorio_id, nombre, apellido, dni, telefono, email, activo) "
                + "VALUES (?, 'Baja', 'Ficticia', '38100003', '1', 'baja@ejemplo.test', false)", consultorioId());

        ResultadoInscripcion r = servicio.inscribir(datos(prestacion, "38100003"));

        assertThat(r).isEqualTo(ResultadoInscripcion.PACIENTE_DE_BAJA);
        assertThat(entradasDe("38100003")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE entrada_lista_espera_id IS NOT NULL "
                + "AND destinatario = 'baja@ejemplo.test'", Integer.class)).isZero();
    }

    @Test
    void rangoInvalidoOHastaAnteriorAHoyDanErrorEnFechaHasta() {
        Long prestacion = crearPrestacion(30);
        assertThatThrownBy(() -> servicio.inscribir(new DatosInscripcion(prestacion, HASTA, DESDE,
                paciente("38100004", "1", "a@ejemplo.test")))).isInstanceOf(ValidacionDominioException.class)
                .satisfies(e -> assertThat(((ValidacionDominioException) e).errores().get(0).field())
                        .isEqualTo("fechaHasta"));
        assertThatThrownBy(() -> servicio.inscribir(new DatosInscripcion(prestacion, HOY.minusDays(5),
                HOY.minusDays(1), paciente("38100004", "1", "a@ejemplo.test"))))
                .isInstanceOf(ValidacionDominioException.class);
        assertThat(entradasDe("38100004")).isEmpty();
        // hasta == hoy es válido
        servicio.inscribir(new DatosInscripcion(prestacion, HOY, HOY, paciente("38100004", "1", "a@ejemplo.test")));
        assertThat(entradasDe("38100004")).hasSize(1);
    }

    @Test
    void prestacionInexistenteEs404EInactivaEs422() {
        assertThatThrownBy(() -> servicio.inscribir(datos(999_999_999L, "38100005")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        Long inactiva = crearPrestacionInactiva(30);
        assertThatThrownBy(() -> servicio.inscribir(datos(inactiva, "38100005")))
                .isInstanceOf(ValidacionDominioException.class);
        assertThat(entradasDe("38100005")).isEmpty();
    }

    // --- 2.2 duplicados ---

    @Test
    void duplicadoVigenteLoRechazaLaBaseConConflicto() {
        Long prestacion = crearPrestacion(30);
        servicio.inscribir(datos(prestacion, "38100006"));

        assertThatThrownBy(() -> servicio.inscribir(datos(prestacion, "38100006")))
                .isInstanceOf(EntradaDuplicadaException.class);
        assertThat(entradasDe("38100006")).hasSize(1);
    }

    @Test
    void duplicadoDeUnaEntradaActivaTambienConflicto() {
        Long prestacion = crearPrestacion(30);
        servicio.inscribir(datos(prestacion, "38100007"));
        jdbc.update("UPDATE entrada_lista_espera SET estado = 'ACTIVA', pendiente_hasta = NULL WHERE id = ?",
                entradasDe("38100007").get(0).get("id"));

        assertThatThrownBy(() -> servicio.inscribir(datos(prestacion, "38100007")))
                .isInstanceOf(EntradaDuplicadaException.class);
    }

    @Test
    void unaPendienteVencidaPasaAVencidaYPermiteReinscribir() {
        Long prestacion = crearPrestacion(30);
        servicio.inscribir(datos(prestacion, "38100008"));
        reloj.set(reloj.instant().plusSeconds(86400 + 60));

        servicio.inscribir(new DatosInscripcion(prestacion, DESDE.plusDays(3), HASTA.plusDays(3),
                paciente("38100008", "1", "x@ejemplo.test")));

        List<Map<String, Object>> es = entradasDe("38100008");
        assertThat(es).extracting(m -> m.get("estado")).containsExactly("VENCIDA", "PENDIENTE_CONFIRMACION");
    }
}
