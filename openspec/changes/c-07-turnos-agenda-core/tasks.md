# Tasks

> **TDD estricto** (CONTRIBUTING §3.3 / `~/.claude/CLAUDE.md`): en cada tarea primero RED (escribir el test, ejecutarlo y confirmar que falla por la razón esperada), después GREEN (lo mínimo para pasar), TRIANGULATE (≥ 2 casos por comportamiento) y REFACTOR con los tests en verde. Entre corchetes van los escenarios de spec que cubre cada tarea (`capability › escenario`).
>
> **Governance CRITICO**: ninguna tarea arranca hasta que Thomas (P1), Martina (P2) y Jeremías (P3) aprueben `design.md`, en especial las decisiones 🔒 D2 (PA-06), D3 (FKs diferidas) y D10 (fase de listeners).
>
> ✅ **Aprobado** por P1 (Thomas), P2 (Martina) y P3 (Jeremías) el 2026-11-22, con D2 (PA-06: `ATENDIDO`/`AUSENTE` ocupan agenda), D3 y D10 tal como están en `design.md` (informado por Martina).
>
> 🔁 **Ajustes posteriores a la aprobación (2026-11-22, revisa solo Thomas)**: alineación con el código real de C-03, C-04 y C-06 ya mergeados: nombres de restricciones `ck_`/`ex_` (D2), `token_acceso` con `ON DELETE CASCADE` y sin índices nuevos (D3), paciente dado de baja → 422 (D5), reutilizar `RestriccionesDb` (D5), datos exactos de las plantillas de C-03 y re-notificación al mover (D11), `@EnableScheduling` ya provisto por C-03 (D8), V9 sin FK condicional a `evolucion_clinica` (D3). No cambian D2/PA-06 ni D10.
>
> **Reparto y bloqueos** (estado al 2026-11-22: C-03, C-04 y C-06 **ya están en `main`**; nada externo bloquea):
>
> | Grupo | Dueño | Bloqueado por |
> |---|---|---|
> | 1 Contrato y máquina de estados | P1 (revisan P2, P3) | nada: arranca ya |
> | 2 Calculadora de disponibilidad | P1 | nada: arranca ya |
> | 3 Migración V9 y persistencia | P1 | nada externo (C-03, C-04 y C-06 en `main`) |
> | 4 Reserva y `POST /turnos` | P1 | grupos 2 y 3 |
> | 5 Consultas de agenda | P1 | grupo 3 |
> | 6 Expiración de pendientes | P1 | grupo 3 |
> | 7 Transiciones de P2 en dominio | P2 | grupo 1 |
> | 8 Errores 409/422 en la API | P2 | grupo 1 (no requiere base) |
> | 9 Mover, cancelar, atender | P2 | grupos 3, 4 (traductor de `23P01`) y 7 |
> | 10 Eventos, concurrencia y turnos de hoy | P2 | grupo 9 |
> | 11 Notificaciones | P3 | grupo 1 (eventos) y C-03; ITs además grupos 4 y 9 |
> | 12 Integración y cierre | todos | grupos 1–11 |
>
> **Higiene de tests**: ITs sobre `AbstractIntegrationTest` / `AbstractWebIntegrationTest` (contenedor compartido): no asumir base vacía, borrar los turnos creados en `@AfterEach`, sin `@DirtiesContext` ni `@MockBean` en ITs; tiempo con `MutableClock`/`FixedClockTestConfig`. Verificación por defecto: `cd backend && ./gradlew test`.

## 1. [P1 Thomas] Contrato compartido y máquina de estados (no bloqueado — publicar primero)

