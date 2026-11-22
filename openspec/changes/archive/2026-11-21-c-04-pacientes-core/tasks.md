# Tasks

> **TDD estricto** (CONTRIBUTING §3.3): en cada par **RED → GREEN** se escribe primero el test, se ejecuta y se confirma que falla por la razón esperada. Recién después se implementa lo mínimo para que pase, se triangula (al menos 2 casos por comportamiento) y se refactoriza con los tests en verde. Entre corchetes, los escenarios de spec que cubre cada tarea (`capability › escenario`).
>
> **Governance MEDIO**: implementar por pasos, mostrando las decisiones no obvias (marcadas 🔑 en `design.md`).
>
> **Reparto**: todo es de **P4 (Mauro Mercado)**. La firma de `buscarOCrearPorDni` (grupo 4) se comparte con **P1 (Thomas Romero)** antes de cerrar el change.
>
> **Higiene de tests** (design D10): el contenedor se reutiliza, así que cada test usa DNIs únicos y borra sus pacientes en `@AfterEach`; sin `@DirtiesContext` ni `@MockBean` en los ITs. Verificación por defecto: `cd backend; .\gradlew.bat test`.

## 1. Esquema `V5__paciente.sql`

- [x] 1.1 RED: crear `FlywayV5MigrationIT` (`AbstractIntegrationTest` + `JdbcTemplate`) y confirmar que falla porque no existe la tabla. El test verifica:
  - la versión `5` aplicada con éxito;
  - la tabla `paciente` con `consultorio_id` no nulo y FK a `consultorio`, `creado_en`/`actualizado_en` `timestamptz` no nulos, `fecha_nacimiento` `date` nullable y `email` `citext`;
  - en una inserción mínima, los defaults `cantidad_ausencias = 0`, `activo = true` y `direccion = ''`;
  - que la base rechaza: `consultorio_id` nulo, un DNI duplicado en el mismo consultorio, los DNI `123456`, `123456789` y `12A45678`, y `cantidad_ausencias = -1`;
  - que acepta el mismo DNI en **otro** consultorio (insertado y borrado por el test).

  [database-schema-management › Versión de C-04 aplicada, Paciente referencia al consultorio, Timestamps de paciente en UTC; paciente-registry › Paciente sin consultorio rechazado por la base, Unicidad garantizada por la base]
- [x] 1.2 GREEN: crear `backend/src/main/resources/db/migration/V5__paciente.sql` según design D2, con comentario de rollback en la cabecera. Verificar que 1.1 pasa y que `FlywayV1/V2/V3MigrationIT` siguen en verde.

## 2. Dominio: `Dni` y entidad `Paciente`

- [x] 2.1 RED → GREEN: unit test de `domain.paciente.Dni.normalizar` y luego su implementación (design D3). Verificar que pasa. Casos:
  - `30.123.456` → `30123456`;
  - ` 7 123 456 ` → `7123456`;
  - `30-123-456` → `30123456`;
  - `123456`, `123456789`, `12AB` y `null` → `ValidacionDominioException` con campo `dni`.

  [paciente-registry › DNI con puntos normalizado (regla de dominio)]
- [x] 2.2 RED: unit test de `Paciente.nuevo(consultorio, DatosPaciente)` y `actualizarDatos`. Confirmar que falla porque las clases no existen. Verifica:
  - `trim` de nombre, apellido, teléfono y dirección;
  - email normalizado con `Emails.normalizar`;
  - DNI normalizado;
  - `direccion` nula → `''` y `fechaNacimiento` nula conservada;
  - `cantidadAusencias = 0` y `activo = true` al crear;
  - `actualizarDatos` no cambia `cantidadAusencias` ni `activo`.
- [x] 2.3 GREEN: crear `application.paciente.DatosPaciente` (record) y `domain.paciente.Paciente` (hereda de `AuditableEntity`; `consultorio` `@ManyToOne(LAZY, optional = false)` no actualizable; `email` `columnDefinition = "citext"`; sin setters públicos), según design D4. Verificar que 2.2 pasa.
- [x] 2.4 RED → GREEN: `PacienteRepositoryIT` (`AbstractIntegrationTest` + `FixedClockTestConfig`). Guardar un `Paciente` del consultorio actual y verificar `creadoEn`/`actualizadoEn` con el reloj fijo, `findByConsultorioIdAndDni` y `findByIdAndConsultorioId` (vacío para el id de otro consultorio). Crear `PacienteRepository`. Verificar que pasa y que el arranque con `ddl-auto=validate` sigue en verde (`ApplicationContextIT`). [paciente-registry › Alta vinculada al consultorio actual (persistencia), Paciente de otro consultorio (persistencia)]

## 3. Búsqueda en el repositorio

