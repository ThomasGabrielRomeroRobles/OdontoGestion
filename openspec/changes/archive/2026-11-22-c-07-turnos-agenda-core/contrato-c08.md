# Contrato final de turnos y agenda para C-08 (frontend, P5)

> Publicado al cerrar C-07 (tarea 12.4). Es el contrato **implementado y probado** (`TurnoApiIT`, `AgendaApiIT`, `TurnosHoyApiIT`, `TurnosAfectadosApiIT`, `GlobalExceptionHandlerWebMvcTest`); reemplaza al "contrato provisional v0" de `openspec/changes/c-08-agenda-frontend-ausentismo/design.md` (D1). Fuente de diseño: `design.md` D1 de este change.

## Convenciones

- Prefijo `/api/v1`. JSON en camelCase. Todos los endpoints exigen sesión (`401` sin ella); las escrituras (`POST`/`PUT`) exigen el header CSRF `X-XSRF-TOKEN` (`403` sin él), como el resto de la API.
- **Instantes** (`inicio`, `fin`, `pendienteHasta`, `alternativas[]`): ISO-8601 en UTC con `Z` (p. ej. `2026-11-23T13:00:00Z`). Mostrarlos en `America/Argentina/Buenos_Aires`.
- **Fechas de consulta** (`desde`, `hasta`): `YYYY-MM-DD`, **inclusivas**, en la zona del consultorio (no instantes). Rango de hasta 31 días.
- **Errores**: `application/problem+json`, `type` = `https://odontogestion.example/problemas/<codigo>`. Códigos que emite C-07:

| Status | `type` (sufijo) | Cuándo |
|---|---|---|
| 400 | (sin sufijo propio; `errors[{field,message}]`) | parámetro faltante o mal formado, cuerpo inválido, `motivo` > 200 caracteres |
| 404 | `recurso-no-encontrado` | turno, paciente, prestación o bloqueo inexistente o de otro consultorio |
| 409 | `hueco-no-disponible` + `alternativas` | el intervalo ya está ocupado (crear o mover) |
| 409 | `modificacion-concurrente` | otro cambio sobre el mismo turno se aplicó antes: recargar la agenda |
| 422 | `transicion-invalida` | acción no permitida desde el estado actual (p. ej. atender un `CANCELADO`, mover un `PENDIENTE_CONFIRMACION`) |
| 422 | `regla-de-negocio` con `errors[{field,message}]` | `field` = `inicio` (fuera de horario, dentro de un bloqueo, no alineado a la granularidad, en el pasado), `prestacionId` (inactiva), `pacienteId` (dado de baja), `desde`/`hasta` (rango inválido) |

## `TurnoDto` (respuesta de todos los endpoints de turnos y de las listas)

```json
{
  "id": 41,
  "inicio": "2026-11-23T13:00:00Z",
  "fin": "2026-11-23T13:30:00Z",
  "estado": "RESERVADO",
  "origen": "ODONTOLOGO",
  "paciente": { "id": 7, "nombre": "Ana", "apellido": "Ficticia" },
  "prestacion": { "id": 3, "nombre": "Consulta" },
  "pendienteHasta": null,
  "canceladoPor": null,
  "motivoCancelacion": null,
  "version": 0
}
```

