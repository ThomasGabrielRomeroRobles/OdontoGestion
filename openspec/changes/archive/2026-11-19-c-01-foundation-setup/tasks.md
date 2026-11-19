# Tasks

> TDD estricto: en cada tarea "RED → GREEN" se escribe primero el test, se ejecuta y se confirma que falla por la razón esperada, y recién después se implementa lo mínimo para pasarlo y se triangula. Los grupos de scaffolding no llevan tests propios.

## 1. Scaffolding del repositorio y del backend

- [x] 1.1 Crear `.gitignore` raíz (`.env`, `build/`, `.gradle/`, `node_modules/`, `dist/`, `backend/src/main/resources/static/`, archivos de IDE) y verificar con `git check-ignore .env backend/build frontend/node_modules`
- [x] 1.2 Crear `.env.example` con todas las variables de KB 08 §Variables de entorno y las sensibles vacías; verificar por inspección que `DB_PASSWORD`, `MAIL_USER`, `MAIL_PASSWORD` y `BOOTSTRAP_ODONTOLOGO_PASSWORD` no tienen valor
- [x] 1.3 Crear `backend/` con Gradle wrapper, `settings.gradle.kts` y `build.gradle.kts` (Boot 3.5.x, toolchain Java 21, starters web/security/data-jpa/validation/mail, Flyway + `flyway-database-postgresql`, driver PostgreSQL, deps de test con Testcontainers; sin versiones explícitas para artefactos del BOM); verificar con `./gradlew compileJava`
- [x] 1.4 Crear paquetes `ar.edu.odontogestion.{api,application,domain,infrastructure}` y `OdontoGestionApplication` (sin `@Enable*` en la clase principal); verificar que `./gradlew compileJava` pasa

## 2. Base de pruebas de integración y migraciones

- [x] 2.1 RED: escribir `AbstractIntegrationTest` (`@SpringBootTest`, `PostgreSQLContainer` `postgres:16-alpine` estático con `@ServiceConnection` y `withReuse(true)`) y `ApplicationContextIT` que verifica que el contexto arranca; ejecutar y confirmar falla (sin `application.yml`/datasource)
- [x] 2.2 GREEN: crear `application.yml` mínimo (datasource por `${DB_URL}`/`${DB_USER}`/`${DB_PASSWORD}`, `ddl-auto=validate`, `open-in-view=false`, `hibernate.jdbc.time_zone=UTC`, exclusión de `UserDetailsServiceAutoConfiguration`); verificar que `ApplicationContextIT` pasa
- [x] 2.3 RED: escribir `FlywayV1MigrationIT` con los escenarios de `database-schema-management`: versión `1` aplicada, `btree_gist` instalada, comparación `citext` verdadera, tabla temporal con `EXCLUDE USING gist (grupo WITH =, rango WITH &&)` rechaza solapamiento, solo existe `flyway_schema_history`; confirmar que fallan
- [x] 2.4 GREEN: crear `db/migration/V1__extensiones.sql` (`CREATE EXTENSION IF NOT EXISTS btree_gist; ... citext;` + comentario de rollback); verificar que `FlywayV1MigrationIT` pasa
- [x] 2.5 RED → GREEN: test que verifica la propiedad efectiva `spring.jpa.hibernate.ddl-auto=validate` y la ausencia de bases en memoria (URL JDBC `jdbc:postgresql:`); verificar que pasa sin `@DirtiesContext`
- [x] 2.6 RED → GREEN: test de arranque idempotente (segunda ejecución de `Flyway.migrate()` sobre la misma base no aplica migraciones) y test de checksum (Flyway `validate` falla ante un script V1 alterado, usando una ubicación de migraciones de prueba); verificar que pasan

## 3. Configuración de ejecución y reloj

- [x] 3.1 RED: unit test de `AppProperties` (binding con `ApplicationContextRunner`/`Binder`): sin `APP_TIMEZONE` → `America/Argentina/Buenos_Aires`; con `app.base-url=http://localhost:9999` → ese valor; valor inválido (horas negativas) rechazado por validación; confirmar falla
- [x] 3.2 GREEN: crear record `AppProperties` con `@ConfigurationProperties("app")` + `@Validated` y completar `application.yml` con todas las variables de KB 08 (`${VAR:default}` para no sensibles, sin default literal para contraseñas); verificar que 3.1 pasa
- [x] 3.3 RED → GREEN: crear `application-dev.yml` y `application-demo.yml`; test de integración que arranca con cada perfil activo y verifica el perfil reportado y `ddl-auto=validate` (una clase por perfil, compartiendo el contenedor); verificar que pasan
- [x] 3.4 RED → GREEN: test de inspección que recorre los `application*.yml` y falla si una clave `*password*` tiene un valor que no sea `${...}`; verificar que pasa
- [x] 3.5 RED: tests de `ClockConfig` (el bean `Clock` usa zona `UTC`) y de reloj fijo vía `@TestConfiguration` compartida (`2026-11-20T12:00:00Z`); test de integración que escribe y lee un `Instant` con `timestamptz` (JdbcTemplate, `TimeZone` por defecto de la JVM distinta de UTC) y obtiene el mismo instante; confirmar falla
- [x] 3.6 GREEN: crear `infrastructure.config.ClockConfig` con `Clock.systemUTC()`; verificar que 3.5 pasa

