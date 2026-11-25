package ar.edu.odontogestion.application.demo;

/**
 * Resultado de una corrida del seed de demo: pacientes y turnos creados, y turnos omitidos porque su franja ya no
 * estaba libre (bloqueo u otro turno). Una corrida sobre un consultorio con pacientes devuelve todo en cero.
 */
public record ResultadoSeedDemo(int pacientes, int turnos, int omitidos) {

    public static final ResultadoSeedDemo NADA = new ResultadoSeedDemo(0, 0, 0);
}
