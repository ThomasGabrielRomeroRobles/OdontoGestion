# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/patient-absenteeism` y la delta de `specs/turno-lifecycle`.

Estado en `main` (C-07, C-03, C-04 archivados):
- `Turno.marcarAusente()` existe y `EstadoTurno` admite `RESERVADO|CONFIRMADO → AUSENTE`; `AUSENTE` no tiene salidas. `AUSENTE` sigue ocupando su rango en `ex_turno_sin_superposicion` (V9, PA-06).
- `GestionTurnoService` concentra los cambios del odontólogo sobre un turno existente: carga por id y consultorio (404), aplica el método de dominio, `saveAndFlush` y publica eventos; la concurrencia sobre el mismo turno la resuelve `@Version` (`409 modificacion-concurrente`). `TurnoComandos` es la fachada que usa `TurnoController`.
- `NotificacionesTurnoListener` encola avisos en `BEFORE_COMMIT` (misma transacción que el cambio del turno), con las variables `consultorio`, `inicio` y `prestacion`. La plantilla `AUSENTE_REPROGRAMAR` (C-03) ya existe, no lleva enlaces con token y agrega sola el enlace a la página pública (`APP_BASE_URL`).
- `paciente.cantidad_ausencias` (V8) tiene `CHECK >= 0` y "solo la cambian casos de uso propios (C-08)".
- La tolerancia es `Consultorio.getParametros().toleranciaAusenteMin()` (C-02, por defecto 15, PA-05).
- Migraciones aplicadas: V1–V4 y V6–V10. La próxima libre es **V11**.
- `TurnoDto` lo usan la agenda, turnos de hoy, turnos afectados por bloqueo y las acciones.

## Goals / Non-Goals

**Goals:**
- Reversión exacta al estado previo, verificable en la base (no solo en Java).
- Contador consistente bajo marcas simultáneas de turnos distintos del mismo paciente.
- Marca, contador y notificación en una sola transacción: todo o nada.
- Contrato final para el frontend de P5 sin romper a los consumidores actuales de `TurnoDto`.

**Non-Goals:**
- Tabla de historial propia: el historial son los turnos `AUSENTE` del paciente.
- Estadísticas, políticas por reincidencia (PA-08) o avisar al paciente de una reversión.
- Cambios en el frontend.

## Decisions

### D1 — V11: estado previo e instante de la marca en `turno`
`V11__ausencia_turno.sql` agrega a `turno`:
- `ausente_marcado_en timestamptz NULL`
- `estado_previo_ausencia text NULL`
- `CONSTRAINT ck_turno_ausencia CHECK ((estado = 'AUSENTE') = (ausente_marcado_en IS NOT NULL AND estado_previo_ausencia IS NOT NULL))`
- `CONSTRAINT ck_turno_estado_previo_ausencia CHECK (estado_previo_ausencia IN ('RESERVADO', 'CONFIRMADO'))` (un `CHECK` con `NULL` pasa)

El `CHECK` de coherencia hace imposible un `AUSENTE` sin datos de reversión o datos de reversión colgando en otro estado. Ningún turno está `AUSENTE` hoy en `main` (no había caso de uso ni seed que lo creara), así que el `CHECK` se agrega sin migrar datos. Si una base de desarrollo tuviera alguno insertado a mano, la migración falla y se borra a mano (solo datos ficticios).

Alternativas: (a) volver siempre a `RESERVADO` → un `CONFIRMADO` perdería la confirmación (descartada por el equipo); (b) tabla `ausencia` con historial propio → duplica información que ya está en `turno` y obliga a mantener dos fuentes; (c) deducir el estado previo de la auditoría → no existe auditoría de estados.

### D2 — Dominio: la reversión es una salida especial, no una transición general
- `EstadoTurno` no agrega `RESERVADO|CONFIRMADO` a los destinos de `AUSENTE`: `puedePasarA` sigue igual para todas las operaciones (atendido, cancelar, mover, confirmar), así ninguna otra puede sacar un turno de `AUSENTE`.
- `Turno.marcarAusente(Instant ahora, int toleranciaMin)`: valida el estado (`TransicionInvalidaException`, 422) y la tolerancia (`ahora < inicio + tolerancia` → `AusenteAntesDeToleranciaException`, 422, código `ausente-antes-de-tolerancia`), guarda `estadoPrevioAusencia = estado` y `ausenteMarcadoEn = ahora` y transiciona. Valida todo antes de modificar nada.
- `Turno.revertirAusencia(Instant ahora)`: exige `AUSENTE` (`TransicionInvalidaException`) y `ahora < ausenteMarcadoEn + 24 h` (`ReversionFueraDePlazoException`, 422, código `reversion-fuera-de-plazo`); restaura `estado = estadoPrevioAusencia` y limpia las dos columnas. La ventana de 24 h es una constante de dominio (RN-AS-06 es "suposición": si se vuelve configurable, se pasa como parámetro como la tolerancia).
- El método actual `marcarAusente()` sin argumentos se reemplaza (solo lo usaban tests de C-07, que se ajustan).
- Ambas excepciones extienden `ReglaNegocioException`: el `GlobalExceptionHandler` ya las mapea a `422 ProblemDetail` con su `codigo()`. Los mensajes no incluyen datos del paciente.