- `estado`: `PENDIENTE_CONFIRMACION | RESERVADO | CONFIRMADO | CANCELADO | AUSENTE | ATENDIDO | EXPIRADO`. `origen`: `PUBLICA | ODONTOLOGO | LISTA_ESPERA`. `canceladoPor`: `ODONTOLOGO | PACIENTE | SISTEMA | null`.
- `pendienteHasta` solo viene en `PENDIENTE_CONFIRMACION`; `motivoCancelacion` es `null` si no se indicó motivo.
- `version` crece con cada cambio; hoy solo informa (no hay `If-Match`).
- **Nunca** trae DNI, email, teléfono ni datos clínicos del paciente (regla dura #10).

## Endpoints

### `POST /api/v1/turnos`

Request: `{ "pacienteId": 7, "prestacionId": 3, "inicio": "2026-11-23T13:00:00Z" }` (los tres obligatorios).

- `201` + header `Location: /api/v1/turnos/41` + `TurnoDto` (`estado: RESERVADO`, `origen: ODONTOLOGO`, `fin = inicio + duración de la prestación`). El odontólogo no está sujeto a la anticipación mínima de reserva.
- `409 hueco-no-disponible`:

```json
{
  "type": "https://odontogestion.example/problemas/hueco-no-disponible",
  "title": "Horario no disponible",
  "status": 409,
  "detail": "El horario elegido ya no está disponible.",
  "instance": "/api/v1/turnos",
  "alternativas": [
    { "inicio": "2026-11-23T13:30:00Z", "fin": "2026-11-23T14:00:00Z" },
    { "inicio": "2026-11-23T13:45:00Z", "fin": "2026-11-23T14:15:00Z" },
    { "inicio": "2026-11-23T14:00:00Z", "fin": "2026-11-23T14:30:00Z" }
  ]
}
```

  `alternativas`: hasta 3 huecos libres **para la misma prestación**, desde el inicio pedido, en los próximos 7 días; puede ser `[]`.
- `404` paciente o prestación inexistente; `422` con `errors` (`inicio`, `prestacionId`, `pacienteId`); `400` cuerpo inválido; `401`; `403`.

### `PUT /api/v1/turnos/{id}/mover`

Request: `{ "inicio": "2026-11-23T14:00:00Z" }`. Solo `RESERVADO` o `CONFIRMADO`; conserva duración, **estado**, paciente y prestación.

- `200` + `TurnoDto` con el nuevo rango y `version` + 1. Mover parcialmente sobre su propio rango (p. ej. correrlo 15 min) es válido. El rango anterior queda libre en el mismo cambio.
- `409 hueco-no-disponible` con `alternativas` (misma forma que arriba; el turno conserva su horario). `409 modificacion-concurrente` si otro cambio se aplicó antes.
- `422 transicion-invalida` (estado no movible) o `422` con `errors[field=inicio]`; `404`; `400`.

### `POST /api/v1/turnos/{id}/cancelar`

Request **opcional**: sin cuerpo, `{}` o `{ "motivo": "Imprevisto del consultorio" }` (máx. 200 caracteres; más → `400` con `errors[field=motivo]`).

- `200` + `TurnoDto` con `estado: CANCELADO`, `canceladoPor: ODONTOLOGO` y `motivoCancelacion` (o `null`). Desde `PENDIENTE_CONFIRMACION`, `RESERVADO` o `CONFIRMADO`; el hueco queda libre.
- `422 transicion-invalida` si ya es final (`ATENDIDO`, `AUSENTE`, `CANCELADO`, `EXPIRADO`); `404`; `409 modificacion-concurrente`.

### `POST /api/v1/turnos/{id}/atendido`

Sin cuerpo. `200` + `TurnoDto` con `estado: ATENDIDO` (desde `RESERVADO` o `CONFIRMADO`; no exige que el turno haya empezado). `422 transicion-invalida` desde cualquier otro estado; `404`.

### `GET /api/v1/agenda?desde=2026-11-23&hasta=2026-11-29`

`200` + `TurnoDto[]` de los turnos que **empiezan** en el rango, **todos los estados**, ordenados por `inicio`. `422` rango invertido o > 31 días (`errors[field=hasta]`); `400` si falta un parámetro o la fecha está mal formada.

### `GET /api/v1/agenda/disponibilidad?prestacionId=3&desde=2026-11-23&hasta=2026-11-23`

`200` + `[{ "inicio": "2026-11-23T12:00:00Z", "fin": "2026-11-23T12:30:00Z" }, …]` ordenado por `inicio`: huecos libres de la prestación (duración de la prestación, alineados a la granularidad, dentro del horario de trabajo, sin bloqueos ni turnos que ocupan agenda; sin huecos pasados). Día sin horario → `[]`. `404` prestación inexistente, `422` prestación inactiva (`errors[field=prestacionId]`) o rango inválido, `400` parámetro faltante. Es **orientativo**: la reserva la decide la base y puede responder `409`.

### `GET /api/v1/turnos/hoy`

`200` + `TurnoDto[]` del día actual **en la zona del consultorio**, ordenados por `inicio`, **sin** `CANCELADO` ni `EXPIRADO` (sí `ATENDIDO` y `AUSENTE`).

### `GET /api/v1/bloqueos/{id}/turnos-afectados` (nuevo, no estaba en el v0)

`200` + `TurnoDto[]` de los turnos que ocupan agenda y se solapan con el bloqueo (para reubicarlos a mano). Crear un bloqueo **no** cancela ni mueve turnos. `404` si el bloqueo no existe.

## Diferencias entre el contrato v0 de C-08 y el real

| # | v0 (C-08 `design.md` D1) | Real (C-07) | Qué ajustar en `agenda-api.ts` / tipos |
|---|---|---|---|
| 1 | `paciente: { id, nombre, apellido, dni, cantidadAusencias }` | `paciente: { id, nombre, apellido }`. **Sin DNI ni `cantidadAusencias`** (regla dura #10; la agenda no los necesita) | Quitar `dni` y `cantidadAusencias` del tipo; el contador de ausencias sale de `GET /pacientes/{id}/ausencias` (backend de C-08, P3) |
| 2 | `prestacion: { id, nombre, duracionMin }` | `prestacion: { id, nombre }`. **Sin `duracionMin`** | La duración sale de `GET /prestaciones` (C-06) o de `fin - inicio` |
| 3 | `ausenteMarcadoEn` en el turno | No existe en C-07; lo agrega el backend de C-08 (P3) junto con `ausente` y `revertir-ausencia` | Mantenerlo opcional hasta que P3 publique |
| 4 | — | Campo nuevo `version` (entero) | Agregarlo al tipo (informativo) |
| 5 | `GET /agenda?desde&hasta` con **instantes** | `desde` y `hasta` son **fechas** `YYYY-MM-DD` inclusivas en la zona del consultorio, rango ≤ 31 días | Mandar fechas locales, no instantes (el día `hasta` se incluye completo) |
| 6 | `GET /agenda/disponibilidad?prestacionId&fecha&excluirTurnoId?` | `?prestacionId&desde&hasta` (fechas inclusivas, ≤ 31 días). **No existe `fecha` ni `excluirTurnoId`** | Usar `desde = hasta = fecha`. Al mover, el hueco del propio turno que se solapa con su rango (p. ej. correrlo 15 min) no aparece en la lista porque su rango sigue ocupado; `PUT /mover` sí lo acepta: ofrecer "elegir otra hora" con la lista y permitir ingresar la hora a mano si hace falta |
| 7 | `GET /turnos/hoy` "del día del consultorio" | Idem, **sin `CANCELADO` ni `EXPIRADO`** | Ninguno (el front ya los oculta) |
| 8 | `409` de crear/mover con `alternativas` | Confirmado: `type …/hueco-no-disponible`, `alternativas: [{inicio, fin}]` (≤ 3, puede ser vacío) | Ninguno |
| 9 | Errores `409`/`422`/`404` | Se suman `409 modificacion-concurrente` (otro cambio se aplicó antes: recargar) y `422 transicion-invalida` (acción no permitida por estado: recargar). `422` por campo trae `errors[{field,message}]` con `field` ∈ `inicio`, `prestacionId`, `pacienteId`, `desde`, `hasta` | Tratar `modificacion-concurrente` como `422` de acciones sobre un turno (mensaje + recarga). Agregarlo a `errores.ts` |
| 10 | `cancelar`: `{motivo?}` (≤ 200) | Igual, el cuerpo entero es opcional; `motivo` > 200 → `400` (no `422`) | Validar el largo en el cliente; mostrar el `400` por campo |
| 11 | `mover`/`atendido`/`cancelar` con `422` por transición | Confirmado: `type …/transicion-invalida`, `detail` con los estados | Ninguno |
| 12 | `POST /turnos/{id}/ausente`, `POST /turnos/{id}/revertir-ausencia`, `GET /pacientes/{id}/ausencias` | **No son de C-07**: son del backend de C-08 (P3). La máquina de estados ya admite `RESERVADO\|CONFIRMADO → AUSENTE` | Seguir contra mocks hasta que P3 los publique |
| 13 | `GET /agenda` incluye todos los estados (pregunta abierta) | Confirmado: **todos** los estados; el front decide qué ocultar | Ninguno |
| 14 | PA-06 abierta ("¿cambia qué huecos devuelve la disponibilidad?") | Resuelta: `ATENDIDO` y `AUSENTE` **siguen ocupando** el rango; revertir un `AUSENTE` (RN-AS-06) nunca puede chocar con otro turno | El mock de superposición debe considerar ocupados todos los estados salvo `CANCELADO` y `EXPIRADO` |
| 15 | — | Estados de salida de `mover`: **conserva el estado** (un `CONFIRMADO` movido sigue `CONFIRMADO`) | Ninguno |
| 16 | — | `GET /bloqueos/{id}/turnos-afectados` | Opcional: listar los turnos afectados al crear un bloqueo (cierra US-012) |

Respuesta a las preguntas abiertas del v0: (a) al revertir una ausencia el estado previo lo define P3 en C-08; (b) `GET /agenda` devuelve todos los estados; (c) PA-06 no cambia el formato, solo qué rangos ocupan (ver fila 14).
