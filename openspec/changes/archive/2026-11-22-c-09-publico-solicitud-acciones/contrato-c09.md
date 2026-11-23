# Contrato de la página pública y de las acciones por token (C-09)

> Contrato del backend de C-09 para el frontend (`features/publico` y `features/acciones-token`, P5). Fuente de diseño: `design.md` D1 de este change. Aprobado el 2026-11-22 (P1, P2, P3, P5); implementado por P2 por acuerdo del equipo. Al cerrar el change se verifica contra lo implementado (sección "Diferencias").

## Convenciones

- Prefijo `/api/v1`. JSON en camelCase. **Sin sesión**: ningún endpoint de este contrato exige login (rutas `/publico/**` y `/acciones/**`).
- **CSRF**: los `POST` exigen la cabecera `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN` (`403` sin ella), como el resto de la API (decisión D7: se mantiene). La cookie la entrega **toda** respuesta del backend, incluido el `GET` previo de cada página (`/publico/prestaciones`, `/acciones/{token}`) y la `401` de `GET /auth/me` al arrancar la SPA. `apiRequest` ya la envía.
- **Instantes** (`inicio`, `fin`, `venceEn`, `limiteCancelacion`, `alternativas[]`): ISO-8601 en UTC con `Z`. Mostrarlos en `America/Argentina/Buenos_Aires`.
- **Fechas de consulta** (`desde`, `hasta`): `YYYY-MM-DD` inclusivas, en la zona del consultorio; rango de hasta 31 días.
- **Errores**: `application/problem+json`, `type` = `https://odontogestion.example/problemas/<codigo>`. Los errores por campo traen `errors: [{field, message}]`.
- **Rutas por token** (`/acciones/{token}`): el `instance` de todo error es una constante (`urn:odontogestion:enlace-no-disponible` en el `410`, `urn:odontogestion:ruta-por-token` en los demás errores) y ningún cuerpo contiene el token. Toda respuesta (API, `index.html`, rutas de la SPA) lleva `Referrer-Policy: no-referrer`.
- **Enlaces de email**: `APP_BASE_URL/acciones/{token}`. En desarrollo hay que arrancar el backend con `APP_BASE_URL=http://localhost:5173` (Vite) porque el backend en `:8080` no sirve rutas de la SPA hasta C-12 (SPA fallback).
- IP de origen de la limitación de tasa: `getRemoteAddr()`; `X-Forwarded-For` se ignora.

## Endpoints

### `GET /api/v1/publico/prestaciones`

`200` + lista, ordenada por nombre, solo prestaciones activas; vacía si no hay:

```json
[ { "id": 3, "nombre": "Consulta", "duracionMin": 30 }, { "id": 4, "nombre": "Limpieza", "duracionMin": 45 } ]
```

### `GET /api/v1/publico/disponibilidad?prestacionId=3&desde=2026-11-23&hasta=2026-11-23`

`200` + `[{ "inicio": "2026-11-23T13:00:00Z", "fin": "2026-11-23T13:30:00Z" }, …]` ordenado por `inicio`: huecos libres de la prestación **con la anticipación mínima pública** (los huecos que empiezan antes de `ahora + anticipación` no aparecen); una pendiente vigente ocupa, una vencida no. Solo `inicio` y `fin`: nunca datos de turnos ni de pacientes. Es **orientativo**: la solicitud puede responder `409`.

Errores: `400` parámetro faltante o fecha mal formada; `404` prestación inexistente; `422` prestación inactiva (`errors[field=prestacionId]`) o rango inválido (`errors[field=hasta]`).

### `POST /api/v1/publico/solicitudes-turno`

Request (todos obligatorios salvo `sitioWeb`):

```json
{
  "prestacionId": 3,
  "inicio": "2026-11-23T13:00:00Z",
  "nombre": "Ana",
  "apellido": "Ficticia",
  "dni": "30.123.456",
  "email": "ana@ejemplo.test",
  "telefono": "+54 11 5555-0000",
  "sitioWeb": ""
}
```