- [x] 1.1 RED: test parametrizado `domain.turno.EstadoTurnoTest` con la matriz completa 7×7 de `puedePasarA` según RN-TU-02 (válidas → true; resto, incluidas las que salen de estados finales, → false) y `ocupaAgenda()` (false solo para `CANCELADO`/`EXPIRADO`, según D2); confirmar que falla porque no existe `EstadoTurno` [turno-lifecycle › Transición válida, Transición desde un estado final, Transición no prevista]
- [x] 1.2 GREEN: crear `EstadoTurno`, `OrigenTurno`, `CanceladoPor`; verificar que 1.1 pasa
- [x] 1.3 RED: unit test `TurnoTest` (sin Spring, con dobles de `Consultorio`/`Paciente`/`Prestacion` o constructores de prueba): `Turno.reservado(...)` calcula `fin = inicio + duracionMin` y nace `RESERVADO`; `Turno.pendiente(..., pendienteHasta)` nace `PENDIENTE_CONFIRMACION`; `marcarAtendido()` desde `RESERVADO`/`CONFIRMADO` → `ATENDIDO`; desde `CANCELADO` o `PENDIENTE_CONFIRMACION` → `TransicionInvalidaException` (código `transicion-invalida`) sin cambiar estado; `expirar()` solo desde pendiente; confirmar que falla
- [x] 1.4 GREEN: crear `domain.turno.Turno` (mapeo JPA según D4, `@Version`, `@Enumerated(STRING)`, relaciones `LAZY`) y `TransicionInvalidaException extends ReglaNegocioException`; verificar que 1.3 pasa. `Prestacion` (C-06) y `Paciente` (C-04) ya están en `main`
- [x] 1.5 Crear los records de eventos (`TurnoCreado`, `TurnoMovido`, `TurnoCancelado`, `HuecoLiberado`), `HuecoNoDisponibleException extends ConflictoException` (código `hueco-no-disponible`, lista inmutable de alternativas), `PoliticaAnticipacion` (firma) y los DTOs de D1 (`TurnoDto`, `HuecoDto`, `CrearTurnoRequest`, `MoverTurnoRequest`, `CancelarTurnoRequest` con `@Size(max=200)`); verificar con `./gradlew compileJava` y un unit test de `HuecoNoDisponibleException` (alternativas copiadas defensivamente; código estable)
- [x] 1.6 Revisión del contrato (D1, D4, eventos) por P2 registrada en el PR o canal del equipo; verificar que P2 compila los tests del grupo 7 y P3 los del grupo 11 contra esas firmas — *Hecho: el contrato está en design.md D1/D4 y fue aprobado por P1/P2/P3 el 2026-11-22; todo el change se implementa en esta rama por decisión de Martina (2026-11-22).*

## 2. [P1 Thomas] Calculadora de disponibilidad pura (no bloqueado)

- [x] 2.1 RED: `domain.agenda.CalculadoraDisponibilidadTest` (sin Spring): horario 09:00–10:00, granularidad 15, duración 30 → 09:00, 09:15, 09:30; día sin horario → vacío; dos horarios el mismo día (09–13, 14–18) sin huecos que crucen el corte; confirmar que falla [agenda-availability › Huecos de un día sin turnos, Día sin horario de trabajo]
- [x] 2.2 GREEN: implementar `CalculadoraDisponibilidad` y `Hueco` según D7; verificar que 2.1 pasa
- [x] 2.3 RED → GREEN: triangular con ocupados y bloqueos en `[)` (contiguo permitido, solape parcial descartado), `horaDesde` no alineada (primer candidato alineado), duración no múltiplo de la granularidad, ventana de varios días ordenada, y conversión de zona (`America/Argentina/Buenos_Aires`, turno a las 22:00 local = 01:00 UTC del día siguiente); verificar que pasan [agenda-availability › Turnos y bloqueos descuentan huecos; turno-lifecycle › Turnos contiguos permitidos (lógica orientativa)]
- [x] 2.4 RED → GREEN: `PoliticaAnticipacionTest`: `odontologo()` excluye huecos con inicio < ahora; `publica(120)` excluye un hueco a 60 min e incluye uno a 120 min exactos; verificar que pasan [agenda-availability › Política pública, Política del odontólogo, Huecos pasados excluidos]

## 3. [P1 Thomas] Migración V9 y persistencia (no bloqueado: C-03, C-04 y C-06 en `main`)

