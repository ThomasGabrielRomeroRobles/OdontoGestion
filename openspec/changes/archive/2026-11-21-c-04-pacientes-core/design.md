# Design

## Context

- Lo que dejó `C-02` en `main`:
  - capas `domain`, `application`, `api` e `infrastructure`, con la convención de paquetes de su design (D1): los repositorios Spring Data viven en `domain.<feature>` y los services devuelven records de aplicación;
  - `AuditableEntity` (id `BIGINT` identity, `creado_en`/`actualizado_en` con el `Clock` UTC) y `ConsultorioActual.id()`;
  - `Emails.normalizar` en `domain.usuario`, y excepciones de dominio (`ConflictoException` → 409, `RecursoNoEncontradoException` → 404, `ValidacionDominioException` → 422) que traduce el `GlobalExceptionHandler`;
  - Bean Validation → `400` con `errors[{field, message}]`;
  - en la seguridad, `/api/**` exige sesión y las escrituras exigen CSRF.
- Migraciones en `main`: V1–V3. El plan reserva **V4 para `C-06`** (todavía en su rama) y **V5 para `C-04`**. `C-03` usa V6/V7 en su rama.
- `GlobalExceptionHandler.handleInesperado` loguea la excepción completa. Ante una violación de unicidad, el mensaje de PostgreSQL incluye el valor de la clave (`Key (consultorio_id, dni)=(1, 30123456)`), así que el duplicado **no puede** llegar a ese handler.
- Las ramas abiertas de `C-03` importan `domain.usuario.Emails`, así que moverlo de paquete generaría conflictos de merge.
- Motivación y alcance: ver `proposal.md`. Comportamiento: ver `specs/paciente-registry/spec.md`.

## Goals / Non-Goals

**Goals:**
- Dejar a `C-07` y `C-09` un contrato de dominio estable: la entidad `Paciente` y `PacienteService.buscarOCrearPorDni`.
- Garantizar la unicidad del DNI con la base, no solo con un chequeo en Java, y sin filtrar el DNI a logs ni respuestas.
- Hacer que `buscarOCrearPorDni` sea idempotente ante concurrencia y no rompa la transacción de quien lo llama.

**Non-Goals:**
- Búsqueda que ignore acentos (`unaccent`) o difusa (`pg_trgm`).
- Paginación de la búsqueda. Hay un tope fijo de 20 resultados.
- Cambiar `Emails`, `GlobalExceptionHandler` o la seguridad.

## Decisions

### D1. Paquetes y clases

| Capa | Paquete | Clases |
|---|---|---|
| domain | `domain.paciente` | `Paciente` (entidad), `PacienteRepository`, `Dni` (normalización y validación) |
| application | `application.paciente` | `PacienteService`, `DatosPaciente` (record de entrada), `PacienteVista` (record de salida), `ResultadoBuscarOCrear` (`PacienteVista paciente, boolean creado`) |
| api | `api.paciente` | `PacienteController`, `PacienteRequest`, `PacienteResponse` |

- Se sigue la convención de `C-02` sin cambios y ninguna entidad cruza a `api` (RD-2).
- `Emails.normalizar` se reutiliza desde `domain.usuario`. *Alternativa descartada*: moverlo a `domain.comun`, que es más limpio pero choca con las ramas abiertas. Si se quiere mover, se hace después del merge de `C-03`.

### D2. Esquema: `V5__paciente.sql`

```sql
CREATE TABLE paciente (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultorio_id     bigint      NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
    nombre             text        NOT NULL CHECK (char_length(nombre)   BETWEEN 1 AND 80),
    apellido           text        NOT NULL CHECK (char_length(apellido) BETWEEN 1 AND 80),
    dni                text        NOT NULL CHECK (dni ~ '^[0-9]{7,8}$'),
    fecha_nacimiento   date        NULL,
    telefono           text        NOT NULL CHECK (char_length(telefono) BETWEEN 1 AND 40),
    email              citext      NOT NULL CHECK (char_length(email)    BETWEEN 3 AND 254),
    direccion          text        NOT NULL DEFAULT '',
    cantidad_ausencias integer     NOT NULL DEFAULT 0 CHECK (cantidad_ausencias >= 0),
    activo             boolean     NOT NULL DEFAULT true,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_paciente_consultorio_dni UNIQUE (consultorio_id, dni)
);
CREATE INDEX ix_paciente_consultorio_email            ON paciente (consultorio_id, email);
CREATE INDEX ix_paciente_consultorio_apellido_nombre  ON paciente (consultorio_id, apellido, nombre);
```

