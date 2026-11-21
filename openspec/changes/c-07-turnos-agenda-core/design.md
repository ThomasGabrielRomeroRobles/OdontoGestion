# Design

## Context

- **En `main` (C-01 + C-02)**: capas `ar.edu.odontogestion.{api,application,domain,infrastructure}`; `GlobalExceptionHandler` + `ProblemDetails.crear(status, titulo, codigoTipo, detalle)`; jerarquía `domain.error` (`DomainException` → `ConflictoException` 409, `ReglaNegocioException` 422, `ValidacionDominioException` 422 con `errors[{field,message}]`, `RecursoNoEncontradoException` 404); `ConsultorioActual.id()`; `Clock` UTC inyectable (`ClockConfig`, `MutableClock` y `FixedClockTestConfig` en tests); `AuditableEntity` (id BIGINT identity, `creado_en`/`actualizado_en`); `ParametrosConsultorio` (`granularidadMin`, `anticipacionMinReservaMin`, `vencimientoPendienteMin`, …) embebido en `Consultorio` junto con `zona_horaria`; `AbstractIntegrationTest` (PostgreSQL 16 Testcontainers singleton reutilizable, sin `@DirtiesContext`/`@MockBean`). Flyway V1–V3; `btree_gist` ya habilitado en V1.
- **No integrado todavía** (ver proposal.md › Impact):
  - C-06 (rama `feat/c-06-agenda-configuracion`, design D2): `V4__agenda_configuracion.sql` con `prestacion(consultorio_id, nombre citext, duracion_min, activa)`, `horario_trabajo(consultorio_id, dia_semana 1..7 ISO, hora_desde time, hora_hasta time)` y `bloqueo_agenda(consultorio_id, desde timestamptz, hasta timestamptz, motivo)` con índice gist por `tstzrange(desde, hasta, '[)')`. Entidades `Prestacion`, `HorarioTrabajo`, `BloqueoAgenda`. C-06 avisa que bajar la granularidad puede dejar prestaciones "no múltiplo": C-07 debe tolerarlo.
  - C-04 (sin rama): `V5` `paciente(consultorio_id, nombre, apellido, dni, email, telefono, …)`, entidad `Paciente` (nombres según KB 04).
  - C-03 (propuesta en curso): `V6` `notificacion` y `V7` `token_acceso`, ambas con `turno_id` nullable **sin FK**; `NotificationService.encolar(...)` sobre outbox.
- Trabajo partido: P1 (Thomas) esquema, dominio, disponibilidad y creación; P2 (Martina) mover/cancelar/atender, concurrencia y eventos; P3 (Jeremías) notificaciones. P2 y P3 trabajan contra el contrato de D1/D4/D10, que P1 publica primero (tasks grupo 1).

## Goals / Non-Goals

