package ar.edu.odontogestion.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Desde V9 (C-07) {@code notificacion.turno_id} y {@code token_acceso.turno_id} tienen FK a {@code turno}. Los ITs
 * de C-03 usan ids de turno ficticios {@code 900000..900999}: esta anotación siembra esos turnos (con su paciente
 * ficticio) antes de cada test y los borra después, junto con las notificaciones y tokens que los referencian.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(TurnosFicticiosExtension.class)
public @interface TurnosFicticios {
}