- **Índice por DNI**: lo cubre el índice único `(consultorio_id, dni)`, porque toda búsqueda filtra por consultorio. También sirve para el prefijo de DNI con `LIKE '3012%'`.
- **Índice por FK**: `consultorio_id` queda indexado como primera columna de los tres índices, así que no hace falta uno propio (postgresql-table-design).
- **`fecha_nacimiento` es `date`, no `timestamptz`**: es una fecha civil, no un instante, y RD-7 se refiere a instantes. Los timestamps de auditoría sí son `timestamptz`.
- **Opcionales**: `direccion` se guarda como `''` y no como `NULL`, igual que en `consultorio`/`usuario` de V2, para no tener tres estados. `fecha_nacimiento` sí admite `NULL`, porque no hay un valor vacío razonable para una fecha.
- **Comentario de rollback**: va en la cabecera, igual que en V2 y V3 (`DROP TABLE paciente;` y borrar la fila `5` del historial, solo en desarrollo).
- *Alternativa descartada*: `dni` como `bigint`. Se pierde la validación de largo y en la UI es un identificador textual.

### D3. Normalización y validación del DNI y del email

- `Dni.normalizar(String)` quita puntos, espacios y guiones, y exige `^[0-9]{7,8}$`. Si no cumple, lanza `ValidacionDominioException("dni", …)`, que da `422`. Es la red de seguridad del dominio para quien llame al servicio sin pasar por la API (como `C-09`).
- La API valida el formato antes, con Bean Validation, y responde `400` (convención de `C-02`: el formato da 400 y las reglas de negocio dan 422). El patrón de `PacienteRequest.dni` acepta 7 u 8 dígitos con puntos de miles opcionales: `^[0-9]{1,2}\.?[0-9]{3}\.?[0-9]{3}$`.
- El email se guarda y se busca con `Emails.normalizar` (`trim` + minúsculas). `citext` garantiza la comparación sin distinguir mayúsculas.
- Nombre, apellido, teléfono y dirección se guardan con `trim`.

### D4. Entidad `Paciente`

- Hereda de `AuditableEntity`. Mapea `consultorio` como `@ManyToOne(LAZY, optional = false, updatable = false)`, igual que `Usuario`.
- No tiene setters públicos:
  - `Paciente.nuevo(consultorio, DatosPaciente)` normaliza y crea el paciente;
  - `actualizarDatos(DatosPaciente)` reemplaza los datos administrativos.
- `cantidadAusencias` y `activo` solo tienen getters. Sus métodos de cambio (`registrarAusencia`, `revertirAusencia`, `desactivar`) los agregan `C-08` y la futura baja lógica, cuando tengan un caso de uso con test.

### D5. 🔑 `buscarOCrearPorDni`: `INSERT … ON CONFLICT DO NOTHING`

`PacienteService.buscarOCrearPorDni(DatosPaciente)` se anota `@Transactional`, con propagación `REQUIRED`, para sumarse a la transacción de `C-07`/`C-09`:

1. Normaliza DNI y email (D3).
2. Busca con `findByConsultorioIdAndDni`. Si existe, devuelve `(vista, creado = false)` **sin tocar sus datos** (RN-PU-04).
3. Si no existe, ejecuta una query nativa en el repositorio:
   `INSERT INTO paciente (...) VALUES (...) ON CONFLICT (consultorio_id, dni) DO NOTHING RETURNING id`.
   `creado_en`/`actualizado_en` se pasan como parámetros desde el `Clock` de la aplicación, porque la auditoría de JPA no corre en una query nativa.
4. Si `RETURNING` devolvió un id, responde `creado = true`. Si no devolvió nada, otra transacción insertó el mismo DNI y se confirmó: se vuelve a leer por DNI y responde `creado = false`.

Por qué así:
- Bajo `READ COMMITTED`, si otra transacción tiene pendiente el mismo DNI, el `INSERT` espera a que termine. Si la otra se confirma, `DO NOTHING` no lanza error, y la lectura siguiente (una sentencia nueva, con un snapshot nuevo) ve la fila. Si la otra hace rollback, el `INSERT` propio inserta.
- **No hay excepción**, así que la transacción de quien llama no queda marcada *rollback-only*. Esto cubre el escenario "Usable dentro de una transacción mayor".

*Alternativas descartadas*:
- `save` + capturar `DataIntegrityViolationException`: la sesión de Hibernate queda inutilizable y la transacción de `C-09` se aborta.
- `REQUIRES_NEW` para el insert: el paciente se confirma aunque falle el turno de `C-09`. Además, con un pool chico puede pedir una segunda conexión mientras la primera sigue abierta.
- Lock con `SELECT … FOR UPDATE`: no se puede bloquear una fila que todavía no existe.