**Goals:**
- La no superposición depende solo de la base (regla dura #8); Java valida horario/bloqueos/granularidad para dar `422` claros, pero nunca decide el conflicto.
- Un único núcleo de reserva que C-09 (pendiente pública) y C-10 (aceptación de aviso) reutilizan sin duplicar la traducción de `23P01`.
- Disponibilidad como función pura testeable sin base ni Spring, para que P1 avance antes de que C-04/C-06 lleguen a `main`.
- Contrato estable para P5 (C-08) desde el primer día.

**Non-Goals:**
- Transición a `AUSENTE` vía API (la máquina de estados la admite; el endpoint y el contador son de C-08).
- Límite de cancelación del paciente (RN-TU-03): lo aplica C-09 al cancelar por token.
- Consumir `HuecoLiberado` (lista de espera, C-10) ni recordatorios (C-11).
- Concurrencia optimista visible al cliente (`If-Match`/versión en el request): `version` se expone en las respuestas para un uso posterior, pero el MVP solo detecta carreras entre transacciones.
- Multi-odontólogo (`usuario_id` en `turno`).

## Decisions

### D1. Contrato de la API (DTOs camelCase, instantes ISO-8601 UTC con `Z`, fechas `YYYY-MM-DD` en la zona del consultorio)

| Endpoint | Dueño | Request | Response |
|---|---|---|---|
| `GET /api/v1/agenda/disponibilidad?prestacionId&desde&hasta` | P1 | fechas inclusivas, rango ≤ 31 días | `200 HuecoDto[]` por `inicio` |
| `GET /api/v1/agenda?desde&hasta` | P1 | fechas inclusivas, ≤ 31 días | `200 TurnoDto[]` por `inicio`, todos los estados |
| `POST /api/v1/turnos` | P1 | `{pacienteId, prestacionId, inicio}` | `201 TurnoDto` + `Location` |
| `GET /api/v1/bloqueos/{id}/turnos-afectados` | P1 | — | `200 TurnoDto[]` |
| `PUT /api/v1/turnos/{id}/mover` | P2 | `{inicio}` | `200 TurnoDto` |
| `POST /api/v1/turnos/{id}/cancelar` | P2 | `{motivo?}` (≤ 200; cuerpo opcional) | `200 TurnoDto` |
| `POST /api/v1/turnos/{id}/atendido` | P2 | — | `200 TurnoDto` |
| `GET /api/v1/turnos/hoy` | P2 | — | `200 TurnoDto[]` sin `CANCELADO`/`EXPIRADO` |

- `HuecoDto = {inicio, fin}`.
- `TurnoDto = {id, inicio, fin, estado, origen, paciente: {id, nombre, apellido}, prestacion: {id, nombre}, pendienteHasta|null, canceladoPor|null, motivoCancelacion|null, version}`. Nunca datos clínicos ni DNI/email del paciente (regla #10; la agenda no los necesita).
- Errores: `400` (faltante/formato), `404` (turno/paciente/prestación/bloqueo inexistente o de otro consultorio), `409` `type …/hueco-no-disponible` con `alternativas: HuecoDto[]` (≤ 3) o `…/modificacion-concurrente`, `422` `…/transicion-invalida` o `ValidacionDominioException` con `errors[{field: "inicio"|"prestacionId"|"desde"|"hasta", message}]`.
- **Mover con `PUT /turnos/{id}/mover`** (CHANGES.md) en vez del `PATCH /turnos/{id}` de KB 02: misma convención de acción explícita que `/cancelar` y `/atendido` (y que C-06 con `/desactivar`). KB 02 se corrige al archivar.
- `turnos-afectados` cuelga de `/bloqueos/{id}` en un controller de C-07 (no se toca el `BloqueoController` de C-06).

### D2. Esquema V9 y resolución de PA-06 🔒 (confirmar P1/P2/P3)

```sql
CREATE TABLE turno (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  consultorio_id bigint NOT NULL REFERENCES consultorio(id) ON DELETE RESTRICT,
  paciente_id    bigint NOT NULL REFERENCES paciente(id)    ON DELETE RESTRICT,
  prestacion_id  bigint NOT NULL REFERENCES prestacion(id)  ON DELETE RESTRICT,
  inicio timestamptz NOT NULL, fin timestamptz NOT NULL,
  estado varchar(30) NOT NULL CHECK (estado IN (...7 estados...)),
  origen varchar(20) NOT NULL CHECK (origen IN ('PUBLICA','ODONTOLOGO','LISTA_ESPERA')),
  pendiente_hasta timestamptz,
  motivo_cancelacion varchar(200),
  cancelado_por varchar(20) CHECK (cancelado_por IN ('ODONTOLOGO','PACIENTE','SISTEMA')),
  version bigint NOT NULL DEFAULT 0,
  creado_en timestamptz NOT NULL, actualizado_en timestamptz NOT NULL,
  CONSTRAINT turno_intervalo_valido CHECK (inicio < fin),
  CONSTRAINT turno_pendiente_con_vencimiento CHECK (estado <> 'PENDIENTE_CONFIRMACION' OR pendiente_hasta IS NOT NULL),
  CONSTRAINT turno_sin_superposicion EXCLUDE USING gist
    (consultorio_id WITH =, tstzrange(inicio, fin, '[)') WITH &&)
    WHERE (estado NOT IN ('CANCELADO','EXPIRADO'))
);
-- índices: (consultorio_id, inicio), (paciente_id, inicio), (estado, inicio), (prestacion_id)
```

- **PA-06 — propuesta: `ATENDIDO` y `AUSENTE` siguen ocupando el rango** (predicado `estado NOT IN ('CANCELADO','EXPIRADO')`):
  - Es el espejo literal de RN-AG-10 ("`CANCELADO` o `EXPIRADO` … liberan el hueco") y de RN-AS-04 ("un turno ausente no libera retroactivamente el hueco").
  - Costo cero: esos rangos ya pasaron y no se puede reservar en el pasado (RN-AG-11).
  - Evita dos bordes reales: (a) marcar `ATENDIDO` un turno antes de su hora (no hay restricción temporal en RN-TU-07) liberaría un rango futuro que el odontólogo sí usó; (b) revertir un `AUSENTE` (RN-AS-06, C-08) nunca puede chocar con otro turno, porque el rango nunca se liberó.
  - Alternativa (suposición de KB 04): sacar `ATENDIDO`/`AUSENTE` del predicado (`estado IN ('PENDIENTE_CONFIRMACION','RESERVADO','CONFIRMADO')`). Cambiarla es una línea de V9 + un escenario de spec + un test.
  - Consecuencia: RN-AG-05 en KB 05 debe decir "ocupan agenda todos los estados salvo `CANCELADO`/`EXPIRADO`"; KB 04 §turno y KB 10 (PA-06 resuelta) se actualizan al archivar.
- `EstadoTurno.ocupaAgenda()` replica el mismo predicado en Java, **solo** para la disponibilidad orientativa y los listados (un test de integración compara ambos para evitar divergencias).
- `estado`/`origen`/`cancelado_por` como `varchar` + `CHECK` + `@Enumerated(STRING)` (más simple que `CREATE TYPE` con `ddl-auto=validate`).

### D3. FKs diferidas en V9 🔒 (coordinar con P3 y P4)

- `ALTER TABLE token_acceso ADD CONSTRAINT token_acceso_turno_fk FOREIGN KEY (turno_id) REFERENCES turno(id) ON DELETE RESTRICT;` ídem `notificacion.turno_id`; `CREATE INDEX IF NOT EXISTS` sobre ambas columnas (C-03 puede ya tener un índice líder por `turno_id`; `IF NOT EXISTS` sobre un nombre acordado evita duplicar).
- `evolucion_clinica.turno_id`: bloque `DO $$ … IF to_regclass('evolucion_clinica') IS NOT NULL THEN ALTER TABLE … END IF; $$` (nota (3) de CHANGES.md). Si C-05 no está aplicada, la FK se difiere: C-05 debe agregarla en **su** migración si llega después de V9 (y en ese caso renumerarse a la siguiente versión libre ≥ V10, porque Flyway rechaza una V8 que aparece después de V9 sin `outOfOrder`).
- `ON DELETE RESTRICT` en todas: los turnos no se borran (auditoría RN-TU-05).

### D4. Modelo `Turno` y máquina de estados (contrato P1 → P2)

- `domain.turno.Turno extends AuditableEntity`: `consultorio` / `paciente` / `prestacion` `@ManyToOne(LAZY, optional=false)`, `inicio`, `fin`, `estado`, `origen`, `pendienteHasta`, `motivoCancelacion`, `canceladoPor`, `@Version long version`.
- Fábricas: `Turno.reservado(consultorio, paciente, prestacion, inicio, origen)` y `Turno.pendiente(…, pendienteHasta)`; `fin = inicio + prestacion.duracionMin`.
- Transiciones (lanzan `TransicionInvalidaException extends ReglaNegocioException`, código `transicion-invalida` → 422): `confirmar()`, `confirmarSolicitud()` (pendiente → reservado, C-09), `cancelar(CanceladoPor, motivo)`, `marcarAtendido()`, `marcarAusente()` (C-08), `expirar()`, `mover(nuevoInicio)` (solo `RESERVADO`/`CONFIRMADO`; conserva duración y **estado**, ver D11-preguntas).
- La tabla de transiciones vive en `EstadoTurno` (`puedePasarA(EstadoTurno)`), testeable sin Spring: 7×7 combinaciones parametrizadas.
- Eventos (records en `domain.turno.evento`): `TurnoCreado(turnoId, consultorioId, estado, origen)`, `TurnoMovido(turnoId, inicioAnterior, finAnterior, inicioNuevo, finNuevo)`, `TurnoCancelado(turnoId, canceladoPor)`, `HuecoLiberado(consultorioId, turnoOrigenId, prestacionId, inicio, fin)`.

### D5. Núcleo de reserva reutilizable

- `application.turno.ReservaTurnoService.reservar(SolicitudReserva)` (`@Transactional`), con `SolicitudReserva(pacienteId, prestacionId, inicio, origen, estadoInicial, PoliticaAnticipacion)`:
  1. Expira pendientes vencidas del consultorio (D8).
  2. Carga prestación (404 / inactiva → 422) y paciente (404), filtrando por `ConsultorioActual.id()`.
  3. `ValidadorHorarioTurno`: inicio alineado a la granularidad, no en el pasado / anticipación de la política, contenido en un `horario_trabajo` del día (zona del consultorio), sin solape con `bloqueo_agenda` → `ValidacionDominioException` (field `inicio`).
  4. `saveAndFlush`; captura `DataIntegrityViolationException` cuya causa raíz es `PSQLException` con SQLState `23P01` y constraint `turno_sin_superposicion` → `HuecoNoDisponibleException` (cualquier otra violación se relanza).
  5. Publica `TurnoCreado`.
- `POST /turnos` lo invoca con `ODONTOLOGO`/`RESERVADO`/`PoliticaAnticipacion.odontologo()`; C-09 con `PUBLICA`/`PENDIENTE_CONFIRMACION`/`publica()`; C-10 con `LISTA_ESPERA`/`RESERVADO`.
- **Alternativa descartada**: chequeo previo `existsOverlap` en Java como garantía. Vulnerable a carreras; solo se acepta como optimización y no se implementa.

### D6. Alternativas del 409 fuera de la transacción fallida

- En PostgreSQL, tras un `23P01` la transacción queda abortada: no se puede consultar disponibilidad en ella.
- `application.turno.TurnoComandos` (bean **sin** `@Transactional`, usado por el controller) llama a `ReservaTurnoService` / `GestionTurnoService`; si atrapa `HuecoNoDisponibleException`, pide a `DisponibilidadService` (transacción read-only nueva) hasta 3 huecos para la misma prestación desde el inicio pedido, en una ventana de 7 días, y relanza la excepción enriquecida.
- `GlobalExceptionHandler` agrega `@ExceptionHandler(HuecoNoDisponibleException.class)` antes del de `ConflictoException`, con la propiedad `alternativas`.
- Alternativa descartada: savepoint (`Propagation.NESTED`): más opaco y acopla el cálculo a la transacción de escritura.

### D7. Disponibilidad como Strategy pura

- `domain.agenda.CalculadoraDisponibilidad` (sin Spring ni JPA): entrada = ventana `[desde, hasta]` en fechas locales, `ZoneId`, horarios `(diaSemana, horaDesde, horaHasta)`, bloqueos `[desde, hasta)`, intervalos ocupados `[inicio, fin)`, `duracion`, `granularidad`, `minimoInicio`; salida = `List<Hueco>` ordenada.
- Candidatos: para cada día y horario, primer minuto del día alineado a la granularidad (`minutoDelDía % granularidad == 0`) ≥ `horaDesde`, avanzando de a `granularidad` mientras `inicio + duracion ≤ horaHasta`; se descartan los que solapan bloqueos u ocupados (semiabierto) o empiezan antes de `minimoInicio`. Tolera duraciones no múltiplo (aviso de C-06).
- `PoliticaAnticipacion` (Strategy): `odontologo()` → `minimoInicio = ahora`; `publica(anticipacionMin)` → `ahora + anticipación`. La regla de lista de espera RN-LE-10 reutiliza `publica`.
- `application.agenda.DisponibilidadService` (read-only) arma la entrada desde repositorios: ocupados = turnos con `ocupaAgenda()` **y** sin pendientes vencidas (`estado = PENDIENTE_CONFIRMACION AND pendiente_hasta <= ahora` cuenta como libre aunque aún no esté marcado `EXPIRADO`).
- La misma calculadora sirve al `ValidadorHorarioTurno` (¿el inicio pedido es un candidato válido ignorando ocupados?), así las reglas de horario no se duplican.

### D8. Expiración de pendientes

- `TurnoRepository.expirarPendientesVencidas(consultorioId, ahora)`: `UPDATE turno SET estado='EXPIRADO', version = version + 1, actualizado_en = :ahora WHERE consultorio_id = :c AND estado='PENDIENTE_CONFIRMACION' AND pendiente_hasta <= :ahora` (`@Modifying(clearAutomatically = true)`). Masivo e idempotente; incrementa `version` para que una transacción concurrente que tenía la entidad cargada falle con 409.
- Se invoca al inicio de `reservar` y de `mover` (misma transacción), y desde `ExpiracionPendientesScheduler` (`@Scheduled(fixedDelayString = "${app.turnos.expiracion-intervalo:PT1M}")`, en `infrastructure`). `@EnableScheduling` lo agrega quien llegue primero (C-03 también lo necesita); en tests se desactiva por propiedad y se invoca el job directamente con `MutableClock`.
- La expiración **no** publica `HuecoLiberado` (el hueco nunca se ofreció como ocupado a la lista de espera; RN-LE-01 habla de cancelar o mover).

### D9. Concurrencia sobre un mismo turno (P2)

- Mover/cancelar/atender: `findByIdAndConsultorioId` → método de dominio → `flush`. Dos transacciones concurrentes: la segunda falla con `ObjectOptimisticLockingFailureException` → `409` `modificacion-concurrente` (mapeo nuevo en `GlobalExceptionHandler`, sin filtrar detalles).
- Mover = `UPDATE` del mismo registro: la exclusión no compara la fila consigo misma, por lo que mover parcialmente sobre el propio rango funciona y el rango viejo se libera en el mismo commit. Un `23P01` al mover usa la misma traducción de D5 (helper compartido `TraductorViolacionAgenda`).

### D10. Eventos y fase de los listeners 🔒 (confirmar P2/P3)

- Los servicios publican con `ApplicationEventPublisher` dentro de su transacción; mover publica `TurnoMovido` + `HuecoLiberado(rango anterior)`; cancelar publica `TurnoCancelado` + `HuecoLiberado`. Se publica el rango anterior completo aunque el nuevo lo solape: C-10 recalcula compatibilidad y disponibilidad igual.
- Listener de notificaciones (P3): `@TransactionalEventListener(phase = BEFORE_COMMIT)` → la fila del outbox se inserta en la misma transacción que el cambio del turno: si el turno se revierte, no queda notificación fantasma; si el envío falla después, no afecta el cambio (RN-NO-06; el envío lo hace el scheduler de C-03).
  - Requisito para C-03: `encolar` no debe lanzar ante duplicado (inserción idempotente), para no revertir el cambio del turno.
  - Alternativa: `AFTER_COMMIT` + `REQUIRES_NEW`: puede perder la notificación si el proceso cae entre commit y listener. Descartada para el outbox.
- Consumidores con efectos externos o trabajo pesado (C-10) usan `AFTER_COMMIT`. Ningún efecto queda si la transacción se revierte (test con `@RecordApplicationEvents` + escenario 409).

### D11. Notificaciones (P3)

- `TurnoCreado` con estado `RESERVADO` → `CONFIRMACION_TURNO`; `TurnoMovido` → `CONFIRMACION_TURNO` (datos nuevos); `TurnoCancelado` → `CANCELACION`. `TurnoCreado` `PENDIENTE_CONFIRMACION` → nada (C-09 envía `CONFIRMAR_SOLICITUD`).
- Datos de plantilla: fecha y hora en la zona del consultorio, nombre/dirección del consultorio. Sin prestación (puede revelar información clínica), sin DNI, sin token (los enlaces de cancelar/confirmar los suma C-09/C-11).
- Paciente sin email → no se encola; log `INFO` con `turnoId` únicamente (regla #10).
- **Dependencia de idempotencia**: KB 04 define índice único (`turno_id`, `tipo`) "para recordatorios". Si C-03 lo crea global, una segunda `CONFIRMACION_TURNO` tras mover chocaría. Propuesta a P3: índice único **parcial** `WHERE tipo = 'RECORDATORIO'` (o clave de deduplicación con versión del turno). Ver Open Questions.

### D12. Paquetes y archivos por dueño (evitar conflictos de merge)

```
domain/turno/        Turno, EstadoTurno, OrigenTurno, CanceladoPor, TurnoRepository,
                     TransicionInvalidaException, HuecoNoDisponibleException, evento/*   (P1; P2 suma métodos de dominio con su test)
domain/agenda/       CalculadoraDisponibilidad, Hueco, PoliticaAnticipacion           (P1)
application/turno/   ReservaTurnoService, TurnoComandos, TraductorViolacionAgenda (P1);
                     GestionTurnoService (mover/cancelar/atender) (P2)
application/agenda/  DisponibilidadService, AgendaConsultaService, TurnosAfectadosService (P1); TurnosHoy en AgendaConsultaService (P2)
application/notificacion/ NotificacionesTurnoListener                                (P3)
api/turno/           TurnoController (POST P1; mover/cancelar/atendido/hoy P2), DTOs
api/agenda/          AgendaController (P1)
infrastructure/scheduling/ ExpiracionPendientesScheduler                              (P1)
db/migration/V9__turno.sql                                                            (P1)
```

### D13. Estrategia de pruebas

- Unitarias de dominio (sin Spring): matriz de transiciones, `CalculadoraDisponibilidad` (contigüidad `[)`, bloqueos, granularidad, cruce de medianoche UTC, día sin horario, `minimoInicio`), `PoliticaAnticipacion`.
- Migración (Testcontainers): `FlywayV9MigrationIT` — exclusión parcial (cada estado), `CHECK`s, FKs (incluidas las de C-03), contigüidad.
- Concurrencia (Testcontainers + `ExecutorService` + `CountDownLatch`, sin `@Transactional` en el test): dos `reservar` del mismo hueco → exactamente 1 éxito y 1 `HuecoNoDisponibleException`; dos operaciones sobre el mismo turno → 1 éxito, 1 409. Limpieza de filas en `@AfterEach` (contenedor compartido).
- Expiración con `MutableClock`.
- API: ITs sobre `AbstractWebIntegrationTest` (sesión + CSRF reales) para códigos 201/200/401/404/409/422 y forma del `ProblemDetail`.
- Eventos: `@RecordApplicationEvents`; notificaciones con el canal falso en memoria de C-03.

## Risks / Trade-offs

- [C-03/C-04/C-06 se demoran → V9 no compila contra el esquema] → Los grupos de tasks que no tocan la base (contrato, calculadora, máquina de estados) se hacen primero; los bloqueados se marcan. Si al 22-nov falta alguna dependencia, P1 decide con el equipo apoyar el change bloqueante (CHANGES.md nota (2)).
- [Nombres de C-04 distintos a KB 04] → Solo `Paciente`, `nombre`, `apellido`, `email`, `consultorio` se usan; se ajusta al mergear C-04.
- [Divergencia entre `ocupaAgenda()` y el predicado SQL] → Test de integración que inserta un turno en cada estado y compara el rechazo de la base con `ocupaAgenda()`.
- [Mensaje del `23P01` mal clasificado] → Se identifica por SQLState **y** nombre de constraint; test con otra violación (FK) que no debe convertirse en 409.
- [Índice único global de notificaciones (C-03)] → D11; si no se acuerda, mover no re-notifica (queda documentado) en lugar de fallar.
- [Scheduler de expiración y tests] → Propiedad para desactivarlo en tests; el job es invocable directamente.
- [PA-06 se decide al revés] → Cambio acotado (D2).

## Migration Plan

- Orden de integración: C-06 (V4), C-04 (V5) y C-03 (V6, V7) en `main` → rebase de `feat/c-07-turnos-agenda-core` → V9. Flyway valida el orden; V8 (C-05) solo si ya está en `main` (D3).
- Rollback en dev/demo: recrear la base (datos ficticios, regla #12). V9 incluye como comentario el inverso (`ALTER TABLE … DROP CONSTRAINT …_turno_fk; DROP TABLE turno;`).

## Open Questions

- Índice de idempotencia de `notificacion` (D11): ¿parcial por `RECORDATORIO`? Lo decide P3 en C-03; no cambia la estructura de C-07, solo si mover re-notifica.
- Mover un turno `CONFIRMADO`: ¿debe volver a `RESERVADO` (el paciente confirmó otro horario)? Propuesta: conservar el estado (RN-TU-02 no prevé `CONFIRMADO → RESERVADO`). Confirmar con P2.
- ¿`ATENDIDO` exige que el turno haya empezado? Propuesta: no (RN-TU-07 no lo pide; con D2 no tiene efectos sobre la agenda).
- ¿`paciente.email` es nullable en C-04? Si es obligatorio, el escenario "Paciente sin email" queda como defensa.