- [x] 3.1 RED: `FlywayV9MigrationIT`: versión `9` aplicada; tabla `turno` con columnas, `CHECK`s e índices de D2; inserción directa contigua (09:00–09:30 y 09:30–10:00) aceptada; solapada con estado `RESERVADO`/`CONFIRMADO`/`PENDIENTE_CONFIRMACION`/`ATENDIDO`/`AUSENTE` rechazada con SQLState `23P01`; solapada contra `CANCELADO`/`EXPIRADO` aceptada; `fin <= inicio` rechazada; pendiente sin `pendiente_hasta` rechazada; estado fuera del enum rechazado; confirmar que falla [turno-lifecycle › Turnos contiguos permitidos, Turnos atendidos o ausentes siguen ocupando el rango, Turnos cancelados o expirados liberan el hueco, Intervalo inválido rechazado por la base]
- [x] 3.2 GREEN: crear `db/migration/V9__turno.sql` según D2 (comentario de rollback); verificar que 3.1 pasa y que `ddl-auto=validate` arranca con la entidad `Turno` (`ApplicationContextIT`)
- [x] 3.3 RED → GREEN: IT que inserta un turno por cada estado y verifica que la base rechaza el solape exactamente cuando `EstadoTurno.ocupaAgenda()` es true (guarda contra divergencias Java/SQL); verificar que pasa
- [x] 3.4 RED → GREEN (V6 y V7 de C-03 en `main`): extender `FlywayV9MigrationIT` para exigir `fk_notificacion_turno` (`RESTRICT`) y `fk_token_acceso_turno` (`CASCADE`) según D3 (inserción con `turno_id` inexistente rechazada; la regla de borrado se lee de `information_schema.referential_constraints`); agregar los `ALTER TABLE` a V9 sin índices nuevos; verificar que pasa
- [x] 3.5 Dejar en V9 un comentario de que la FK `evolucion_clinica.turno_id → turno` la agrega C-05 en su migración (V10 o posterior, D3); verificar que V9 no referencia `evolucion_clinica`
- [x] 3.6 RED → GREEN: `TurnoRepositoryIT`: `findByIdAndConsultorioId`, turnos que empiezan en `[desde, hasta)` ordenados por inicio con `JOIN FETCH` de paciente y prestación (sin N+1), turnos que ocupan agenda y solapan un rango; verificar que pasan

## 4. [P1 Thomas] Núcleo de reserva y `POST /api/v1/turnos` (bloqueado por grupos 2 y 3)

- [ ] 4.1 RED: unit test (Mockito) de `ValidadorHorarioTurno`: inicio fuera de horario, dentro de bloqueo, no alineado, en el pasado → `ValidacionDominioException` con field `inicio`; odontólogo a 30 min de ahora con anticipación 120 → válido; confirmar que falla [turno-lifecycle › Fuera del horario de trabajo, Dentro de un bloqueo, Inicio no alineado a la granularidad, Inicio en el pasado, Odontólogo sin anticipación mínima]
- [ ] 4.2 GREEN: implementar `ValidadorHorarioTurno` reutilizando `CalculadoraDisponibilidad` (D7); verificar que 4.1 pasa
- [ ] 4.3 RED: IT de `ReservaTurnoService.reservar`: crea `RESERVADO` con origen `ODONTOLOGO` y `fin` correcto; paciente/prestación inexistentes o de otro consultorio → `RecursoNoEncontradoException`; prestación inactiva o paciente dado de baja → 422; solape → `HuecoNoDisponibleException` y ninguna fila nueva; violación de FK no se traduce a 409; confirmar que falla [turno-lifecycle › Creación exitosa, Paciente o prestación inexistente, Prestación inactiva, Paciente dado de baja, Superposición rechazada]
- [ ] 4.4 GREEN: mover `RestriccionesDb` de C-06 a `application.comun` (público; ajustar el `import` de los services de C-06 y correr sus tests) e implementar `ReservaTurnoService` y `TraductorViolacionAgenda` sobre él (`ex_turno_sin_superposicion`, D5); verificar que 4.3 pasa
- [ ] 4.5 RED → GREEN: test de concurrencia (Testcontainers, `ExecutorService` + `CountDownLatch`, sin `@Transactional` en el test): dos `reservar` del mismo hueco y dos de huecos solapados distintos → exactamente 1 éxito y 1 `HuecoNoDisponibleException`, una sola fila; repetir 10 veces sin fallos intermitentes [turno-lifecycle › Dos reservas simultáneas del mismo hueco]
- [ ] 4.6 RED → GREEN: `TurnoComandos` enriquece `HuecoNoDisponibleException` con hasta 3 alternativas fuera de la transacción fallida (D6) y `GlobalExceptionHandler` expone `alternativas`; IT: ocupado 10:00–10:30 → alternativas 10:30, 10:45, 11:00; sin huecos en 7 días → lista vacía [turno-lifecycle › Conflicto con alternativas, Conflicto sin alternativas]
- [ ] 4.7 RED → GREEN: `TurnoApiIT` (`AbstractWebIntegrationTest`, sesión + CSRF): `POST /api/v1/turnos` → `201` + `Location` + `TurnoDto` sin DNI/email; sin sesión → `401`; sin CSRF → `403`; cuerpo inválido → `400`; ocupado → `409` `type …/hueco-no-disponible`; verificar que pasan [turno-lifecycle › Creación exitosa, Sin sesión]