- `sitioWeb` es el **campo señuelo** (honeypot): la persona no lo ve ni lo completa; enviarlo vacío o no enviarlo es lo normal. Con texto, la respuesta es el mismo `202` pero no se crea nada.
- Formatos iguales a `PacienteRequest`: DNI de 7 u 8 dígitos con o sin puntos; email válido (máx. 254); nombre y apellido máx. 80; teléfono máx. 40.

**`202`** (único cuerpo posible de éxito: idéntico para DNI nuevo, DNI existente, paciente dado de baja y señuelo; sin ids):

```json
{ "mensaje": "Si los datos son correctos, vas a recibir un email para confirmar el turno." }
```

El turno queda `PENDIENTE_CONFIRMACION` y retiene el hueco `vencimiento_pendiente_min` minutos (30 por defecto); el email trae el enlace `CONFIRMAR_SOLICITUD`. Si el DNI ya existía, el email se envía **al email registrado** del paciente, no al ingresado.

| Status | `type` (sufijo) | Cuándo |
|---|---|---|
| 400 | (sin sufijo; `errors[{field,message}]`) | dato faltante o inválido (`dni`, `email`, `nombre`, `apellido`, `telefono`, `prestacionId`, `inicio`, `sitioWeb` > 200); sin efectos y sin consumir cupo |
| 403 | `acceso-denegado` | falta o es inválido `X-XSRF-TOKEN` |
| 404 | `recurso-no-encontrado` | prestación inexistente |
| 409 | `hueco-no-disponible` + `alternativas` | el intervalo ya está ocupado (misma forma que C-07; hasta 3 huecos con la política pública; puede ser `[]`) |
| 422 | `regla-de-negocio` + `errors[{field,message}]` | `field=inicio` (fuera de horario, en un bloqueo, no alineado, antes de la anticipación mínima) o `prestacionId` (inactiva) |
| 429 | `demasiadas-solicitudes` + cabecera `Retry-After` (segundos) | límite por IP, por DNI o por email (`PUBLIC_RATE_LIMIT_PER_HOUR`, por defecto 5 por hora) o 3.ª pendiente vigente del mismo DNI/email. **Cuerpo idéntico para cualquiera de ellos** |

```json
{
  "type": "https://odontogestion.example/problemas/demasiadas-solicitudes",
  "title": "Demasiadas solicitudes",
  "status": 429,
  "detail": "Demasiadas solicitudes. Intente nuevamente más tarde."
}
```

### `GET /api/v1/acciones/{token}`

Muestra la acción **sin consumir** el token (puede abrirse varias veces; los escáneres de email no lo gastan).

`200` + `ResumenAccion`:

```json
{
  "accion": "CANCELAR_TURNO",
  "venceEn": "2026-11-23T11:00:00Z",
  "disponible": false,
  "motivo": "FUERA_DE_LIMITE",
  "limiteCancelacion": "2026-11-23T11:00:00Z",
  "turno": {
    "consultorio": "Consultorio Ficticio",
    "prestacion": "Consulta",
    "inicio": "2026-11-23T13:00:00Z",
    "fin": "2026-11-23T13:30:00Z",
    "estado": "RESERVADO"
  }
}
```

- `accion`: `CONFIRMAR_SOLICITUD | CONFIRMAR_TURNO | CANCELAR_TURNO` (el propósito del token).
- `disponible`: `true` si la acción puede ejecutarse ahora. Si es `false`, `motivo` es `"ESTADO_NO_PERMITE"` (el estado actual del turno no admite la acción, p. ej. el odontólogo ya lo canceló) o `"FUERA_DE_LIMITE"` (solo `CANCELAR_TURNO`: pasó el límite de cancelación). Con `disponible: true`, `motivo` es `null`.
- `limiteCancelacion`: instante límite de cancelación (`inicio − limite_cancelacion_horas`, **inclusivo**: a esa hora exacta todavía se puede cancelar). Solo viene en `CANCELAR_TURNO`; en las otras acciones es `null`.
- `turno.estado`: `PENDIENTE_CONFIRMACION | RESERVADO | CONFIRMADO | CANCELADO | AUSENTE | ATENDIDO | EXPIRADO`.
- **Nunca** trae datos del paciente (nombre, DNI, email, teléfono) ni ids.

