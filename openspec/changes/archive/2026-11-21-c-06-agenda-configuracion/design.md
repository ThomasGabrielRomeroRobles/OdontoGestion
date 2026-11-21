# Design

## Context

- `C-01` dejó listo lo siguiente:
  - El backend en capas `ar.edu.odontogestion.{api,application,domain,infrastructure}`.
  - Flyway `V1` (con `btree_gist` y `citext`), `ProblemDetail` global, `Clock` en UTC y Testcontainers.
  - En el frontend: `shared/http/client.ts` (CSRF en escrituras, `401` → login, `ApiError` tipado con `fieldErrors`), un router de **coincidencia exacta de path** (`routes.find(r => r.path === pathname)`), el layout autenticado con navegación y páginas construidas con nodos DOM (sin `innerHTML`).
- `C-02` (mergeado en `main`) dejó:
  - La migración `V2` (`consultorio`, `usuario`), que ya **inserta el consultorio ficticio** "Consultorio Demo", y `V3` (parámetros del consultorio). El seed Java de C-02 solo crea el odontólogo.
  - `AuditableEntity` (id, `creado_en`, `actualizado_en` con el `Clock`), `ConsultorioActual` y `ConsultorioService.parametrosVigentes()`.
  - `GlobalExceptionHandler`: `400` con `errors` para Bean Validation, `422` con `errors` para `ValidacionDominioException` y `404` para `RecursoNoEncontradoException`.
  - Convenciones: restricciones con nombre (`uq_usuario_email`), opcionales guardados como `''` y nunca `NULL`, y los endpoints `/api/v1/consultorio` y `/api/v1/perfil`.
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
| `GET /bloqueos?desde=&hasta=` (ambos opcionales, ver D12) | — | `200 BloqueoDto[]` por `desde` |
| `POST /bloqueos` · `PUT /bloqueos/{id}` | `{desde, hasta, motivo?}` | `201`/`200 BloqueoDto` |
| `DELETE /bloqueos/{id}` | — | `204` |

- Formas de los DTOs:
  - `PrestacionDto = {id, nombre, duracionMin, activa}`.
  - `HorarioDto = {id, diaSemana (1=lunes … 7=domingo, ISO), horaDesde "HH:mm", horaHasta "HH:mm"}`.
  - `BloqueoDto = {id, desde, hasta, motivo}`, con instantes ISO-8601 en UTC con `Z`. `motivo` siempre es texto: sin motivo viaja `""`, nunca `null` (convención de C-02). En el request, `motivo` puede venir ausente, `null` o `""`, y el backend lo guarda como `""`.
  - En los requests, todos los campos se envían como texto o número JSON. Las fechas y horas de horarios y bloqueos viajan como **texto** y las interpreta el servicio (D11).
- **Por qué `/desactivar` y `/activar` en vez de `PATCH {activa}` o `DELETE`**: el `DELETE` sugiere un borrado que la regla prohíbe, y la acción explícita es más clara para auditar. Es el mismo estilo que `C-07` usa en `/turnos/{id}/cancelar`.
- Errores:
  - `400` con `errors: [{field, message}]` solo si falta un campo obligatorio (`@NotNull`) o el JSON es ilegible.
  - `422` con `errors: [{field, message}]` para toda regla de negocio o de formato: rangos, formatos, tamaños, no vacío, granularidad, duplicados y solapamientos (D11).
  - Un recurso inexistente o de otro consultorio responde `404`.
- **Contrato real de C-02** (implementado en `main`, spec `consultorio-config`), que el frontend consume:
  - `GET /consultorio` → `200 {nombre, direccion, telefono, emailContacto, zonaHoraria, parametros}`.
  - `PUT /consultorio` con `{nombre, direccion, telefono, emailContacto, parametros}` → `200` con la misma forma del `GET`. `zonaHoraria` se muestra y no se edita: el `PUT` la ignora y el frontend no la envía.
  - `parametros` es un objeto anidado con 7 enteros obligatorios: `granularidadMin`, `anticipacionMinReservaMin`, `limiteCancelacionHoras`, `recordatorioHorasAntes`, `vencimientoAvisoMin`, `vencimientoPendienteMin` y `toleranciaAusenteMin`. Valores por defecto: 15, 120, 2, 24, 60, 30 y 15.
  - Errores del `PUT /consultorio`:
    - `400` con `errors: [{field, message}]` si falta un dato o un parámetro, o si el formato es inválido.
    - `422` si un parámetro es cero o negativo. Se informan todos juntos y no se aplica ningún cambio.
    - Los campos de `parametros` llegan con prefijo (p. ej. `parametros.granularidadMin`). El frontend quita el prefijo `parametros.` para mostrar cada error junto a su input (ver D10).
  - `GET/PUT /perfil` con `{email, nombre, apellido, matricula}`. `email` es de solo lectura.
  - `POST /perfil/password` con `{passwordActual, passwordNueva}` → `204`.
  - Si el contrato de `C-02` cambia, se ajusta solo `features/config/*-api.ts` (ver D5).