## 5. [P1 Thomas] Consultas de agenda (bloqueado por grupo 3)

- [ ] 5.1 RED: IT de `DisponibilidadService`: descuenta turnos que ocupan agenda y bloqueos; trata `CANCELADO` y pendientes vencidas no expiradas como libres; prestación inactiva → 422, inexistente → 404; confirmar que falla [agenda-availability › Turnos y bloqueos descuentan huecos, Turnos cancelados y pendientes vencidas no ocupan, Prestación inactiva o inexistente]
- [ ] 5.2 GREEN: implementar `DisponibilidadService` (read-only, zona del consultorio, política odontólogo para la API); verificar que 5.1 pasa
- [ ] 5.3 RED → GREEN: `AgendaApiIT`: `GET /api/v1/agenda/disponibilidad` y `GET /api/v1/agenda` → `200` ordenados (agenda con todos los estados y `version`); rango invertido o > 31 días → `422`; parámetro faltante → `400`; sin sesión → `401` [agenda-availability › Huecos de un día sin turnos, Agenda semanal, Agenda sin sesión, Rango invertido, Rango demasiado largo, Parámetro faltante]
- [ ] 5.4 RED → GREEN: `GET /api/v1/bloqueos/{id}/turnos-afectados`: devuelve solo turnos que ocupan agenda y solapan el bloqueo; bloqueo inexistente o de otro consultorio → `404`; crear el bloqueo no modifica turnos (cierra US-012) [agenda-availability › Bloqueo que pisa turnos, Bloqueo inexistente]

## 6. [P1 Thomas] Expiración de pendientes (bloqueado por grupo 3)

- [ ] 6.1 RED: IT con `MutableClock` de `TurnoRepository.expirarPendientesVencidas`: vencida → `EXPIRADO` con `version` incrementada; vigente intacta; segunda ejecución no modifica filas; confirmar que falla [turno-lifecycle › Expiración periódica, Expiración idempotente]
- [ ] 6.2 GREEN: implementar la consulta `@Modifying` de D8; verificar que 6.1 pasa
- [ ] 6.3 RED → GREEN: `reservar` expira antes de insertar: pendiente vencida en el mismo rango → `201` y la pendiente queda `EXPIRADO`; pendiente vigente → `409` [turno-lifecycle › Pendiente vigente retiene el hueco, Pendiente vencida se expira antes de reservar]
- [ ] 6.4 RED → GREEN: `ExpiracionPendientesScheduler` (`app.turnos.expiracion-intervalo`; se apaga con el `app.scheduling.enabled=false` de C-03, sin redeclarar `@EnableScheduling`) invoca la expiración con el `Clock`; test que llama al job directamente; agregar la propiedad a `application.yml` y documentar en `.env.example` solo si se expone como variable de entorno

## 7. [P2 Martina] Transiciones de mover y cancelar en el dominio (bloqueado por grupo 1)

