# Tasks

> TDD estricto (RED → GREEN → TRIANGULATE → REFACTOR). Ids y datos ficticios. "Ahora" del reloj fijo de los ITs: 2026-11-20T12:00Z (`MutableClock`, restaurar en `@AfterEach`). Los ITs de turnos extienden `AbstractTurnoIT`.

## 0. Precondiciones

- [x] 0.1 Safety net: `cd backend && ./gradlew test` en verde sobre `feat/c-08-ausentismo-backend` recién creada desde `main`; anotar la cantidad de tests como baseline; si hay fallas previas, reportar y no seguir — _Baseline 2026-11-23: backend 1087 tests en verde_
- [ ] 0.2 Avisar a Juan Ignacio (P5) el contrato final (design D5: `ausenteMarcadoEn`, `paciente.cantidadAusencias`, sin DNI en el turno; forma del historial) para su tarea 9.1; verificar con el aviso registrado en el canal del equipo o en el PR

## 1. Migración V11

- [x] 1.1 RED: `FlywayV11MigrationIT`: versión `11` aplicada; columnas `ausente_marcado_en` (`timestamptz`, nullable) y `estado_previo_ausencia` (`text`, nullable) en `turno`; con inserciones directas de turnos ficticios: `AUSENTE` sin marca o sin estado previo → rechazado; `RESERVADO` con marca o con estado previo → rechazado; `AUSENTE` con estado previo `CANCELADO` → rechazado; `AUSENTE` con marca y estado previo `RESERVADO`/`CONFIRMADO` → aceptado; borrar las filas al final; confirmar que falla [patient-absenteeism › Reversión de un turno reservado (base)]
- [x] 1.2 GREEN: crear `db/migration/V11__ausencia_turno.sql` según design D1 (columnas, `ck_turno_ausencia`, `ck_turno_estado_previo_ausencia`, rollback comentado); mapear las columnas en `Turno` (`@Enumerated` para el estado previo) sin lógica todavía; verificar que 1.1 pasa, que `ddl-auto=validate` arranca y que `FlywayV1MigrationIT` sigue en verde — _Además se ajustaron los helpers de tests que insertan turnos `AUSENTE` a mano (`AbstractTurnoIT.insertarTurno`, `FlywayV9MigrationIT`) para completar la marca y el estado previo, y `FlywayDesdeCeroIT` suma la versión `11`_

## 2. Dominio del turno (unitarios, sin Spring)

- [x] 2.1 RED → GREEN: en `TurnoTest`, `marcarAusente(ahora, toleranciaMin)`: `RESERVADO` y `CONFIRMADO` a `inicio + tolerancia` exacto → `AUSENTE` con `ausenteMarcadoEn = ahora` y estado previo guardado; un segundo antes → `AusenteAntesDeToleranciaException` (código `ausente-antes-de-tolerancia`) sin cambios; desde `PENDIENTE_CONFIRMACION`, `CANCELADO`, `EXPIRADO`, `ATENDIDO` y `AUSENTE` → `TransicionInvalidaException` sin cambios; reemplazar los usos de `marcarAusente()` sin argumentos en `TurnoTest`; verificar con `./gradlew test --tests '*TurnoTest'` [patient-absenteeism › Ausente pasada la tolerancia, Antes de la tolerancia, Justo al cumplirse la tolerancia, Turno ya ausente, Turno pendiente, cancelado o atendido]
- [x] 2.2 RED → GREEN: `revertirAusencia(ahora)`: marcado a las 10:20, revertir a las 10:20 + 24 h − 1 s → vuelve al estado previo (`RESERVADO` y `CONFIRMADO`) con las dos columnas en `null`; a las 10:20 + 24 h exactas → `ReversionFueraDePlazoException` (código `reversion-fuera-de-plazo`) sin cambios; desde `RESERVADO` o `ATENDIDO` → `TransicionInvalidaException`; tras revertir, `marcarAusente` vuelve a funcionar; verificar con `TurnoTest` [patient-absenteeism › Reversión de un turno reservado, Reversión de un turno confirmado, Fuera de plazo, Turno que no está ausente, Marcar de nuevo después de revertir]
- [x] 2.3 RED → GREEN: en `EstadoTurnoTest`, `AUSENTE` sigue sin destinos en `puedePasarA` (atendido, cancelar, confirmar, `RESERVADO`) y en `TurnoTest` un turno `AUSENTE` no se puede mover ni cancelar; tests de las dos excepciones nuevas (código, mensaje sin datos del paciente); verificar con `./gradlew test --tests '*EstadoTurnoTest' --tests '*TurnoTest' --tests '*ExceptionTest'` [turno-lifecycle › Ausente solo sale por reversión]