### D2. Esquema: migración `V4__agenda_configuracion.sql`

Mismas convenciones que `V2`: `bigint GENERATED ALWAYS AS IDENTITY`, `creado_en` / `actualizado_en` (`timestamptz NOT NULL DEFAULT now()`), FK `consultorio_id` con `ON DELETE RESTRICT` e índice, y **todas las restricciones con nombre**, para poder identificarlas al traducir errores (D3).

- **`prestacion`**:
  - Columnas: `id`, `consultorio_id`, `nombre citext NOT NULL`, `duracion_min integer NOT NULL`, `activa boolean NOT NULL DEFAULT true`, `creado_en`, `actualizado_en`.
  - Restricciones:
    - `ck_prestacion_duracion_positiva CHECK (duracion_min > 0)`.
    - `ck_prestacion_nombre_no_vacio CHECK (char_length(nombre) BETWEEN 1 AND 80)`.
    - `uq_prestacion_consultorio_nombre UNIQUE (consultorio_id, nombre)`. Con `citext` la unicidad no distingue mayúsculas sin lógica en Java.
- **`horario_trabajo`**:
  - Columnas: `id`, `consultorio_id`, `dia_semana smallint NOT NULL`, `hora_desde time NOT NULL`, `hora_hasta time NOT NULL`, `creado_en`, `actualizado_en`.
  - Restricciones:
    - `ck_horario_dia_semana CHECK (dia_semana BETWEEN 1 AND 7)`.
    - `ck_horario_rango CHECK (hora_desde < hora_hasta)`.
    - `ex_horario_trabajo_sin_solapamiento EXCLUDE USING gist (consultorio_id WITH =, dia_semana WITH =, tsrange('2000-01-01'::date + hora_desde, '2000-01-01'::date + hora_hasta, '[)') WITH &&)`.
  - **Por qué el `EXCLUDE`**: la regla dura #8 pide garantías en la base, y dos altas concurrentes no deben poder colarse. La fecha ancla convierte `time` en un rango indexable con `btree_gist`, que ya instaló `V1`. Alternativa descartada: un chequeo solo en Java, vulnerable a carreras.
- **`bloqueo_agenda`**:
  - Columnas: `id`, `consultorio_id`, `desde timestamptz NOT NULL`, `hasta timestamptz NOT NULL`, `motivo text NOT NULL DEFAULT ''`, `creado_en`, `actualizado_en`.
  - Restricciones: `ck_bloqueo_rango CHECK (desde < hasta)` y `ck_bloqueo_motivo_largo CHECK (char_length(motivo) <= 200)`.
  - Índice: `ix_bloqueo_agenda_rango gist (consultorio_id, tstzrange(desde, hasta, '[)'))`, para las consultas por rango de `C-07`.
  - Los bloqueos **sí** pueden solaparse entre sí; eso no es un error.
- **Seed al final de `V4`** (D4).
- **Número de versión**: `V4`, porque `C-02` ocupa `V2` y `V3` en `main`. P3 (`C-03`) y P4 (`C-04`) toman los siguientes libres.

**Mapeo JPA, para que Hibernate pase `ddl-auto=validate`**:
- Las tres entidades extienden `AuditableEntity` (`creado_en` / `actualizado_en`).
- La relación con el consultorio es `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "consultorio_id", nullable = false, updatable = false) Consultorio consultorio`, igual que `Usuario`.
- `nombre`: `@Column(nullable = false, columnDefinition = "citext") String`, como `Usuario.email`.
- `dia_semana`: `@Column(name = "dia_semana", nullable = false) short`. `short` mapea a `smallint`; un `int` o `Integer` haría fallar la validación (`int2` vs `int4`).
- `hora_desde` / `hora_hasta`: `LocalTime` → `time`, con `@JdbcType(LocalTimeJdbcType.class)`. Así la hora local viaja tal cual al driver. El mapeo por defecto pasa por `java.sql.Time` con la zona de la JVM y, con `hibernate.jdbc.time_zone=UTC` (C-02), corría las horas en JVMs que no están en UTC: en Windows en Argentina, 09:00 se guardaba como 12:00. Un IT fija la zona de la JVM para cubrirlo. `desde` / `hasta`: `Instant` → `timestamptz`, igual que las fechas de auditoría.
- `duracion_min`: `int` → `integer`. `activa`: `boolean`. `motivo`: `String` → `text`.