### D3 — Contador con `UPDATE` atómico
`PacienteRepository` suma `@Modifying` `UPDATE paciente SET cantidad_ausencias = cantidad_ausencias + 1 WHERE id = :id` y el decremento con `WHERE id = :id AND cantidad_ausencias > 0` (devuelve filas afectadas). No se usa `paciente.setCantidadAusencias(...)` sobre la entidad: dos marcas simultáneas de turnos distintos del mismo paciente leerían el mismo valor y una se perdería (`Paciente` no tiene `@Version`). El `UPDATE` relativo lo serializa PostgreSQL por fila. La marca del mismo turno dos veces a la vez ya la frena `@Version` del turno. El `CHECK >= 0` de V8 queda como red de seguridad; si el decremento afecta 0 filas (contador ya en 0 por una corrección manual) se registra un `warn` con el id del paciente y la reversión sigue.

El `UPDATE` se ejecuta después del `saveAndFlush` del turno en la misma transacción, y el contexto de persistencia se limpia del paciente (`clearAutomatically`) para que el `TurnoDto` devuelto lea el contador actualizado.

### D4 — Casos de uso en `GestionTurnoService`, eventos y listener
- `GestionTurnoService.marcarAusente(turnoId)` y `revertirAusencia(turnoId)`, `@Transactional`, con el mismo patrón que `marcarAtendido` (cargar 404 → dominio → `saveAndFlush` → contador → evento). `TurnoComandos` los expone y `TurnoController` agrega `POST /{id}/ausente` y `POST /{id}/revertir-ausencia` (sin cuerpo, `200 TurnoDto`).
- Eventos nuevos en `domain.turno.evento`: `TurnoAusente(long turnoId)` y `AusenciaRevertida(long turnoId)`. No se publica `HuecoLiberado` (el turno sigue ocupando su rango, RN-AS-04).
- `NotificacionesTurnoListener` (`BEFORE_COMMIT`): `alMarcarAusente` encola `AUSENTE_REPROGRAMAR` con las tres variables de siempre y sin enlaces; `alRevertirAusencia` llama a `NotificationService.descartarPendiente(Turno(id), AUSENTE_REPROGRAMAR, "AUSENCIA_REVERTIDA")`.
- `NotificationService.descartarPendiente(...)`: `UPDATE notificacion SET estado = 'FALLIDA', error = :motivo WHERE turno_id = :id AND tipo = :tipo AND estado = 'PENDIENTE'`. No borra la fila (el registro de envíos no se pierde, igual que C-03 D1). Si el procesador la tiene tomada con `FOR UPDATE SKIP LOCKED`, el `UPDATE` espera su commit y, ya `ENVIADA`, no la toca: coincide con el escenario "Aviso ya enviado".

### D5 — `TurnoDto` aditivo
Se agregan `ausenteMarcadoEn` (Instant, `null` si no está ausente) y `PacienteResumen.cantidadAusencias`. Los demás campos no cambian, así que agenda, hoy, afectados y acciones siguen funcionando y los tests de C-07 solo suman aserciones. El DNI del contrato v0 del frontend **no** se agrega (C-07 D1 y regla dura #10): se le informa a P5 para su tarea 9.1. El estado al revertir ya no es una pregunta abierta para el frontend: la API devuelve el estado previo real.

### D6 — Historial de ausencias
`AusenciaPacienteService.historial(pacienteId)` (`readOnly`): verifica que el paciente sea del consultorio (404 con `RecursoNoEncontradoException`, mismo mensaje que la ficha) y consulta `TurnoRepository` con un JPQL `SELECT t FROM Turno t JOIN FETCH t.prestacion WHERE t.paciente.id = :id AND t.consultorio.id = :c AND t.estado = AUSENTE ORDER BY t.inicio DESC`. `cantidad` es `paciente.cantidad_ausencias` (el contador oficial de RN-AS-02), no el largo de la lista: coinciden mientras todo pase por estos casos de uso. Endpoint `GET /api/v1/pacientes/{id}/ausencias` en el `PacienteController` existente (la ruta ya es privada, C-04). Sin paginación: el MVP es un consultorio y un paciente tiene pocas ausencias.

### D7 — Reloj y tests
Todo "ahora" sale del `Clock` inyectado (`MutableClock` en tests). Los ITs extienden `AbstractTurnoIT` (sesión, CSRF, helpers de turnos ficticios, `LUNES`) y cubren la tolerancia en el borde exacto, las 24 h en el borde exacto, la concurrencia del contador con dos hilos y el descarte del aviso pendiente.

## Risks / Trade-offs

- [Marcar, revertir y volver a marcar no reenvía la invitación: el índice único `uq_notificacion_turno_tipo` de C-03 ignora la segunda `AUSENTE_REPROGRAMAR` del mismo turno] → caso raro (una marca equivocada y después una real del mismo turno); se documenta. Cambiar la idempotencia de C-03 queda fuera de alcance.
- [El contador y el historial pueden divergir si alguien edita la base a mano] → `cantidad` sale del contador oficial; solo datos ficticios en el MVP.
- [La invitación ya enviada no se puede "desenviar" al revertir] → aceptado; enviar una disculpa queda fuera de alcance.
- [La ventana de 24 h es fija] → RN-AS-06 es una suposición; si se confirma otro valor, se vuelve parámetro del consultorio.
- [Cambio en `TurnoDto` compartido] → aditivo; los tests de C-07 y C-10 siguen en verde como verificación.

## Migration Plan

1. `V11__ausencia_turno.sql` con rollback comentado como V9/V10: `ALTER TABLE turno DROP CONSTRAINT ck_turno_ausencia, DROP CONSTRAINT ck_turno_estado_previo_ausencia, DROP COLUMN ausente_marcado_en, DROP COLUMN estado_previo_ausencia;` y borrar la fila `11` de `flyway_schema_history`.
2. Sin datos que migrar (no hay turnos `AUSENTE` en `main`).
3. Al mergear, P5 ajusta su front (tarea 9 de `c-08-agenda-frontend-ausentismo`) contra este contrato.