### D6. Alta y edición por el odontólogo: 409 sin fugas

- `crear(DatosPaciente)`:
  1. normaliza;
  2. si ya existe el DNI en el consultorio, lanza `ConflictoException("Ya existe un paciente con ese DNI.")`, sin el valor del DNI;
  3. si no, hace `save` + `flush`.
- `actualizar(id, DatosPaciente)`:
  1. busca el paciente por `id` **y** `consultorio_id` (si no está, `RecursoNoEncontradoException` → 404, también para otro consultorio);
  2. si el DNI nuevo pertenece a **otro** paciente, lanza 409;
  3. si no, `actualizarDatos` + `flush`.
- **Carrera**: entre el chequeo y el `flush` puede entrar otro alta con el mismo DNI. El `flush` va dentro de un `try` que captura `DataIntegrityViolationException`; si la causa es la restricción `uq_paciente_consultorio_dni`, la traduce a `ConflictoException`. Cualquier otra causa se relanza.
  - El 409 sale por `ConflictoException`, que es una `RuntimeException`, así que la transacción hace rollback normalmente.
  - Lo importante es que el mensaje de PostgreSQL, que contiene el DNI, **nunca** llegue a `handleInesperado`, que loguearía el stack trace.
- *Alternativa descartada*: usar también `ON CONFLICT` en el alta. Funciona, pero el alta del odontólogo sí debe fallar ante un duplicado, y `save` mantiene la auditoría de JPA.

### D7. Búsqueda

`buscar(String dni, String q)` devuelve una `List<PacienteVista>`, como máximo 20, ordenada por `apellido, nombre, id`. El `id` hace el orden determinista.