### D3. Reglas en el servicio, contra los parámetros vigentes, y traducción de restricciones

- **Granularidad**: el servicio la lee en cada operación con `ConsultorioService.parametrosVigentes().granularidadMin()`, sin cachear. La base solo garantiza `duracion_min > 0`. Que la duración sea múltiplo de la granularidad (y que las horas de un horario estén alineadas a ella) lo valida el servicio al **crear o editar**.
  - Por qué: la granularidad es configurable y sus cambios no son retroactivos (US-013). Un `CHECK` no puede depender de otra tabla, y un trigger sería opaco para el equipo.
- **Desactivar o activar una prestación no vuelve a validar la duración.** Si la granularidad cambió después del alta, una prestación "no múltiplo" igual se puede desactivar o reactivar. La validación aplica solo cuando se cambian `nombre` o `duracionMin`.
- **Duplicados y solapamientos**: el servicio guarda con `saveAndFlush` dentro de un `try`, para que la restricción se evalúe en ese momento y no al commit. Si salta `DataIntegrityViolationException`, identifica la restricción por su nombre y lanza `ValidacionDominioException`, que se traduce a `422` con campo:
  - `uq_prestacion_consultorio_nombre` → campo `nombre`.
  - `ex_horario_trabajo_sin_solapamiento` → campo `horaDesde`.
  - Cualquier otra restricción se relanza tal cual (es un defecto, no un error del usuario).
  - Así el contrato es uniforme aunque el chequeo previo en Java no haya visto una carrera.
- El chequeo previo en Java (nombre existente, franja solapada) se mantiene igual, para dar el mensaje sin depender de la excepción en el caso común. La base es la garantía.

### D4. Seed de prestaciones y horarios al final de `V4`

- `V2` ya inserta el consultorio en la migración, así que `V4` puede sembrar con `INSERT … SELECT` desde `consultorio`:
  - Las 6 prestaciones ficticias: Consulta 30, Limpieza 45, Obturación 60, Endodoncia 90, Extracción 45 y Control 15.
  - Los 10 horarios: lunes a viernes, 09:00-13:00 y 14:00-18:00.
- Flyway aplica `V4` una sola vez, así que un segundo arranque no pisa ni duplica cambios del odontólogo. No hace falta un runner Java ni una lógica de "solo si está vacío".
- Alternativa descartada: un `ApplicationRunner` en Java. Tiene más piezas móviles (orden respecto del seed de C-02, idempotencia a mano) sin ningún beneficio, porque el consultorio ya existe al migrar.

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

- La sección Prestaciones obtiene `parametros.granularidadMin` con `GET /consultorio` al montarse, para validar.
- Si esa llamada falla, la validación de cliente cae a lo mínimo (entero positivo) y deja que el `422` del servidor decida. La validación de cliente nunca es la autoridad.

### D9. Mocks mientras no hay backend

- Los tests de Vitest usan dobles inyectados (D5) y `fetch` mockeado. No se agrega MSW ni ninguna otra dependencia.
- Para probar en el navegador antes de `C-02`, se puede usar un modo opcional de desarrollo con `VITE_CONFIG_MOCKS=true`, que inyecta implementaciones en memoria de las funciones de API. Ese modo nunca entra en el build de producción, porque `import.meta.env.DEV` lo elimina por tree-shaking.

### D10. Errores de campos anidados en `parametros`

- C-02 informa los errores de los parámetros con la ruta completa del campo (`parametros.granularidadMin`), mientras que el formulario identifica cada input por el nombre plano (`granularidadMin`).
- La sección Consultorio quita el prefijo `parametros.` antes de ubicar cada error. Así el mensaje aparece junto a su input y no termina en la alerta general.
- La normalización vive en `form-errors.ts` como función pura, y la sección la usa al mostrar errores de la API. El resto de las secciones no tiene campos anidados y no la usa.
- Alternativa descartada: nombrar los inputs `parametros.granularidadMin`. Acopla el DOM a la forma del JSON y complica los selectores.

### D11. DTOs solo con `@NotNull`; toda regla de negocio en el servicio (422)

- Los records de request solo llevan `@NotNull` (mensaje "Es obligatorio."), como C-02. Si falta un campo obligatorio → `400` con su campo.
- Todo lo demás se valida en el servicio, que reúne los errores de la solicitud y lanza una sola `ValidacionDominioException` → `422` con un error por campo:
  - **Formatos**: horas `HH:mm` e instantes ISO-8601 **con zona** u offset explícitos.
  - **Rangos**: `diaSemana` entre 1 y 7, `desde < hasta`, `horaDesde < horaHasta`.
  - **Tamaños**: nombre de hasta 80 caracteres y motivo de hasta 200.
  - **No vacío**: nombre sin espacios sobrantes.
  - **Duración**: positiva y múltiplo de la granularidad vigente.