## 4. Manejo global de errores (ProblemDetail)

- [x] 4.1 RED: crear en `src/test` un controlador de prueba (`/api/v1/recurso-de-prueba`) con un request record validado y endpoints que lanzan cada excepción; escribir `GlobalExceptionHandlerWebMvcTest` (`@WebMvcTest`, `addFilters = false`) para formato común: `application/problem+json`, `status`, `title` no vacío, `instance` igual al path; confirmar falla
- [x] 4.2 GREEN: crear `api.error.GlobalExceptionHandler` (`@RestControllerAdvice`, extiende `ResponseEntityExceptionHandler`) y habilitar `spring.mvc.problemdetails.enabled`; verificar que 4.1 pasa
- [x] 4.3 RED → GREEN: escenarios de validación: campo ausente → 400 con `errors[].field = nombre`; dos campos inválidos → dos entradas; JSON mal formado → 400 sin texto del parser; implementar el override de `handleMethodArgumentNotValid` y `handleHttpMessageNotReadable`; verificar que pasan
- [x] 4.4 RED → GREEN: crear `domain.error.DomainException` y subclases `RecursoNoEncontradoException`, `ConflictoException`, `ReglaNegocioException` y sus handlers → 404/409/422 (422 con el mensaje de la regla en `detail`); verificar los tres escenarios
- [x] 4.5 RED → GREEN: excepción no controlada con mensaje `secreto-interno` → 500, cuerpo sin `secreto-interno` ni traza, `detail` genérico fijo; verificar con `OutputCaptureExtension` que se loguea el error y no el cuerpo de la solicitud

## 5. Integración del build del frontend en Gradle