| Status | `type` | Cuándo |
|---|---|---|
| 410 | `token-invalido` | token inexistente, mal formado, vencido, usado, revocado, de otro propósito (p. ej. `ACEPTAR_AVISO`), o solicitud pública con `pendiente_hasta` ya pasado. **Mismo cuerpo para todas las causas** |

```json
{
  "type": "https://odontogestion.example/problemas/token-invalido",
  "title": "Enlace no disponible",
  "status": 410,
  "detail": "El enlace no es válido o ya fue utilizado.",
  "instance": "urn:odontogestion:enlace-no-disponible"
}
```

### `POST /api/v1/acciones/{token}`

Request: `{ "accion": "CONFIRMAR_SOLICITUD" | "CONFIRMAR_TURNO" | "CANCELAR_TURNO" }`. Debe coincidir con el propósito del token (la intención es explícita).

`200` + `ResumenAccion` con el **estado nuevo** del turno: `disponible: false`, `motivo: "ESTADO_NO_PERMITE"` y `venceEn: null` (el token ya quedó usado). Efectos:

| Acción | Efecto |
|---|---|
| `CONFIRMAR_SOLICITUD` | `PENDIENTE_CONFIRMACION → RESERVADO`; se encola la notificación `CONFIRMACION_TURNO` |
| `CONFIRMAR_TURNO` | `RESERVADO → CONFIRMADO` (el token `CANCELAR_TURNO` del mismo turno sigue vigente) |
| `CANCELAR_TURNO` | `RESERVADO\|CONFIRMADO → CANCELADO` con `canceladoPor = PACIENTE`; el hueco queda libre, se encola `CANCELACION` y los demás tokens del turno se invalidan |

Acción y consumo del token son atómicos: si la acción falla, el token **sigue vigente**.

| Status | `type` (sufijo) | Cuándo |
|---|---|---|
| 400 | (sin sufijo; `errors[{field=accion}]`) | `accion` ausente o con un valor desconocido |
| 403 | `acceso-denegado` | falta o es inválido `X-XSRF-TOKEN` (el turno no cambia y el token sigue vigente) |
| 409 | `modificacion-concurrente` | otro cambio sobre el mismo turno se aplicó a la vez (token vigente; recargar con `GET`) |
| 410 | `token-invalido` | token usado/vencido/inexistente, **o `accion` distinta del propósito** (sin consumirlo), o solicitud vencida. Mismo cuerpo que en el `GET` |
| 422 | `transicion-invalida` | el estado actual no admite la acción (turno `CANCELADO`, `ATENDIDO`, …); token vigente |
| 422 | `limite-cancelacion` | `CANCELAR_TURNO` pasado el límite (`inicio − limite_cancelacion_horas`); token vigente |

## Decisión: CSRF se mantiene en los `POST` públicos (D7)

`POST /publico/solicitudes-turno` y `POST /acciones/{token}` **exigen** `X-XSRF-TOKEN` (cookie `XSRF-TOKEN`), igual que el resto de la API; no se agrega ninguna excepción en `SecurityConfig`. La cookie la entrega toda respuesta del backend, así que el flujo de la SPA ya la tiene (`GET /auth/me` al arrancar, `GET /publico/prestaciones`, `GET /acciones/{token}`). Sin CSRF, cualquier sitio podría disparar solicitudes públicas desde el navegador de una víctima (emails a terceros, retención de huecos, consumo de su cupo por IP) o ejecutar una acción si conoce el token; con CSRF solo el origen propio puede escribir. Consecuencia para clientes no-SPA (p. ej. `curl`): primero un `GET` para obtener la cookie y luego el `POST` con la cabecera. Riesgo residual: un `403` si el usuario bloquea las cookies; la página muestra un error genérico.

## Valores de `motivo` y qué muestra la página