## 3. Contador de ausencias

- [x] 3.1 RED → GREEN: `PacienteRepositoryAusenciasIT`: `incrementarAusencias(id)` suma 1; `decrementarAusencias(id)` resta 1 y devuelve 1; con el contador en 0 devuelve 0 y no viola el `CHECK`; implementar los dos `@Modifying` según design D3; verificar que pasa
- [x] 3.2 RED → GREEN: concurrencia: dos hilos (latch) incrementan el mismo paciente en transacciones separadas, 10 rondas → el contador sube exactamente 2 por ronda; mutación: reemplazar el `UPDATE` relativo por leer-y-guardar la entidad y confirmar que el test falla; verificar que pasa con el `UPDATE` [patient-absenteeism › Marcas simultáneas del mismo paciente]

## 4. Casos de uso y eventos

- [x] 4.1 RED: `AusenciaTurnoServiceIT` (`AbstractTurnoIT`): turno `RESERVADO` que empezó a las 11:40 con tolerancia 15 → `marcarAusente` a las 12:00 devuelve `TurnoDto` `AUSENTE` con `ausenteMarcadoEn` 12:00 y `paciente.cantidadAusencias` +1, y se publica `TurnoAusente` (sin `HuecoLiberado`); rechazo por tolerancia o transición → contador sin cambio y sin evento; turno de otro consultorio → `RecursoNoEncontradoException`; confirmar que falla [patient-absenteeism › Ausente pasada la tolerancia, Contador incrementado, Marca rechazada no cuenta; turno-lifecycle › Ausente sin hueco liberado]
- [x] 4.2 GREEN: eventos `TurnoAusente` y `AusenciaRevertida`, `GestionTurnoService.marcarAusente` y `revertirAusencia` según design D2–D4, y los dos métodos en `TurnoComandos`; verificar que 4.1 pasa
- [x] 4.3 RED → GREEN: en `AusenciaTurnoServiceIT`, revertir dentro de las 24 h → estado previo, contador −1, `ausenteMarcadoEn` `null` y evento `AusenciaRevertida`; fuera de plazo → contador y estado sin cambio; tolerancia del consultorio cambiada a 30 min → a los 20 min rechazado; el intervalo de un turno `AUSENTE` sigue ocupado (crear otro en el mismo rango → `409`); verificar que pasa [patient-absenteeism › Reversión de un turno reservado, Fuera de plazo, Tolerancia configurada por el consultorio, El intervalo sigue ocupado]
- [x] 4.4 RED → GREEN: `TurnoDto` con `ausenteMarcadoEn` y `paciente.cantidadAusencias` (design D5): `TurnoDtoTest` y una aserción en la agenda (`GET /api/v1/agenda`) para un turno ausente y uno reservado, sin DNI, email ni teléfono; verificar que `TurnoApiIT`, `TurnosHoyApiIT`, `TurnosAfectadosApiIT` y los ITs de C-10 siguen en verde [turno-lifecycle › Turno ausente, Turno no ausente]

## 5. Notificaciones