- [ ] 7.1 RED: `TurnoTest` amplía: `cancelar(ODONTOLOGO, motivo)` desde pendiente/reservado/confirmado → `CANCELADO` con `canceladoPor` y motivo (vacío permitido); desde `ATENDIDO` → `TransicionInvalidaException`; confirmar que falla [turno-lifecycle › Cancelación con motivo, Cancelación sin motivo, Cancelar un turno ya finalizado]
- [ ] 7.2 GREEN: implementar `cancelar`; verificar que 7.1 pasa
- [ ] 7.3 RED → GREEN: `mover(nuevoInicio)` desde `RESERVADO`/`CONFIRMADO` conserva duración, estado, paciente y prestación y devuelve el rango anterior; desde los otros cinco estados → `TransicionInvalidaException` [turno-lifecycle › Movimiento exitoso, Mover un turno en estado no movible]

## 8. [P2 Martina] Errores 409/422 de turnos en la API (bloqueado por grupo 1; sin base)

- [ ] 8.1 RED: ampliar `GlobalExceptionHandlerWebMvcTest` (con `ErrorTestController`): `TransicionInvalidaException` → `422` `type …/transicion-invalida`; `ObjectOptimisticLockingFailureException` → `409` `type …/modificacion-concurrente` sin nombres de clases ni SQL en el cuerpo; `HuecoNoDisponibleException` → `409` con `alternativas`; confirmar que falla
- [ ] 8.2 GREEN: agregar los handlers a `GlobalExceptionHandler`; verificar que 8.1 pasa y que los tests existentes del handler siguen en verde

## 9. [P2 Martina] Mover, cancelar y atender (bloqueado por grupos 3, 4 y 7)

- [ ] 9.1 RED: IT de `GestionTurnoService.mover`: a hueco libre → nuevo rango, mismo estado; parcialmente sobre su propio rango → ok; a hueco ocupado → `HuecoNoDisponibleException` y horario original intacto; fuera de horario/bloqueo → 422 (reutiliza `ValidadorHorarioTurno`); expira pendientes antes de mover; turno de otro consultorio → 404; confirmar que falla [turno-lifecycle › Movimiento exitoso, Movimiento parcialmente solapado consigo mismo, Movimiento a un hueco ocupado, Turno inexistente]
- [ ] 9.2 GREEN: implementar `mover` con `TraductorViolacionAgenda` (D9); verificar que 9.1 pasa
- [ ] 9.3 RED → GREEN: `cancelar` y `marcarAtendido` en `GestionTurnoService`; IT: cancelar libera el hueco (nuevo `reservar` en el mismo rango → ok); atender un `CANCELADO` → 422 [turno-lifecycle › Hueco reutilizable tras cancelar, Turno atendido, Transición desde un estado final]
- [ ] 9.4 RED → GREEN: `TurnoApiIT` amplía: `PUT /turnos/{id}/mover` (`200`, `409` con alternativas vía `TurnoComandos`, `422`), `POST /turnos/{id}/cancelar` con y sin cuerpo (`200`, motivo > 200 → `400`), `POST /turnos/{id}/atendido` (`200`, `422`), id inexistente → `404`, sin sesión → `401`, sin CSRF → `403` [turno-lifecycle › Atender sin sesión, Turno inexistente, Movimiento a un hueco ocupado]

## 10. [P2 Martina] Eventos, concurrencia optimista y turnos de hoy (bloqueado por grupo 9)

- [ ] 10.1 RED: IT con `@RecordApplicationEvents`: crear publica `TurnoCreado`; mover publica `TurnoMovido` (rangos anterior y nuevo) y `HuecoLiberado` del rango anterior; cancelar publica `TurnoCancelado` y `HuecoLiberado`; un `409` no deja efectos (listener de prueba `BEFORE_COMMIT` que escribe una marca: no persiste tras rollback); confirmar que falla [turno-lifecycle › Mover libera hueco, Cancelar libera hueco, Sin evento ante conflicto]
- [ ] 10.2 GREEN: publicar los eventos desde `ReservaTurnoService` (coordinar con P1) y `GestionTurnoService`; verificar que 10.1 pasa
- [ ] 10.3 RED → GREEN: test de concurrencia (Testcontainers, dos hilos, sin `@Transactional` en el test): cancelar y mover el mismo turno a la vez → exactamente una operación aplicada y la otra `ObjectOptimisticLockingFailureException` (→ `409` vía API); repetir 10 veces [turno-lifecycle › Cancelar y mover a la vez]
- [ ] 10.4 RED → GREEN: `GET /api/v1/turnos/hoy` con `FixedClockTestConfig`: incluye `ATENDIDO` y `CONFIRMADO` de hoy ordenados, excluye `CANCELADO`/`EXPIRADO`, incluye `paciente.id`; un turno a las 22:00 de Buenos Aires (01:00 UTC del día siguiente) cuenta como de hoy; sin sesión → `401` [agenda-availability › Lista del día, Límite de día en la zona del consultorio]

