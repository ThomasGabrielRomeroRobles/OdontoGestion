# Design

## Context

- `C-01` dejó listo lo siguiente:
  - El backend en capas `ar.edu.odontogestion.{api,application,domain,infrastructure}`.
  - Flyway `V1` (con `btree_gist` y `citext`), `ProblemDetail` global, `Clock` en UTC y Testcontainers.
  - En el frontend: `shared/http/client.ts` (CSRF en escrituras, `401` → login, `ApiError` tipado con `fieldErrors`), un router de **coincidencia exacta de path** (`routes.find(r => r.path === pathname)`), el layout autenticado con navegación y páginas construidas con nodos DOM (sin `innerHTML`).
- `C-02` (pendiente) crea:
  - Las migraciones `V2` (`consultorio`, `usuario`) y `V3` (parámetros del consultorio), el seed de arranque, la sesión y el CSRF.
  - Los endpoints `/api/v1/consultorio` y `/api/v1/perfil`.
  - Este change depende de esas tablas (FK `consultorio_id`, `granularidad_min`) y de esos endpoints, que el frontend consume.
- Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/agenda-configuration` y `specs/web-configuration`.
- Trabajo partido: P1 hace prestaciones, P2 horarios y bloqueos, P5 el frontend. El frontend arranca antes que el backend, así que el contrato de la API se fija acá.

## Goals / Non-Goals

**Goals:**
- Contrato de API estable para que el frontend avance contra mocks sin retrabajo.
- No solapamiento de horarios garantizado por la base (coherente con la regla dura #8 y con cómo `C-07` resolverá turnos).
- Frontend sin dependencias nuevas, testeable con funciones puras (validadores, conversión de zona horaria) separadas del DOM.

**Non-Goals:**
- Listar o reubicar turnos afectados por un bloqueo: lo hace `C-07`.
- Que un bloqueo cancele turnos.
- Horarios por odontólogo (`usuario_id`) o excepciones por fecha (feriados): un bloqueo cubre ese caso.
- Paginación: los volúmenes son de decenas de filas.
- Rediseño visual: se usa el estilo mínimo existente.

## Decisions

### D1. Contrato de la API (DTOs en camelCase, fechas ISO-8601)

| Endpoint | Request | Response |
|---|---|---|
| `GET /prestaciones` | — | `200 PrestacionDto[]` ordenado por nombre |
| `POST /prestaciones` | `{nombre, duracionMin}` | `201 PrestacionDto` + `Location` |
| `PUT /prestaciones/{id}` | `{nombre, duracionMin}` | `200 PrestacionDto` |
| `POST /prestaciones/{id}/desactivar` · `/activar` | — | `200 PrestacionDto` (idempotente) |
| `GET /horarios` | — | `200 HorarioDto[]` por `diaSemana`, `horaDesde` |
| `POST /horarios` · `PUT /horarios/{id}` | `{diaSemana, horaDesde, horaHasta}` | `201`/`200 HorarioDto` |
| `DELETE /horarios/{id}` | — | `204` |
| `GET /bloqueos?desde=&hasta=` | — | `200 BloqueoDto[]` por `desde` |
| `POST /bloqueos` · `PUT /bloqueos/{id}` | `{desde, hasta, motivo?}` | `201`/`200 BloqueoDto` |
| `DELETE /bloqueos/{id}` | — | `204` |

- Formas de los DTOs:
  - `PrestacionDto = {id, nombre, duracionMin, activa}`.
  - `HorarioDto = {id, diaSemana (1=lunes … 7=domingo, ISO), horaDesde "HH:mm", horaHasta "HH:mm"}`.
  - `BloqueoDto = {id, desde, hasta, motivo|null}`, con instantes ISO-8601 en UTC con `Z`.
- **Por qué `/desactivar` y `/activar` en vez de `PATCH {activa}` o `DELETE`**: el `DELETE` sugiere un borrado que la regla prohíbe, y la acción explícita es más clara para auditar. Es el mismo estilo que `C-07` usa en `/turnos/{id}/cancelar`.
- Errores de validación: `422` con `errors: [{field, message}]`, el formato que ya parsea `shared/http/problem.ts`. Un recurso inexistente o de otro consultorio responde `404`.
- **Contrato asumido de C-02**, que el frontend consume:
  - `GET/PUT /consultorio` con `{nombre, direccion, telefono, emailContacto, granularidadMin, anticipacionMinReservaMin, limiteCancelacionHoras, recordatorioHorasAntes, vencimientoAvisoMin, vencimientoPendienteMin}`. `zonaHoraria` se muestra y no se edita.
  - `GET/PUT /perfil` con `{email, nombre, apellido, matricula}`. `email` es de solo lectura.
  - `POST /perfil/password` con `{passwordActual, passwordNueva}` → `204`.
  - Si `C-02` define otra cosa, se ajusta solo `features/config/*-api.ts` (ver D5).

### D2. Esquema: migración `V4__agenda_configuracion.sql`

- `prestacion(id bigint identity, consultorio_id FK, nombre citext, duracion_min int CHECK > 0, activa bool default true, timestamps)`, con `UNIQUE (consultorio_id, nombre)`. `citext` da la unicidad sin distinguir mayúsculas sin lógica en Java.
- `horario_trabajo(id, consultorio_id FK, dia_semana smallint CHECK 1..7, hora_desde time, hora_hasta time, CHECK hora_desde < hora_hasta)` con `EXCLUDE USING gist (consultorio_id WITH =, dia_semana WITH =, tsrange('2000-01-01'::date + hora_desde, '2000-01-01'::date + hora_hasta, '[)') WITH &&)`.
  - **Por qué**: la regla dura #8 pide garantías en la base, y dos altas concurrentes no deben poder colarse. Usar una fecha ancla convierte `time` en un rango indexable con `btree_gist`, que ya está instalado.
  - Alternativa descartada: un chequeo solo en Java, que es vulnerable a carreras.
- `bloqueo_agenda(id, consultorio_id FK, desde timestamptz, hasta timestamptz, motivo varchar(200), CHECK desde < hasta)`, con índice `gist (consultorio_id, tstzrange(desde, hasta, '[)'))` para las consultas por rango que va a hacer `C-07`. Los bloqueos **sí** pueden solaparse entre sí; eso no es un error.
- Número de versión: `V4`, si `C-02` ocupa `V2` y `V3`. Si no, se usa el siguiente libre al momento de aplicar, y se coordina con P3, que usa `V6`/`V7` en `C-03`, y con P4, en `C-04`.

### D3. La validación de duración vive en el servicio, contra la granularidad vigente

- La base solo garantiza `duracion_min > 0`. El múltiplo de la granularidad lo valida el servicio con el `granularidadMin` del consultorio al crear o editar.
- **Por qué**: la granularidad es configurable y sus cambios no son retroactivos (US-013). Un `CHECK` fijo no puede depender de otra tabla, y un trigger sería opaco para el equipo.
- Mismo criterio para las horas de un horario alineadas a la granularidad.
- La violación del `EXCLUDE` (SQLState `23P01`) y de la `UNIQUE` (`23505`) se traduce en el servicio a una excepción de validación → `422` con campo (`horaDesde` o `nombre`). Así el contrato es uniforme aunque el chequeo previo en Java no haya visto la carrera.

### D4. Seed de configuración en el arranque, idempotente, después del de C-02

- El consultorio lo crea el seed de arranque de `C-02` (en Java, porque la contraseña viene de una variable de entorno). Por eso las prestaciones y horarios no pueden ir en la migración SQL: el `consultorio_id` no existe todavía.
- Un `ApplicationRunner` ordenado después del seed de `C-02` crea las 6 prestaciones y los 10 horarios **solo si el consultorio no tiene ni prestaciones ni horarios**. Así un segundo arranque no pisa cambios del odontólogo.
- Alternativa descartada: semilla por migración con `INSERT … SELECT` desde `consultorio`. Falla si `C-02` siembra en Java después de Flyway.

### D5. Frontend: una feature `features/config` con capas puras y vistas finas

```
features/config/
  config-api.ts          # tipos DTO + funciones sobre apiRequest (consultorio, perfil, password)
  agenda-config-api.ts   # prestaciones, horarios, bloqueos
  validators.ts          # funciones puras: duración, rangos, solapamiento, parámetros
  zoned-time.ts          # conversión local de Buenos Aires ↔ instante UTC (Intl, sin librerías)
  form-errors.ts         # ApiError → mensajes por campo / general
  pages/
    config-page.ts       # pestañas + despacho por sección
    consultorio-section.ts, perfil-section.ts, prestaciones-section.ts,
    horarios-section.ts, bloqueos-section.ts
```

- Las vistas reciben sus dependencias (las funciones de API) por parámetro, igual que `createLoginPage`. Así los tests de vista inyectan dobles y no hace falta mockear `fetch`. Los tests de `*-api.ts` sí mockean `fetch`, para fijar método, URL, cuerpo y CSRF.
- **Por qué funciones puras aparte**: la mayor parte de la lógica (validación, zona horaria, solapamiento) se cubre con tests rápidos y deterministas, y la lógica de vista queda en solo armar el DOM y conectar eventos.

### D6. Rutas: una entrada por sección, sin tocar el router

- El router compara el path exacto. Se registran `/configuracion` y `/configuracion/{consultorio|perfil|prestaciones|horarios|bloqueos}` como rutas privadas que renderizan la misma página con la sección indicada.
- Las pestañas son enlaces `data-link`, así que se navega sin recarga, el botón atrás funciona y la URL de cada sección es compartible.
- Alternativa descartada: agregar parámetros o prefijos al router. Cambiaría `web-app-shell` y el riesgo no se justifica.

### D7. Conversión de zona horaria sin librerías

- `zoned-time.ts` convierte un `datetime-local` interpretado en `America/Argentina/Buenos_Aires` a un instante UTC, y al revés.
- Usa `Intl.DateTimeFormat(...).formatToParts` para obtener el offset de la zona en ese instante. Hoy es `-03:00` fijo, pero no se hardcodea, así no hay que tocar código si vuelve el horario de verano.
- La zona se toma de una constante única, en línea con la regla dura #7.
- Los tests corren con `TZ` del proceso distinto de Buenos Aires para probar que no depende de la zona del navegador.

### D8. Granularidad en el cliente

- La sección Prestaciones (y Horarios) obtiene `granularidadMin` con `GET /consultorio` al montarse, para validar.
- Si esa llamada falla, la validación de cliente cae a lo mínimo (entero positivo) y deja que el `422` del servidor decida. La validación de cliente nunca es la autoridad.

### D9. Mocks mientras no hay backend

- Los tests de Vitest usan dobles inyectados (D5) y `fetch` mockeado. No se agrega MSW ni ninguna otra dependencia.
- Para probar en el navegador antes de `C-02`, se puede usar un modo opcional de desarrollo con `VITE_CONFIG_MOCKS=true`, que inyecta implementaciones en memoria de las funciones de API. Ese modo nunca entra en el build de producción, porque `import.meta.env.DEV` lo elimina por tree-shaking.

## Risks / Trade-offs

- [`C-02` fija un contrato de consultorio o perfil distinto al de D1] → Los adaptadores están aislados en `config-api.ts`. Antes de aplicar se revisa el `design.md` de `C-02` y se ajusta. Si `C-02` ya está mergeado, ese contrato manda.
- [Número de migración en conflicto con otros carriles] → Se confirma el siguiente número libre en `main` al momento de `/opsx:apply`. Flyway falla rápido ante duplicados, así que el error no pasa desapercibido.
- [Expresión `tsrange` sobre `time` difícil de mantener] → Se documenta en un comentario de la migración, y un test de integración cubre solapamiento, contigüidad y otro día.
- [Bajar la granularidad deja prestaciones "no múltiplo"] → Se acepta por diseño (no retroactivo). `C-07` debe tolerarlo al calcular disponibilidad. Se avisa a P1.
- [Mock dev-mode filtrado al build] → Se condiciona a `import.meta.env.DEV` y un test de build verifica su ausencia.
- [Desfase de zona en fechas límite] → Los tests de `zoned-time` cubren medianoche y cambio de día.

## Migration Plan

- Se despliega con `C-02` ya en `main`: `V4` se aplica sobre `V3`, y el seed corre al arrancar.
- Rollback en dev o demo: recrear la base. No hay datos reales, por la regla dura #12. Además, `V4` incluye como comentario el `DROP TABLE` inverso.

## Open Questions

- Texto exacto de los mensajes de error del servidor: lo define el backend. El frontend muestra el `message` recibido, así que no cambia la implementación.