- **Con `dni`**: se normaliza. Si el formato es inválido, la API responde `400` (campo `dni`). Busca coincidencia exacta en el consultorio. Si llegan `dni` y `q` juntos, **`dni` tiene prioridad** y `q` se ignora.
- **Con `q`**: se aplica `trim`, largo máximo 100 (si no, `400`). Si queda vacío, se trata como "sin parámetros". La consulta JPQL filtra por `consultorio.id = :cid` y por alguna de estas condiciones:
  - `lower(apellido) LIKE :patron`
  - `lower(nombre) LIKE :patron`
  - `lower(email) LIKE :patron`
  - `dni LIKE :prefijo`

  Con `patron = '%' + escape(lower(q)) + '%'` y `prefijo = escape(soloDigitos(q)) + '%'`; si `q` no tiene dígitos, la condición de DNI no se agrega. `escape` neutraliza `%`, `_` y `\`, y los valores siempre van como parámetros, nunca concatenados.
- **Sin parámetros**: los primeros 20 del consultorio.
- El tope se aplica con `Limit.of(20)` o `PageRequest.of(0, 20)` del repositorio, y la lista resultante se devuelve sin metadatos de página.
- *Alternativa descartada*: `Page<>` con `page`/`size`. Agrega contrato y tests sin necesidad: un consultorio de la demo tiene decenas de pacientes y el buscador se usa para encontrar uno.

### D8. Contratos REST

| Método y ruta | Request | Respuesta OK | Errores |
|---|---|---|---|
| `GET /api/v1/pacientes?dni=&q=` | — | `200 [PacienteResponse]` (≤ 20) | `400` (`dni`/`q` inválidos), `401` |
| `GET /api/v1/pacientes/{id}` | — | `200 PacienteResponse` | `401`, `404` |
| `POST /api/v1/pacientes` | `PacienteRequest` | `201 PacienteResponse` + `Location: /api/v1/pacientes/{id}` | `400`, `401`, `403`, `409` |
| `PUT /api/v1/pacientes/{id}` | `PacienteRequest` | `200 PacienteResponse` | `400`, `401`, `403`, `404`, `409` |

- **`PacienteResponse`**: `{id, nombre, apellido, dni, fechaNacimiento, telefono, email, direccion, cantidadAusencias, activo}`.
  - `fechaNacimiento` va como `"yyyy-MM-dd"` o `null`; el DNI, normalizado.
  - Se expone `id` porque el frontend y `C-05` lo usan en las rutas.
- **`PacienteRequest`** (Bean Validation → `400`). Si llegan propiedades desconocidas como `cantidadAusencias` o `activo`, Jackson las ignora, igual que `zonaHoraria` en `C-02`.

  | Campo | Validación |
  |---|---|
  | `nombre`, `apellido` | `@NotBlank @Size(max = 80)` |
  | `dni` | `@NotBlank @Pattern` (D3) |
  | `telefono` | `@NotBlank @Size(max = 40)` |
  | `email` | `@NotBlank @Email @Size(max = 254)` |
  | `fechaNacimiento` | `LocalDate`, opcional, `@PastOrPresent` |
  | `direccion` | opcional, `@Size(max = 200)`; `null` se guarda como `''` |

- **`Location`** relativo (`/api/v1/pacientes/{id}`). Los mensajes de validación van en español, como en `ConsultorioRequest`.
- `PacienteController` solo traduce entre la API y el servicio, sin lógica ni `@Transactional` (RD-3).

### D9. Datos personales fuera de los logs

- Ningún `log` de `paciente` incluye DNI, email ni teléfono. No hace falta loguear nada en el camino feliz.
- El `detail` del 409 es fijo y no lleva el valor.
- La traducción de D6 evita que el mensaje de PostgreSQL llegue al log de `handleInesperado`.
- Se verifica con `OutputCaptureExtension` en el IT del duplicado.

### D10. Estrategia de pruebas (TDD estricto)

- **Unitarios puros**: `Dni.normalizar` (con puntos, espacios, 7 y 8 dígitos, 6 y 9 dígitos, letras, `null`) y la construcción y edición de `Paciente` (trim, email normalizado, opcionales vacíos).
- **Unitarios Mockito de `PacienteService`**: 409 por DNI existente en el alta; 409 al cambiar a un DNI ajeno; mismo DNI propio permitido; 404 en la edición; `buscarOCrearPorDni` existente devuelve `creado = false` sin escribir; traducción de `DataIntegrityViolationException` con la restricción correcta, y relanzamiento con otra restricción.
- **Persistencia** (`AbstractIntegrationTest`, PostgreSQL real), en un `FlywayV5MigrationIT`:
  - V5 aplicada, columnas y tipos (`timestamptz`, `date`, `citext`), FK y `NOT NULL` de `consultorio_id`;
  - unicidad `(consultorio_id, dni)`, `CHECK` del DNI y de `cantidad_ausencias >= 0`, defaults (`0`, `true`, `''`).
  
  Además, un `PacienteRepositoryIT` con auditoría por reloj fijo, la búsqueda (patrón, prefijo de DNI, escape de `%`, orden, tope 20, aislamiento por consultorio) y el `INSERT … ON CONFLICT` (`RETURNING` vacío ante un duplicado).
- **Concurrencia** (`PacienteConcurrenciaIT`):
  - dos hilos con `CountDownLatch` invocan `buscarOCrearPorDni` con el mismo DNI nuevo; se verifica un solo paciente y el mismo id en ambos;
  - un `TransactionTemplate` externo invoca la operación sobre un DNI ya confirmado por otra transacción, después escribe algo y hace commit sin error.
- **API** (`PacienteApiIT`, `AbstractWebIntegrationTest`, con la cadena de seguridad real): todos los escenarios HTTP de la spec (201 + `Location`, 400 por campo, 409, 404, `PUT` que ignora campos de solo lectura, búsqueda, 401 sin sesión, 403 sin CSRF) y el log sin datos personales.
- **Higiene**:
  - el contenedor se reutiliza, así que cada test usa DNIs únicos (generados con un contador o un prefijo por clase) y borra sus pacientes en `@AfterEach`;
  - el paciente "de otro consultorio" se crea insertando un segundo consultorio por `JdbcTemplate` y se borra al final;
  - sin `@DirtiesContext` ni `@MockBean` en los ITs;
  - las fechas futuras de los tests son lejanas (`2999-01-01`), porque `@PastOrPresent` usa el reloj del sistema y no el `Clock` fijo de los tests.

## Risks / Trade-offs

- **[Hueco V4 → V5]** Si `C-04` se mergea antes que `C-06`, cualquier base local que ya tenga V5 rechaza después la V4 de `C-06` (Flyway marca la migración como *ignored* y `validate` falla). Las bases nuevas de los tests no tienen el problema. → Mitigación, a coordinar con P1 (ver "Open Questions"): mergear `C-06` primero, o, si `C-04` llega antes, que quien tenga una base local la recree (`docker compose down -v` o `DROP SCHEMA`). Como alternativa, el equipo puede habilitar `spring.flyway.out-of-order=true` solo en el perfil `dev`.
- **[Búsqueda sensible a acentos]** `q=perez` no encuentra a "Pérez". → Se acepta para el MVP: el odontólogo suele buscar por DNI. Si molesta en la demo, `unaccent` es una mejora acotada para después.
- **[`@PastOrPresent` con el reloj del sistema]** La "fecha actual" la decide la JVM, no el `Clock` de la aplicación. La diferencia es como mucho de un día, por zona horaria. → Se acepta. Los tests usan fechas lejanas.
- **[`buscarOCrearPorDni` no actualiza el contacto]** Si un paciente cambió de email, los enlaces de la página pública siguen yendo al email viejo (PA-03 / RN-PU-04). → Es la regla vigente. El odontólogo lo corrige con `PUT`.
- **[Paciente creado aunque falle el turno]** Si `C-09` hace rollback, el paciente nuevo también se revierte, porque comparte la transacción. Esto es lo deseado. En cambio, un paciente creado por un `POST` de la API queda aunque después no se le dé turno. → Se acepta (PA-13, depuración a futuro).
- **[Tope fijo de 20]** Una búsqueda muy amplia no muestra todos los resultados. → El frontend debe sugerir refinar la búsqueda. Si hiciera falta, la paginación se agrega después sin romper el contrato: los parámetros nuevos serían opcionales.

## Migration Plan

1. Agregar `V5__paciente.sql`. Al arrancar, Flyway la aplica sobre V1–V3 (o sobre V1–V4 si `C-06` ya está en `main`). No modifica tablas existentes ni requiere backfill.
2. `ddl-auto=validate` verifica que la entidad `Paciente` coincide con el esquema.
3. **Rollback** (solo desarrollo local): `DROP TABLE paciente;` y borrar la fila `version = '5'` de `flyway_schema_history`. En entornos compartidos no hay rollback manual: se corrige con una migración nueva (database-migrations: forward-only).

## Open Questions

Resueltas el 2026-11-21 con P1 (Thomas Romero):

- **Orden de merge con `C-06`**: `C-06` se mergea **antes** que `C-04`. Así V4 queda aplicada antes que V5 en todas las bases y el aviso de recrear la base local no hace falta. El PR de `C-04` espera a que `C-06` esté en `main`.
- **Firma consumida por `C-07`/`C-09`**: se mantiene `ResultadoBuscarOCrear buscarOCrearPorDni(DatosPaciente)`, que devuelve `PacienteVista`. No se agrega una variante con la entidad.

## Implementación — diferencias con este diseño

- **`DatosPaciente` vive en `domain.paciente`**, no en `application.paciente` (D1): `Paciente.nuevo` lo recibe y el dominio no debe depender de la capa de aplicación (KB 08 §Capas). El servicio lo usa igual.
- **Helper de búsqueda**: el armado de `patron`/`prefijo` con escape (D7) es el record `domain.paciente.BusquedaPaciente`. Sin dígitos en `q`, el prefijo es `''`, que no coincide con ningún DNI por el `CHECK` de V5, y así se evita un parámetro nulo en JPQL.
- **Email con espacios alrededor**: `@Email` los rechaza, así que `PacienteRequest` recorta el email en su constructor compacto antes de que se valide. La normalización completa sigue en el dominio.
- **Filtros de búsqueda**: se reciben como `@Valid @ModelAttribute BusquedaPacienteRequest` para que un `dni`/`q` inválido produzca el mismo `400` con `errors[{field}]` (por `MethodArgumentNotValidException`) sin tocar `GlobalExceptionHandler`.
- **`buscarOCrearPorDni`** construye un `Paciente` sin persistir solo para normalizar y validar con las mismas reglas del alta, antes del `INSERT … ON CONFLICT`.
- **`FlywayV1MigrationIT.elHistorialRegistraLasVersionesAplicadasEnOrden`** (de C-01/C-02) exigía exactamente `1, 2, 3`. Se cambió a "contiene `1, 2, 3, 5` en orden ascendente", para que cada change no tenga que reescribir esa línea (y C-06/C-03 no choquen al mergear). Si el equipo adopta `out-of-order`, ese orden puede romperse y habrá que revisar la aserción.
- **Renumeración V5 → V8 (2026-11-22)**: C-03 entró a `main` con V6/V7 antes que C-04. Para que las bases locales que ya aplicaron V7 no rechacen una V5 posterior, la migración pasó a `V8__paciente.sql` (y `FlywayV5MigrationIT` → `FlywayV8MigrationIT`). Las referencias a "V5" de este change archivado se refieren a esa migración. C-05 pasa de V8 a V11 (V9 = C-07, V10 = C-10).
