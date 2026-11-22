# Proposal

## Why

El objetivo central de OdontoGestión (bajar el ausentismo y llenar los huecos de agenda) necesita primero una agenda confiable: hoy existen en `main` el consultorio, la sesión, las prestaciones, los horarios y los bloqueos (C-06), y en ramas en curso los pacientes (C-04) y las notificaciones (C-03), pero no hay turnos. C-07 crea el turno, su máquina de estados y la garantía de no superposición en la base (regla dura #8, RN-AG-05/06, DD-06). Está en el camino crítico (`C-06 → C-07 → C-09 → C-10 → C-12`): la página pública (C-09), la lista de espera (C-10), los recordatorios (C-11) y la vista de agenda (C-08) consumen su contrato.

## What Changes

- **Migración V9** `turno` con `ex_turno_sin_superposicion EXCLUDE USING gist (consultorio_id WITH =, tstzrange(inicio, fin, '[)') WITH &&)` parcial por estado, `ck_turno_rango CHECK (inicio < fin)` (nombres con la convención `ck_`/`ex_`/`uq_`/`ix_` de V4, V6, V7 y V8), `version` para `@Version`, índices (`consultorio_id, inicio`), (`paciente_id, inicio`), (`estado, inicio`) y FK a `consultorio`, `paciente` (V8, C-04) y `prestacion` (V4, C-06).
- V9 agrega además las FK diferidas de C-03 con la política que fija su design D1: `notificacion.turno_id → turno` `ON DELETE RESTRICT` y `token_acceso.turno_id → turno` `ON DELETE CASCADE` (C-03 crea esas columnas nullable sin FK y ya indexadas), La FK `evolucion_clinica.turno_id → turno` **no** va en V9: la agrega C-05 en su propia migración (V10 o posterior, porque V8 ya la usa C-04).
- **Resolución de PA-06** (aprobada por P1, P2 y P3 el 2026-11-22): `ATENDIDO` y `AUSENTE` **siguen ocupando** el rango; la restricción aplica a todo estado salvo `CANCELADO` y `EXPIRADO` (ver design.md D2).
- Modelo `Turno` con máquina de estados RN-TU-01/02 (transición inválida → `422`; paciente dado de baja o prestación inactiva → `422`), orígenes `PUBLICA | ODONTOLOGO | LISTA_ESPERA`, `cancelado_por`, `motivo_cancelacion`, `pendiente_hasta`.
- `DisponibilidadService` (Strategy): horarios − bloqueos − turnos que ocupan agenda, respetando duración, granularidad y anticipación (pública vs. odontólogo).
- Endpoints autenticados: `GET /api/v1/agenda/disponibilidad`, `GET /api/v1/agenda?desde&hasta`, `POST /api/v1/turnos` (US-021), `PUT /api/v1/turnos/{id}/mover` (US-022), `POST /api/v1/turnos/{id}/cancelar` (US-023), `POST /api/v1/turnos/{id}/atendido` y `GET /api/v1/turnos/hoy` (US-024), `GET /api/v1/bloqueos/{id}/turnos-afectados` (cierra US-012).
- Violación de la exclusión (SQLState `23P01`) → `HuecoNoDisponible` → `409` con alternativas; conflicto de `@Version` → `409`.
- Expiración de `PENDIENTE_CONFIRMACION` vencidas antes de cada reserva y por scheduler (RN-AG-12, RN-PU-02).
- Núcleo de reserva reutilizable por C-09 (solicitud pública, nace `PENDIENTE_CONFIRMACION`) y C-10 (aceptación de aviso, nace `RESERVADO`).
- Eventos de dominio `TurnoCreado`, `TurnoMovido`, `TurnoCancelado` y `HuecoLiberado`; integración con `NotificationService` (C-03) para `CONFIRMACION_TURNO` (crear/mover) y `CANCELACION`.

Fuera de alcance: marcar `AUSENTE` y su endpoint (C-08), solicitud pública y acciones por token (C-09), lista de espera y consumo de `HuecoLiberado` (C-10), recordatorios (C-11), frontend de agenda (C-08).

## Capabilities

### New Capabilities
- `turno-lifecycle`: turno, estados y transiciones, no superposición garantizada en la base, creación/mover/cancelar/atender, expiración de pendientes, concurrencia optimista, eventos de dominio y notificaciones al paciente.
- `agenda-availability`: cálculo de huecos libres, consulta de agenda por rango, turnos del día y turnos afectados por un bloqueo.

### Modified Capabilities
- (ninguna) — `api-error-handling` ya exige `409` para conflictos de agenda y `422` para reglas de negocio; las alternativas del `409` se especifican en `turno-lifecycle`. `agenda-configuration` (C-06) todavía no está archivada; el listado de turnos afectados por un bloqueo se especifica aquí.

## Impact

- **Estado de dependencias (al 2026-11-22)**: las tres están en `main`: C-06 (`prestacion`, `horario_trabajo`, `bloqueo_agenda`, V4; PR #3), C-03 backend (`NotificationService`, `notificacion` V6, `token_acceso` V7; PR #4, change sin archivar por el frontend de P6) y C-04 (`paciente`, V8; PR #6). Ningún grupo de tasks queda bloqueado por otro change.
- **Flyway**: en `main` V1–V4, V6, V7 y V8 (V5 no existe: C-04 se renumeró a V8). **V9 es de C-07**; C-05 pasa a V10 o posterior.
- **Código**: nuevos paquetes `domain/turno`, `domain/agenda`, `application/turno`, `application/agenda`, `api/turno`, `api/agenda`; `GlobalExceptionHandler` agrega el mapeo de `HuecoNoDisponibleException` (con `alternativas`) y de `ObjectOptimisticLockingFailureException`.
- **Contrato** consumido por C-08 (P5), C-09 (P1/P2), C-10 (P1/P2) y C-11 (P3).
- **Governance CRITICO**: plan aprobado por Thomas (P1), Martina (P2) y Jeremías (P3) el 2026-11-22. Los ajustes posteriores (alineación con C-03/C-04 ya mergeados) los revisa y aprueba **solo Thomas (P1)**.
- **KB**: al cerrar, actualizar `04_modelo_de_datos.md` (§turno), `05` (RN-AG-05) y `10` (PA-06 resuelta) con la decisión final de PA-06.