- **Fechas y horas como `String` en los requests.** Si fueran `Instant` o `LocalTime`, Jackson rechazaría un valor mal formado al deserializar, Spring respondería `400` ("JSON ilegible") sin campo, y el formulario no podría mostrar el error junto al input. Con `String`, el servicio parsea y responde `422` en `desde`, `hasta`, `horaDesde` u `horaHasta`. Lo mismo vale para los filtros `desde` / `hasta` de `GET /bloqueos`.
- Los instantes se parsean con `OffsetDateTime.parse` (acepta `Z` y offsets) y se guardan como `Instant` UTC. Un valor sin zona, como `2026-12-24T00:00:00`, es inválido.
- Los records de request declaran `toString()` sin datos solo donde haga falta. Acá no hay secretos ni datos clínicos (regla dura #10).

### D12. Bloqueos: `motivo` y filtros de `GET /bloqueos`

- **`motivo`**: opcional. Se recorta y, si es `null` o vacío, se guarda `""` (columna `NOT NULL DEFAULT ''`). La respuesta siempre trae un texto, nunca `null`.
- **Filtros de `GET /bloqueos`**: `desde` y `hasta` son **independientes y opcionales**. El resultado son los bloqueos que se intersecan con el intervalo `[inicio, fin)`:

  | Parámetros | `inicio` | `fin` |
  |---|---|---|
  | ninguno | ahora (`Clock`) | sin límite |
  | solo `desde` | `desde` | sin límite |
  | solo `hasta` | ahora (`Clock`) | `hasta` |
  | ambos | `desde` | `hasta` |

  - Un bloqueo se interseca si `bloqueo.hasta > inicio` y, cuando hay `fin`, `bloqueo.desde < fin`.
  - Siempre ordenados por `desde`.
- **Errores de los filtros** (422): un filtro con formato inválido da error en su campo (`desde` o `hasta`). Con ambos presentes, si `hasta <= desde`, el error va en `hasta`.
- **Por qué "solo `hasta`" usa "ahora" como inicio**: mantiene la semántica de la llamada sin filtros (vigentes y futuros) y evita devolver todo el historial por accidente. Quien quiera bloqueos pasados manda `desde` explícito.

## Risks / Trade-offs

- [`C-02` fija un contrato de consultorio o perfil distinto al de D1] → Los adaptadores están aislados en `config-api.ts`. Antes de aplicar se revisa el `design.md` de `C-02` y se ajusta. Si `C-02` ya está mergeado, ese contrato manda.
- [Número de migración en conflicto con otros carriles] → Se confirma el siguiente número libre en `main` al momento de `/opsx:apply`. Flyway falla rápido ante duplicados, así que el error no pasa desapercibido.
- [Expresión `tsrange` sobre `time` difícil de mantener] → Se documenta en un comentario de la migración, y un test de integración cubre solapamiento, contigüidad y otro día.
- [El frontend trata `motivo === null` como "sin motivo", pero el backend devuelve `""` (D12)] → Se ajusta en la verificación integral (grupo 10): `bloqueos-section.ts` y los mocks deben tratar `""` como sin motivo.
- [El nombre de la restricción no aparece en la excepción del driver] → El traductor revisa toda la cadena de causas (nombre de restricción de Hibernate y mensaje SQL). Un IT por restricción verifica el `422` real contra PostgreSQL.
- [Bajar la granularidad deja prestaciones "no múltiplo"] → Se acepta por diseño (no retroactivo). `C-07` debe tolerarlo al calcular disponibilidad. Se avisa a P1.
- [Mock dev-mode filtrado al build] → Se condiciona a `import.meta.env.DEV` y un test de build verifica su ausencia.
- [Desfase de zona en fechas límite] → Los tests de `zoned-time` cubren medianoche y cambio de día.

## Migration Plan

- Se despliega con `C-02` ya en `main`: `V4` se aplica sobre `V3` y deja sembradas las prestaciones y los horarios en la misma migración.
- Rollback en dev o demo: recrear la base. No hay datos reales, por la regla dura #12. Además, `V4` incluye como comentario el `DROP TABLE` inverso.

## Open Questions

- Texto exacto de los mensajes de error del servidor: lo define el backend. El frontend muestra el `message` recibido, así que no cambia la implementación.
