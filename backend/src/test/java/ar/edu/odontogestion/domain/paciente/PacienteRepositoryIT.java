package ar.edu.odontogestion.domain.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.support.AbstractIntegrationTest;
import ar.edu.odontogestion.support.FixedClockTestConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** Cada test corre en una transacción revertida: no deja pacientes en el contenedor reutilizado. */
@Transactional
@Import(FixedClockTestConfig.class)
class PacienteRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private PacienteRepository pacientes;

    @Autowired
    private ConsultorioRepository consultorios;

    @Autowired
    private ConsultorioActual consultorioActual;

    @Autowired
    private EntityManager em;

    private Consultorio demo() {
        return consultorios.findById(consultorioActual.id()).orElseThrow();
    }

    private Paciente guardar(Consultorio c, String nombre, String apellido, String dni, String email) {
        return pacientes.saveAndFlush(Paciente.nuevo(c,
                new DatosPaciente(nombre, apellido, dni, null, "+54 11 5555-0000", email, null)));
    }

    @Test
    void alGuardarLosTimestampsSonLosDelReloj() {
        Paciente p = guardar(demo(), "Ana", "Pérez", "30500001", "ana@ejemplo.test");
        em.clear();

        Paciente leido = pacientes.findById(p.getId()).orElseThrow();
        assertThat(leido.getCreadoEn()).isEqualTo(FixedClockTestConfig.AHORA);
        assertThat(leido.getActualizadoEn()).isEqualTo(FixedClockTestConfig.AHORA);
    }

    @Test
    void buscaPorConsultorioYDni() {
        Paciente p = guardar(demo(), "Ana", "Pérez", "30500002", "ana@ejemplo.test");
        em.clear();

        assertThat(pacientes.findByConsultorioIdAndDni(consultorioActual.id(), "30500002"))
                .get().extracting(Paciente::getId).isEqualTo(p.getId());
        assertThat(pacientes.findByConsultorioIdAndDni(consultorioActual.id(), "30500099")).isEmpty();
    }

    @Test
    void elMismoDniEnOtroConsultorioNoSeConfunde() {
        Consultorio otro = consultorios.saveAndFlush(Consultorio.nuevo("Otro C-04", "UTC"));
        guardar(otro, "Luis", "Gómez", "30500003", "luis@ejemplo.test");
        em.clear();

        assertThat(pacientes.findByConsultorioIdAndDni(consultorioActual.id(), "30500003")).isEmpty();
        assertThat(pacientes.findByConsultorioIdAndDni(otro.getId(), "30500003")).isPresent();
    }

    @Test
    void buscaPorIdSoloDentroDelConsultorio() {
        Consultorio otro = consultorios.saveAndFlush(Consultorio.nuevo("Otro C-04", "UTC"));
        Paciente ajeno = guardar(otro, "Luis", "Gómez", "30500004", "luis@ejemplo.test");
        Paciente propio = guardar(demo(), "Ana", "Pérez", "30500005", "ana@ejemplo.test");
        em.clear();

        assertThat(pacientes.findByIdAndConsultorioId(propio.getId(), consultorioActual.id())).isPresent();
        assertThat(pacientes.findByIdAndConsultorioId(ajeno.getId(), consultorioActual.id())).isEmpty();
    }

    private java.util.List<String> apellidos(String q) {
        BusquedaPaciente b = BusquedaPaciente.de(q);
        return pacientes.buscarPorTexto(consultorioActual.id(), b.patron(), b.prefijoDni(),
                        org.springframework.data.domain.Limit.of(PacienteRepository.MAXIMO_RESULTADOS)).stream()
                .map(p -> p.getApellido() + " " + p.getNombre()).toList();
    }

    @Test
    void buscaPorApellidoSinDistinguirMayusculas() {
        guardar(demo(), "Ana", "Pérez", "30500010", "ana@ejemplo.test");
        guardar(demo(), "Luis", "Gómez", "30500011", "luis@ejemplo.test");
        em.clear();

        assertThat(apellidos("pér")).contains("Pérez Ana").doesNotContain("Gómez Luis");
        assertThat(apellidos("PÉREZ")).contains("Pérez Ana");
    }

    @Test
    void buscaPorNombreYPorEmailParcial() {
        guardar(demo(), "Zoe", "Ficticia", "30500012", "zoe.contacto@ejemplo.test");
        em.clear();

        assertThat(apellidos("zoe")).contains("Ficticia Zoe");
        assertThat(apellidos("contacto@ejem")).contains("Ficticia Zoe");
    }

    @Test
    void buscaPorPrefijoDeDni() {
        guardar(demo(), "Ana", "Prefijo", "39870013", "ana@ejemplo.test");
        em.clear();

        assertThat(apellidos("3987")).contains("Prefijo Ana");
        assertThat(apellidos("3988")).doesNotContain("Prefijo Ana");
    }

    @Test
    void elPorcientoSeBuscaLiteralmente() {
        guardar(demo(), "Ana", "Pérez", "30500014", "ana@ejemplo.test");
        em.clear();

        assertThat(apellidos("%")).isEmpty();
        assertThat(apellidos("_")).isEmpty();
    }

    @Test
    void devuelveComoMaximoVeinteOrdenadosPorApellidoYNombre() {
        for (int i = 0; i < 25; i++) {
            guardar(demo(), "N" + (char) ('z' - i), "Tope" + (char) ('a' + i % 5), String.valueOf(30600000 + i),
                    "tope" + i + "@ejemplo.test");
        }
        em.clear();

        java.util.List<String> encontrados = apellidos("tope");
        assertThat(encontrados).hasSize(20);
        assertThat(encontrados).isSorted();
        assertThat(encontrados.get(0)).startsWith("Topea ");
    }

    @Test
    void noDevuelvePacientesDeOtroConsultorio() {
        Consultorio otro = consultorios.saveAndFlush(Consultorio.nuevo("Otro C-04", "UTC"));
        guardar(otro, "Ana", "Ajenísima", "30500015", "ajena@ejemplo.test");
        em.clear();

        assertThat(apellidos("ajenísima")).isEmpty();
    }

    @Test
    void sinFiltroListaLosPrimerosDelConsultorioOrdenados() {
        Consultorio otro = consultorios.saveAndFlush(Consultorio.nuevo("Otro C-04", "UTC"));
        guardar(otro, "Ana", "AAA Ajena", "30500016", "ajena@ejemplo.test");
        guardar(demo(), "Ana", "AAA Propia", "30500017", "propia@ejemplo.test");
        em.clear();

        java.util.List<Paciente> todos = pacientes.findByConsultorioIdOrderByApellidoAscNombreAscIdAsc(
                consultorioActual.id(),
                org.springframework.data.domain.Limit.of(PacienteRepository.MAXIMO_RESULTADOS));
        assertThat(todos).extracting(Paciente::getApellido).contains("AAA Propia").doesNotContain("AAA Ajena");
        assertThat(todos).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void elEmailCitextSeEncuentraSinDistinguirMayusculas() {
        guardar(demo(), "Ana", "Pérez", "30500006", "ana@ejemplo.test");
        em.clear();

        Long cantidad = em.createQuery(
                "select count(p) from Paciente p where p.email = :e", Long.class)
                .setParameter("e", "ana@ejemplo.test").getSingleResult();
        assertThat(cantidad).isEqualTo(1L);
    }
}