## 11. [P3 Jeremías] Notificaciones al paciente (bloqueado por grupo 1 y C-03; ITs además por grupos 4 y 9)

- [ ] 11.1 Acordar con P1/P2 la fase `BEFORE_COMMIT` (D10) y definir si el nombre de la prestación se queda en los emails (Open Questions de design.md). La idempotencia de `encolar` y el índice parcial ya los resolvió C-03 (`V6__notificacion.sql`); registrar la decisión en el PR
- [ ] 11.2 RED: unit test (Mockito) de `NotificacionesTurnoListener`: `TurnoCreado` `RESERVADO` → `encolar(CONFIRMACION_TURNO, email, ReferenciaToken.Turno(id), {consultorio, inicio ISO UTC, prestacion}, [])` y esos `datos` pasan `PlantillaNotificacion.validarDatos` (sin DNI); `TurnoCreado` `PENDIENTE_CONFIRMACION` → nada; `TurnoMovido` → `CONFIRMACION_TURNO` con el inicio nuevo; `TurnoCancelado` → `CANCELACION`; email vacío (defensa) → nada y log sin datos personales (`OutputCaptureExtension`); confirmar que falla [turno-lifecycle › Pendiente pública sin confirmación de turno, Mover re-notifica]
- [ ] 11.3 GREEN: implementar el listener (`@TransactionalEventListener(phase = BEFORE_COMMIT)`); verificar que 11.2 pasa
- [ ] 11.4 RED → GREEN: IT con el canal falso en memoria de C-03: crear, mover y cancelar un turno por la API dejan filas `notificacion` `PENDIENTE` con `turno_id` y tipo correctos (dos `CONFIRMACION_TURNO` tras mover); con el canal configurado para fallar, el turno queda `CANCELADO` y la notificación queda para reintento; un `409` no deja notificación [turno-lifecycle › Confirmación encolada al crear, Mover re-notifica, Cancelación encolada, Fallo del canal no revierte]

## 12. [Todos] Integración y cierre

- [ ] 12.1 Actualizar la rama desde `main` (C-03, C-04 y C-06 ya integrados); verificar que Flyway aplica V1…V9 en orden sobre una base vacía y que `cd backend && ./gradlew test` pasa completo
- [ ] 12.2 Verificar la regla dura #10 en todo el change: `grep` de logs y DTOs sin DNI, email, tokens ni datos clínicos; `TurnoDto` sin datos personales más allá de nombre y apellido
- [ ] 12.3 Actualizar la KB con la decisión final de PA-06 (`04_modelo_de_datos.md` §turno, `05_reglas_de_negocio.md` RN-AG-05, `10_preguntas_abiertas.md` PA-06 → resuelta) y el endpoint de mover en `02_descripcion_general.md`; verificar que `CLAUDE.md`/`AGENTS.md` no requieren cambios
- [ ] 12.4 Publicar a P5 (C-08) el contrato final de D1 con ejemplos de respuesta (en el PR o en el canal) y verificar que no hay diferencias con lo implementado (`TurnoApiIT`/`AgendaApiIT`)

## Workflow follow-up

- `/opsx:archive c-07-turnos-agenda-core` cuando los tests estén en verde y el PR revisado por otra persona.
- Marcar `[x]` C-07 en `CHANGES.md` y abrir el PR desde `feat/c-07-turnos-agenda-core`.