| `disponible` | `motivo` | La página muestra |
|---|---|---|
| `true` | `null` | el resumen y **un único botón** según `accion` (Confirmar solicitud / Confirmar asistencia / Cancelar turno) |
| `false` | `ESTADO_NO_PERMITE` | "Este turno ya no admite esta acción", sin botón |
| `false` | `FUERA_DE_LIMITE` | "Ya no se puede cancelar por este enlace (límite: …)", sin botón |

## Cobertura de escenarios

Cada escenario de `specs/public-booking` y `specs/token-actions` tiene su respuesta en este contrato: prestaciones y disponibilidad públicas (secciones 1 y 2); solicitud exitosa, DNI existente/nuevo/de baja, señuelo, validación, horario, conflicto con alternativas, límites y no enumeración (sección 3); resumen, disponibilidad de la acción, ejecución, confirmación de solicitud, confirmar asistencia, cancelación con límite, efectos, rechazo uniforme, concurrencia, CSRF y token fuera de logs (secciones 4 y 5).

## Notas de seguridad para el cliente

- Nunca poner el token en un `console.log`, en un mensaje de error ni en una URL que no sea `/acciones/{token}`.
- Los `410` se muestran siempre con el mismo texto genérico y un enlace a `/publico`; no revelar la causa.
- Un `429` (cualquier causa) se muestra como "esperá unos minutos" usando `Retry-After`; no sugerir qué límite se alcanzó.
- CSRF: el `GET` previo entrega la cookie; si un `POST` devuelve `403` por cookie ausente, mostrar un error genérico (el usuario puede tener las cookies bloqueadas).

## Diferencias entre el contrato publicado y lo implementado

Verificado el 2026-11-22 contra el backend implementado (grupos 2–10) y contra el frontend (grupos 11–13). Ningún campo, código HTTP ni `type` cambió respecto de lo publicado en el grupo 1; las diferencias son aclaraciones:

| # | Publicado (grupo 1) | Real | Qué ajustar en `publico-api.ts` / `acciones-api.ts` |
|---|---|---|---|
| 1 | `instance` constante `urn:odontogestion:ruta-por-token` en los errores de las rutas por token | Además de `/api/v1/acciones/**`, el saneamiento (`RutasPorTokenAdvice`) cubre `/api/v1/avisos/**` (rutas por token de C-10); el `410` usa `urn:odontogestion:enlace-no-disponible` como estaba | Nada: el cliente no lee `instance` |
| 2 | Formato del DNI "igual a `PacienteRequest`" | `PacienteRequest.PATRON_DNI` pasó a `public` para compartirlo con `SolicitudTurnoPublicaRequest`; mismo patrón | `validators.ts` espeja el mismo patrón (`^\s*[0-9]{1,2}\.?[0-9]{3}\.?[0-9]{3}\s*$`) |
| 3 | `409` de la solicitud con `alternativas` (puede ser `[]`) | Confirmado; el cliente descarta elementos que no sean `{inicio, fin}` de texto y, si no hay alternativas, recarga los huecos del día | Ninguno |
| 4 | `422`/`409` al ejecutar: "token vigente, recargar con `GET`" | El cliente trata `422` (`transicion-invalida`, `limite-cancelacion`) y `409` (`modificacion-concurrente`) como `estadoCambiado`: avisa y vuelve a pedir el resumen; si esa recarga da `410` muestra la página genérica | Ninguno |
| 5 | `429` con `Retry-After` en segundos | El cliente HTTP lo expone como `retryAfterSeconds` solo si es un entero (una fecha HTTP o un valor vacío se ignoran) y la página redondea a minutos hacia arriba; sin cabecera muestra "unos minutos" | Ninguno |
| 6 | Cuerpo de éxito de la solicitud `{ "mensaje": … }` | La página **no usa** el texto del servidor: muestra siempre su propia constante (idéntica al texto del contrato) para que un cambio de redacción en el backend no pueda distinguir casos | Ninguno |

Revisión de P5 (Juan Ignacio): pendiente de confirmar en el PR (el cliente ya está alineado con esta tabla).
