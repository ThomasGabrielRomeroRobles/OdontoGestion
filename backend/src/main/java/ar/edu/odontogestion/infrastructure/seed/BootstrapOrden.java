package ar.edu.odontogestion.infrastructure.seed;

/** Orden de los {@code ApplicationRunner} de seed. C-06 usa {@code @Order(200)} para su catálogo. */
public final class BootstrapOrden {

    public static final int CONSULTORIO_Y_ODONTOLOGO = 100;

    /** Datos ficticios de la demo (C-12): después del consultorio, el odontólogo y el catálogo. */
    public static final int DEMO = 300;

    private BootstrapOrden() {
    }
}