- [x] 5.1 Crear `frontend/` con `package.json` (scripts `dev`, `build`, `test`, `typecheck`; devDependencies `typescript`, `vite`, `vitest`, `jsdom`), `tsconfig.json` strict (`noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `noImplicitOverride`), `vite.config.ts` (proxy `/api` → `http://localhost:8080`, Vitest `environment: 'jsdom'`), `index.html` y carpetas `src/{features,shared,pages}` + `main.ts` vacío; verificar con `npm ci && npm run typecheck && npm run build`
- [x] 5.2 RED → GREEN: test Vitest que importa la configuración de Vite y verifica que `server.proxy['/api'].target` es `http://localhost:8080` sin reescritura de path; verificar que pasa
- [x] 5.3 Agregar al `build.gradle.kts` tareas `npmCi`/`npmBuild` (`Exec`, `npm.cmd` en Windows, `workingDir` = `../frontend`) activadas solo con `-PwithFrontend`, y copiar `frontend/dist` a `static/` vía `processResources`; verificar: `./gradlew bootJar -PwithFrontend` produce un jar que contiene `BOOT-INF/classes/static/index.html` y `static/assets/*`, y `./gradlew bootJar` (sin propiedad, sin Node en PATH) no lo contiene

## 6. Frontend: cliente HTTP (`shared/http`)

- [x] 6.1 RED: tests de `csrf.ts`: lee `XSRF-TOKEN` de `document.cookie`, decodifica `a%2Bb` → `a+b`, devuelve `null` sin cookie; confirmar falla
- [x] 6.2 GREEN: implementar `shared/http/csrf.ts`; verificar que 6.1 pasa
- [x] 6.3 RED: tests de `client.ts` con `fetch` simulado: POST/PUT/PATCH/DELETE llevan `X-XSRF-TOKEN`, GET no, sin cookie no se envía; URL `/api/v1/<path>`; `credentials: 'same-origin'`; cuerpo serializado y `Content-Type: application/json`; `200` devuelve JSON decodificado y `204` devuelve `undefined`; confirmar falla
- [x] 6.4 GREEN: implementar `apiRequest<T>` y tipos `HttpMethod`/`ApiError` (unión discriminada `http` | `network`) en `shared/http/client.ts`; verificar que 6.3 pasa y `npm run typecheck` sin `any`
- [x] 6.5 RED → GREEN: tests de errores: `422` ProblemDetail → `status/title/detail`; `400` con `errors` → `fieldErrors`; `502` HTML → `title` genérico; `fetch` rechazado → `kind: 'network'`; implementar `shared/http/problem.ts` (tipo + type guard); verificar que pasan
- [x] 6.6 RED → GREEN: tests de `401`: invoca `onUnauthorized` inyectado con la ruta actual, salvo en `POST auth/login` donde rechaza con `ApiError` 401 sin invocarlo; implementar la opción en el cliente; verificar que pasan

## 7. Frontend: estado de sesión y router (`shared/state`, `shared/router`)

- [x] 7.1 RED: tests del store de sesión: estado inicial `unknown`; `setAuthenticated(email)` y `clear()`; un suscriptor recibe cada cambio exactamente una vez y deja de recibir tras desuscribirse; confirmar falla
- [x] 7.2 GREEN: implementar `shared/state/session.ts`; verificar que 7.1 pasa
- [x] 7.3 RED: tests del router (jsdom): ruta privada sin sesión → `/login?next=%2Fagenda` sin render privado; con sesión → render en layout; ruta pública sin sesión → render; ruta desconocida → not-found; click en `a[data-link]` usa `pushState` sin recarga; `popstate` re-renderiza; carga directa de `/pacientes` con sesión; confirmar falla
- [x] 7.4 GREEN: implementar `shared/router/router.ts` con tabla tipada de rutas (`access: 'public' | 'private'`); verificar que 7.3 pasa
- [x] 7.5 RED → GREEN: tests de `sanitizeNext`: acepta `/pacientes`, rechaza `https://evil.example`, `//evil.example` y valores vacíos (→ `/agenda`); implementar en `shared/router`; verificar que pasan

## 8. Frontend: autenticación, login, layout y logout (`features/auth`, `pages`)

- [x] 8.1 RED: tests de `features/auth/auth-api.ts` con `fetch` simulado: `me()` → `{email}` en 200 y `null` en 401; `login(email, password)` hace `POST /api/v1/auth/login`; `logout()` hace `POST /api/v1/auth/logout`; confirmar falla
- [x] 8.2 GREEN: implementar `auth-api.ts` como única definición de los paths del contrato de Flujo 1 (D11); verificar que 8.1 pasa
- [x] 8.3 RED: tests de `pages/login-page.ts`: render con email, contraseña `type=password` y botón; email vacío no envía y muestra "obligatorio"; éxito con `next=%2Fpacientes` navega a `/pacientes`; éxito sin `next` → `/agenda`; `next` externo → `/agenda`; `401` muestra mensaje genérico, vacía la contraseña y permanece en `/login`; confirmar falla
- [x] 8.4 GREEN: implementar `pages/login-page.ts` (DOM con módulos propios, sin `innerHTML` con datos del usuario); verificar que 8.3 pasa
- [x] 8.5 RED → GREEN: tests de `shared/layout/app-layout.ts`: muestra el email de la sesión y "Cerrar sesión"; logout con `204` y con `500` deja sesión anónima y navega a `/login`; implementar layout + acción de logout; verificar que pasan
- [x] 8.6 RED → GREEN: test de arranque de `main.ts` (función `bootstrap()` exportada): consulta `me()`, con 200 queda autenticado y renderiza la ruta actual; con 401 queda anónimo y una ruta privada redirige a login; conectar `onUnauthorized` del cliente al store y al router; verificar que pasan

## 9. Verificación integral

- [x] 9.1 Ejecutar `./gradlew clean test` en `backend/` (con Docker) y `npm test && npm run typecheck && npm run build` en `frontend/`; verificar todo en verde
- [x] 9.2 Arranque manual: con PostgreSQL local y `.env` exportado, `./gradlew bootRun --args='--spring.profiles.active=dev'` arranca y aplica V1; `npm run dev` sirve `/login` en el navegador; verificar sin errores en consola del navegador ni en el log del backend
- [x] 9.3 Agregar a `backend/README.md` y `frontend/README.md` (breves) los comandos de build/test, el requisito de Docker para Testcontainers y el uso de `-PwithFrontend`; verificar que los comandos documentados corren tal cual

## Workflow follow-up

- Revisar el desvío D9 (destino del build del frontend) y la elección de `auth/me` (D11) con el equipo antes de archivar.
- Al cerrar, archivar con `/opsx:archive c-01-foundation-setup` y marcar `[x]` C-01 en `CHANGES.md`.
- Commit (solo cuando el usuario lo pida) con `feat(C-01): ...`.