- [x] 3.1 RED: casos de búsqueda en `PacienteRepositoryIT`, con DNIs únicos por test. Confirmar que fallan porque la consulta no existe. Casos:
  - `q=pér` encuentra a "Ana Pérez" y no a "Luis Gómez";
  - `q=PÉREZ`, sin distinguir mayúsculas;
  - búsqueda por email parcial;
  - prefijo de DNI `3012`;
  - `q=%` no devuelve todo (escape de `%`, `_` y `\`);
  - 25 coincidencias → 20, ordenadas por apellido, nombre e id;
  - un paciente de otro consultorio con el mismo apellido no aparece.

  [paciente-registry › Búsqueda por apellido sin distinguir mayúsculas, Búsqueda por prefijo de DNI, Orden y tope de resultados, Solo pacientes del consultorio actual]
- [x] 3.2 GREEN: implementar la consulta JPQL con parámetros y el tope de 20 (`Limit`/`PageRequest`) según design D7. El armado de `patron`/`prefijo` con escape va en un helper del servicio o del dominio, con su propio unit test. Verificar que 3.1 pasa.

## 4. Servicio `PacienteService` (incluye el contrato para `C-07`/`C-09`)

- [x] 4.1 RED: unit tests (Mockito) de `PacienteService`. Confirmar que fallan. Casos:
  - `crear` con un DNI existente → `ConflictoException` cuyo mensaje no contiene el DNI, y no se llama a `save`;
  - `crear` válido → `PacienteVista` con `cantidadAusencias 0` y `activo true`;
  - `actualizar` con un id inexistente → `RecursoNoEncontradoException`;
  - `actualizar` a un DNI de otro paciente → `ConflictoException`;
  - `actualizar` conservando el propio DNI con otro formato → OK;
  - `DataIntegrityViolationException` con la restricción `uq_paciente_consultorio_dni` en el `flush` → `ConflictoException`; con otra restricción → se relanza tal cual;
  - `obtener` con un id de otro consultorio → `RecursoNoEncontradoException`.

  [paciente-registry › Alta con DNI existente, Cambio a un DNI de otro paciente, Conservar el propio DNI, Paciente inexistente, Edición de paciente inexistente (reglas)]
- [x] 4.2 GREEN: implementar `PacienteVista`, y en `PacienteService` los métodos `crear`, `obtener`, `actualizar` y `buscar` (`@Transactional`, con `readOnly` en las lecturas), según design D6/D7, usando `ConsultorioActual`. Verificar que 4.1 pasa.
- [x] 4.3 RED: tests de `buscarOCrearPorDni`. Confirmar que fallan porque el método no existe.
  - Unit: con un DNI existente devuelve `creado = false` y no ejecuta el insert.
  - IT (`PacienteRepositoryIT` o un IT del servicio):
    - un DNI nuevo crea al paciente (`creado = true`) con `creado_en` = reloj fijo;
    - `30.123.456` con email `otra@ejemplo.test`, sobre un paciente existente con `ana@ejemplo.test`, devuelve el mismo id, `creado = false`, y el email en la base sigue siendo `ana@ejemplo.test`;
    - el `INSERT … ON CONFLICT` del repositorio devuelve vacío ante un DNI duplicado.

  [paciente-registry › DNI nuevo crea al paciente, DNI existente no sobrescribe el contacto]
- [x] 4.4 GREEN: implementar `ResultadoBuscarOCrear` y `buscarOCrearPorDni` con la query nativa `INSERT … ON CONFLICT (consultorio_id, dni) DO NOTHING RETURNING id` (timestamps desde el `Clock`) y la relectura, según design D5 🔑. Verificar que 4.3 pasa.
- [x] 4.5 RED → GREEN: `PacienteConcurrenciaIT`. Verificar que pasa varias veces seguidas (`.\gradlew.bat test --tests "*PacienteConcurrenciaIT" --rerun`, 3 corridas). Casos:
  - dos hilos sincronizados con `CountDownLatch` invocan `buscarOCrearPorDni` con el mismo DNI nuevo: ambos terminan sin excepción, con el mismo id, y `SELECT count(*)` da 1;
  - un `TransactionTemplate` confirma un paciente; otro `TransactionTemplate` invoca la operación con ese DNI, luego ejecuta una escritura propia (por ejemplo, un `UPDATE` inocuo del paciente) y hace commit sin `UnexpectedRollbackException`.

  [paciente-registry › Pedidos concurrentes con el mismo DNI, Usable dentro de una transacción mayor]
- [x] 4.6 Compartir con P1 la firma de `buscarOCrearPorDni` y de `Paciente` (design, "Open Questions"), en el PR o en el canal del equipo. Verificar que queda registrado su OK o el ajuste pedido (si pide la entidad, agregar la variante con su test).
  - *Resuelto 2026-11-21*: P1 (Thomas Romero) aprobó que devuelva `PacienteVista` (sin variante con la entidad) y que `C-06` se mergee antes que `C-04`.

## 5. API REST `/api/v1/pacientes`

- [x] 5.1 RED: `PacienteApiIT` (`AbstractWebIntegrationTest`, cadena real, `@AfterEach` que borra los pacientes del test) para alta y consulta. Confirmar que falla con `404`/`401` porque no hay controller. Casos:
  - `POST` válido → `201`, `Location` `/api/v1/pacientes/{id}`, email normalizado, `cantidadAusencias 0`, `activo true`, sin `password`/`hash`/`token` en el cuerpo;
  - `GET /{id}` → los 10 campos exactos;
  - `GET` de un id inexistente → `404` `application/problem+json`;
  - `GET` de un id de otro consultorio → `404`;
  - alta sin `fechaNacimiento` ni `direccion` → `201` con `null` y `''`.

  [paciente-registry › Alta válida, Alta seguida de consulta, Paciente existente, Paciente inexistente, Paciente de otro consultorio, Opcionales ausentes, Sin datos de acceso en la respuesta, Alta vinculada al consultorio actual]
- [x] 5.2 GREEN: crear `PacienteRequest` (Bean Validation, design D8), `PacienteResponse` y `PacienteController` (`POST` con `ResponseEntity.created(URI.create("/api/v1/pacientes/" + id))`, `GET /{id}`), sin lógica ni `@Transactional`. Verificar que 5.1 pasa.
- [x] 5.3 RED → GREEN: casos de validación y duplicado en `PacienteApiIT`. Ajustar las anotaciones del request hasta que pasen.
  - `400` con campo `email` (ausente), `telefono` (vacío), `dni` (`12AB` y `123456`) y `fechaNacimiento` (`2999-01-01`);
  - en todos esos casos, no se crea ningún paciente;
  - alta con un DNI existente en otro formato → `409`, el `detail` no contiene el DNI y no se crea un segundo paciente.

  [paciente-registry › Falta el email, Falta el teléfono, DNI con formato inválido, Fecha de nacimiento futura, Alta con DNI existente, Detalle del conflicto sin datos personales]
- [x] 5.4 RED → GREEN: `PUT /{id}` en `PacienteApiIT`, agregando el endpoint al controller. Verificar que pasa.
  - Teléfono nuevo → `200` y el `GET` lo refleja.
  - Cambio a un DNI ajeno → `409`, y el `GET` muestra el DNI original.
  - Mismo DNI con puntos → `200`.
  - `cantidadAusencias 0` y `activo false` en el cuerpo, sobre un paciente con 2 ausencias puestas por `JdbcTemplate` → `200` con `2`/`true`.
  - Id inexistente → `404`.

  [paciente-registry › Edición válida, Cambio a un DNI de otro paciente, Conservar el propio DNI, Campos de solo lectura ignorados, Edición de paciente inexistente]
- [x] 5.5 RED → GREEN: `GET /api/v1/pacientes` en `PacienteApiIT`, agregando el endpoint con `@RequestParam(required = false)` y la validación de `dni`/`q`. Verificar que pasa.
  - `?dni=30.123.456` → lista de 1;
  - un `dni` inexistente → `[]`;
  - `?q=pér` → contiene a "Ana Pérez";
  - `?dni=abc` → `400` con campo `dni`;
  - `q` de 101 caracteres → `400`;
  - `dni` y `q` juntos → manda `dni`.

  [paciente-registry › Búsqueda exacta por DNI, DNI sin coincidencias, Búsqueda por apellido sin distinguir mayúsculas]
- [x] 5.6 RED → GREEN: seguridad y logs en `PacienteApiIT`. Verificar que pasa (si hace falta código, solo en `PacienteService`/`PacienteController`).
  - `GET` y `POST` sin sesión → `401` `application/problem+json`, sin pacientes creados;
  - `POST` con sesión y sin `X-XSRF-TOKEN` → `403`, sin pacientes creados;
  - con `OutputCaptureExtension`, el intento de duplicado no deja en la salida ni el DNI ni el email.

  [paciente-registry › Búsqueda sin sesión, Alta sin sesión, Alta sin token CSRF, Duplicado sin datos personales en el log]

## 6. Documentación e integración

- [x] 6.1 Documentar en `backend/README.md`:
  - los endpoints de pacientes, con ejemplo de alta y de búsqueda;
  - que la próxima migración libre después de V5 es V6, que ya usa `C-03` (así que hay que revisar las ramas abiertas antes de numerar);
  - el aviso del hueco V4/V5 si `C-04` se mergea antes que `C-06`.

  Verificar por inspección que los ejemplos usan solo datos ficticios.
- [x] 6.2 Correr la suite completa con Docker abierto (`cd backend; .\gradlew.bat test --rerun`) y verificar que no hay fallos y que `ConfiguracionSinSecretosTest` sigue en verde. Correr `openspec validate c-04-pacientes-core --strict` sin errores.

## Workflow follow-up

- Revisar que todos los escenarios de `specs/paciente-registry/spec.md` y del delta de `database-schema-management` estén cubiertos por al menos un test.
- `/opsx:archive c-04-pacientes-core` y marcar `[x]` en `CHANGES.md` (C-04).
- Commit `feat(C-04): pacientes-core …` y PR desde `feat/c-04-pacientes-core`, revisado por otra persona. Solo cuando el usuario lo pida (RD-16).
- Avisar a P1 (para `C-07`/`C-09`) y a P4/P6 (para `C-05`) que `C-04` está en `main`. Eso abre el GATE 2.