- [x] 5.1 RED → GREEN: en `NotificacionesTurnoIT`, marcar ausente un turno de un paciente con email → una `AUSENTE_REPROGRAMAR` `PENDIENTE` del turno con las variables `consultorio`, `inicio`, `prestacion` y sin enlaces; procesada con `CanalEnMemoria`, el cuerpo contiene `APP_BASE_URL/` y no contiene el DNI; con el canal fallando el turno sigue `AUSENTE`; implementar `alMarcarAusente` en `NotificacionesTurnoListener`; verificar que pasa [patient-absenteeism › Notificación encolada, Canal caído; turno-lifecycle › Invitación a reprogramar encolada] — _En un IT propio, `NotificacionesAusenciaIT`, que usa `TurnoComandos` porque los endpoints llegan en el grupo 6; `NotificacionesTurnoIT` sigue en verde_
- [x] 5.2 RED → GREEN: revertir con la `AUSENTE_REPROGRAMAR` todavía `PENDIENTE` → queda `FALLIDA` con error `AUSENCIA_REVERTIDA` y el siguiente `procesarPendientes()` no envía nada; con la notificación ya `ENVIADA` → sigue `ENVIADA`; implementar `NotificationService.descartarPendiente` y `alRevertirAusencia` según design D4; verificar que pasa [patient-absenteeism › Aviso todavía pendiente, Aviso ya enviado]

## 6. API

- [ ] 6.1 RED → GREEN: en `TurnoApiIT` (sesión y CSRF reales): `POST /api/v1/turnos/{id}/ausente` → `200` con `estado` `AUSENTE` y `ausenteMarcadoEn`; antes de la tolerancia → `422` `ProblemDetail` con `type` `…/ausente-antes-de-tolerancia`; desde `ATENDIDO` → `422`; id inexistente → `404`; sin CSRF → `403`; sin sesión → `401`; implementar el endpoint en `TurnoController`; verificar que pasa [patient-absenteeism › Ausente pasada la tolerancia, Antes de la tolerancia, Turno pendiente, cancelado o atendido, Turno de otro consultorio]
- [ ] 6.2 RED → GREEN: `POST /api/v1/turnos/{id}/revertir-ausencia` → `200` con el estado previo; fuera de plazo → `422` con `type` `…/reversion-fuera-de-plazo`; turno `RESERVADO` → `422`; inexistente → `404`; implementar el endpoint; verificar que pasa [patient-absenteeism › Reversión de un turno reservado, Fuera de plazo, Turno que no está ausente, Turno inexistente]
- [ ] 6.3 RED → GREEN: `AusenciasPacienteApiIT`: paciente con turnos `AUSENTE` el 3 y el 10 de noviembre y uno `ATENDIDO` el 17 → `GET /api/v1/pacientes/{id}/ausencias` devuelve `cantidad` 2 y las ausencias `[10, 3]` con `turnoId`, `inicio` y `prestacionNombre`; sin ausencias → `cantidad` 0 y lista vacía; ausencia revertida → no aparece; paciente de otro consultorio o inexistente → `404`; sin sesión → `401`; implementar `AusenciaPacienteService` y el endpoint en `PacienteController` según design D6; verificar que pasa [patient-absenteeism › Paciente con ausencias, Paciente sin ausencias, Ausencia revertida fuera del historial, Paciente de otro consultorio]
- [ ] 6.4 RED → GREEN: con `OutputCaptureExtension` y log `DEBUG` para `ar.edu.odontogestion`, marcar ausente, revertir y consultar el historial de un paciente ficticio → la salida no contiene su DNI, email ni teléfono; verificar que pasa [patient-absenteeism › Log de una marca de ausente]

## 7. Integración y cierre

- [ ] 7.1 Suite completa en verde: `cd backend && ./gradlew test`; comparar con el baseline de 0.1 (sin tests rotos) y `openspec validate c-08-ausentismo-backend --strict` sin errores
- [ ] 7.2 Prueba manual con P5 contra el backend real (perfil `dev`, seed ficticio): marcar ausente pasada la tolerancia, ver el contador en la agenda, revertir dentro de 24 h y ver el historial en el detalle del turno; anotar el resultado en el PR

## Workflow follow-up

- `/opsx:archive c-08-ausentismo-backend` cuando 7.x esté completo.
- C-08 se marca `[x]` en `CHANGES.md` cuando estén archivados este change y `c-08-agenda-frontend-ausentismo` (P5).
- PR desde `feat/c-08-ausentismo-backend`, revisado por otra persona (sugerido: P2 por el cambio en la máquina de estados).
