# Design

## Context

Repositorio greenfield: solo existen `knowledge-base/`, `CHANGES.md`, `openspec/` y las instrucciones de agentes. No hay `backend/` ni `frontend/`. Motivación en `proposal.md` (Why). Restricciones que moldean el diseño:

- Stack impuesto (DD-02), Flyway + `validate` (DD-04), frontend servido por Spring en demo y Vite con proxy en dev (DD-12), JUnit 5 + Testcontainers + Vitest con TDD estricto (DD-15).
- Las 16 reglas duras de `AGENTS.md`/`CLAUDE.md` (inyección por constructor, DTOs, `ProblemDetail`, sin `ddl-auto` destructivo, UTC, Testcontainers sin H2, sin secretos en logs ni commits, TS strict sin `any`, sin framework de UI).
- `C-02` (governance CRITICO) es dueño de `SecurityFilterChain`, CSRF del backend, sesión y endpoints `auth/*`. C-01 no debe adelantarlo, pero el frontend necesita el contrato para avanzar con mocks.
- Equipo de 6 en paralelo: la fundación debe dejar convenciones claras (paquetes, base de tests, cliente HTTP) para que cada carril empiece sin coordinar.

## Goals / Non-Goals

**Goals:**
- Esqueleto compilable y testeable de backend y frontend, con la estructura de directorios de la KB 08.
- Una única clase base de integración con PostgreSQL real reutilizable por todos los changes (contexto de Spring cacheado).
- Contratos transversales estables: formato de error de la API y cliente HTTP del frontend.
- Shell de SPA navegable con guardia de rutas, listo para que `C-02` conecte el backend real sin cambios en el frontend.

**Non-Goals:**
- Tablas, entidades o repositorios de dominio (empiezan en V2 con `C-02`).
- `SecurityFilterChain`, CSRF del lado servidor, sesión, BCrypt, seed del odontólogo (`C-02`).
- Pipeline de CI, Docker Compose, ESLint/Prettier (no requeridos por el roadmap; se pueden sumar luego).
- Estilos más allá de un layout base funcional.

## Decisions

### D1. Paquete base `ar.edu.odontogestion`, capas como paquetes de primer nivel
`api`, `application`, `domain`, `infrastructure` bajo el paquete base, tal como KB 08. Dentro de cada capa, subpaquetes por feature (`api.error`, `infrastructure.config`, etc.).
- *Alternativa*: paquetes por feature en el primer nivel (regla de `java-springboot`). Se descarta porque la KB fija capas como mínimo no negociable; se combina feature como segundo nivel.
- La KB dejaba el paquete "a definir en el primer change de scaffolding": se fija acá (confirmado por el equipo).

### D2. Gradle Kotlin DSL, Spring Boot 3.5.x, Java 21 toolchain, sin versiones explícitas en artefactos del BOM
`backend/build.gradle.kts` con `spring-boot-starter-{web,security,data-jpa,validation,mail}`, `flyway-core` + `flyway-database-postgresql`, driver `postgresql`; tests con `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `org.testcontainers:{junit-jupiter,postgresql}`. Wrapper de Gradle versionado.
- *Por qué*: compact rule `java-springboot` (no fijar versiones gestionadas por el BOM; Boot 3 usa `starter-web` + Jackson 2). Boot 3.5 trae `@ServiceConnection` (regla `spring-boot-test-patterns`).
- *Alternativa*: Groovy DSL; sin ventaja relevante y Kotlin DSL da autocompletado.

### D3. Configuración por variables de entorno con `${VAR:default}` y `@ConfigurationProperties` tipadas
- `application.yml` común con todas las variables de KB 08; las sensibles sin default (`${DB_PASSWORD}`, `${MAIL_PASSWORD:}`), las no sensibles con default de la KB.
- `application-dev.yml` y `application-demo.yml` con diferencias mínimas (logging, `spring.web.resources` de cache en demo).
- Un record `AppProperties` (`app.base-url`, `app.timezone`, `app.reminder-hours-before`, `app.pending-request-ttl-min`, `app.public-rate-limit-per-hour`, `app.notification-channels`) con `@Validated`. Los changes posteriores consumen este record, no `@Value` sueltos.
- `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`.
- `.env.example` en la raíz con todas las variables y sensibles vacías; `.gitignore` con `.env`, `build/`, `.gradle/`, `node_modules/`, `dist/`, `backend/src/main/resources/static/`.
- *Por qué*: reglas duras 5, 7, 11 y compact rule `springboot-security` (secretos desde env, nunca en `application.yml`).
- Sin `spring-dotenv`: Spring no lee `.env` por sí mismo; se documenta exportar variables (o el IDE/run config). Evita una dependencia extra.

### D4. Migración `V1__extensiones.sql` solo con extensiones
```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS citext;
```
- Ambas son *trusted extensions* desde PostgreSQL 13: el dueño de la base puede crearlas sin superusuario.
- *Por qué*: DD-04 y regla `postgresql-table-design` (`EXCLUDE USING gist` con columnas de igualdad necesita `btree_gist`); `citext` lo usa `C-02` para `usuario.email`.
- Rollback documentado en comentario (`DROP EXTENSION ...`); forward-only en la práctica (regla `database-migrations`).

### D5. Base de integración única con Testcontainers y `@ServiceConnection`
- Clase abstracta `AbstractIntegrationTest` (`@SpringBootTest`, `@Testcontainers`) con un `PostgreSQLContainer` estático `postgres:16-alpine` (la imagen oficial incluye los módulos contrib, por lo tanto `btree_gist` y `citext`), `@ServiceConnection` y `withReuse(true)`.
- Una sola configuración de contexto para maximizar el cache de contextos; prohibido `@DirtiesContext` y `@MockBean` variantes en ella (regla `spring-boot-test-patterns`).
- Los tests de persistencia de changes futuros usan `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + el mismo contenedor (fábrica compartida `PostgresTestContainer`).
- *Alternativa*: `@DynamicPropertySource`; más verboso. H2: prohibido (regla dura 9, DD-15).
- Si no hay Docker los tests de integración **fallan** (no se usa `disabledWithoutDocker`), para no ocultar cobertura faltante.

### D6. `Clock` como bean en UTC
`infrastructure.config.ClockConfig` expone `Clock.systemUTC()`. La lógica de dominio recibe `Clock` por constructor; las pruebas unitarias pasan `Clock.fixed(...)` directamente, y las de integración que lo necesiten usan una `@TestConfiguration` con `@Primary` compartida (una sola variante, para no romper el cache de contextos).
- *Por qué*: patrón "Clock inyectable" (KB 08), regla dura 7.

### D7. Manejo de errores: `@RestControllerAdvice` + jerarquía de excepciones de dominio
- `api.error.GlobalExceptionHandler extends ResponseEntityExceptionHandler`, `spring.mvc.problemdetails.enabled=true`.
- Excepciones en `domain.error`: `DomainException` (abstracta) → `RecursoNoEncontradoException` (404), `ConflictoException` (409; `C-07` derivará `HuecoNoDisponible`), `ReglaNegocioException` (422).
- `MethodArgumentNotValidException` → 400 con propiedad `errors: [{field, message}]`; `HttpMessageNotReadableException` → 400 con detalle genérico; `Exception` → 500 con detalle fijo y `log.error` con la excepción pero sin cuerpo de la solicitud (regla dura 10).
- `type` = `about:blank` salvo excepciones de dominio que usan `https://odontogestion.example/problemas/<codigo>` (URI estable, no resoluble; documentado).
- Tests con `@WebMvcTest` sobre un controlador de prueba en `src/test` (no se publica ningún endpoint en `main`), con `@AutoConfigureMockMvc(addFilters = false)` porque la seguridad no es objeto de C-01.
- *Por qué*: regla dura 4 y compact rule `java-springboot` (un único advice con `ProblemDetail`).

### D8. Seguridad: autoconfiguración por defecto, sin usuario generado
C-01 agrega `spring-boot-starter-security` pero no define `SecurityFilterChain`. Para no registrar en log la contraseña aleatoria que Boot genera, se excluye `UserDetailsServiceAutoConfiguration`; el efecto es "todo denegado" hasta que `C-02` defina la cadena real.
- *Alternativa*: no incluir Security hasta C-02. Se descarta porque CHANGES.md lo pide en C-01 y así C-02 solo agrega configuración.
- *Alternativa*: una cadena mínima con CSRF ya en C-01. Se descarta: invade un change CRITICO que requiere revisión humana.

### D9. Integración del build del frontend opt-in vía propiedad de Gradle
- `./gradlew build -PwithFrontend` ejecuta `npm ci` y `npm run build` en `../frontend` (tareas `Exec`, usando `npm.cmd` en Windows) y agrega `frontend/dist` a `processResources` bajo `static/`. Sin la propiedad, el backend compila sin Node (spec `runtime-configuration`).
- *Desvío respecto de CHANGES.md*: en lugar de copiar a `backend/src/main/resources/static/` se copia a la salida de recursos del build (`build/resources/main/static`). Spring lo sirve igual desde `classpath:/static/` y no quedan archivos generados en `src/` ni riesgo de commitearlos. Se agrega igualmente `backend/src/main/resources/static/` al `.gitignore` por si alguien copia a mano.
- *Alternativa*: plugin `com.github.node-gradle.node`; agrega una dependencia de build y descarga Node; innecesario si el equipo ya tiene Node.

### D10. Frontend: módulos propios tipados, sin dependencias de runtime
Estructura `src/{features,shared,pages}/` + `main.ts`, `tsconfig` con `strict`, `noUncheckedIndexedAccess`, `noImplicitOverride`, `exactOptionalPropertyTypes`. Sin dependencias de runtime; devDependencies: `typescript`, `vite`, `vitest`, `jsdom`.
- `shared/http`: `csrf.ts` (leer y decodificar `XSRF-TOKEN`), `problem.ts` (tipo `ProblemDetail` + type guard), `client.ts` con `apiRequest<T>(method, path, body?)` que devuelve `Promise<T>` y rechaza con `ApiError`, unión discriminada `{ kind: 'http', status, title, detail?, fieldErrors } | { kind: 'network' }` (regla `typescript-advanced-types`). `HttpMethod` como unión literal; métodos de escritura derivados con un tipo `Exclude<HttpMethod, 'GET'>`.
- El cliente recibe un `onUnauthorized` inyectado por `main.ts` (evita dependencia circular cliente ↔ router y facilita los mocks).
- `shared/router`: tabla de rutas `{ path, access: 'public' | 'private', render(ctx) }`, History API, interceptación de clicks en `a[data-link]`, `popstate`; guardia que consulta el estado de sesión y arma `next`.
- `shared/state/session.ts`: store observable mínimo `{ status: 'unknown' | 'anonymous' | 'authenticated', email? }` con `subscribe`.
- `features/auth/auth-api.ts`: `login`, `logout`, `me` contra `/api/v1/auth/*` (única definición de los paths del contrato). `pages/login-page.ts`, `shared/layout/app-layout.ts` (email + "Cerrar sesión"), placeholders para `/agenda` y `not-found`.
- `next` saneado: solo se acepta si empieza con `/` y no con `//` (evita open redirect).
- Tests Vitest con entorno `jsdom` y `fetch` simulado con `vi.stubGlobal`; `npm run typecheck` (`tsc --noEmit`) como verificación adicional.
- *Por qué*: reglas duras 13 y 14, DD-02.

### D11. Contrato de Flujo 1 asumido por el frontend
`POST /api/v1/auth/login` `{email, password}` → `200` `{email}` | `401` ProblemDetail; `POST /api/v1/auth/logout` → `204`; `GET /api/v1/auth/me` → `200` `{email}` | `401`. Se usa `auth/me` (CHANGES.md C-02) en lugar de `auth/sesion` (KB 02). `C-02` publica el contrato definitivo; si difiere, el impacto queda confinado a `features/auth/auth-api.ts`.

## Risks / Trade-offs

- [Docker no disponible en alguna máquina (SU-11, PA-07)] → Los tests de integración fallan explícitamente; los unitarios y de frontend siguen corriendo. Documentar en el README del backend el requisito de Docker Desktop en Windows.
- [Ruta del repo con espacios y en OneDrive] → Las tareas `Exec` de Gradle usan `workingDir` (sin concatenar rutas en strings de shell); sugerir clonar fuera de OneDrive si la sincronización bloquea `node_modules`/`build`.
- [Seguridad por defecto bloquea todo hasta C-02] → Esperado: el frontend trabaja contra Vite + mocks; `C-02` está inmediatamente después en el camino crítico.
- [Desvío del destino del build del frontend (D9)] → Mismo comportamiento observable; registrado acá para que el equipo lo valide.
- [Contrato `auth/me` vs `auth/sesion` (D11)] → Confinado a un módulo; C-02 decide.
- [Reutilización de contenedores con `withReuse(true)` requiere `testcontainers.reuse.enable=true` en `~/.testcontainers.properties`] → Sin esa opción solo pierde velocidad, no corrección.

## Migration Plan

Greenfield, no hay datos ni despliegue previo. Aplicar: crear proyectos, `./gradlew test` (con Docker) y `npm test` en `frontend/`. Rollback: revertir el commit del change; en una base local ya migrada, `DROP EXTENSION citext, btree_gist;` y borrar `flyway_schema_history`.

## Open Questions

Resueltas por el equipo el 2026-11-19:
- Paquete base: **`ar.edu.odontogestion`** (confirmado).
- Imagen de PostgreSQL en tests: **`postgres:16-alpine`** (confirmada); las máquinas locales de la demo deben tener PostgreSQL 15 o superior.
- Desvíos respecto de CHANGES.md aprobados: D8 (cadena de seguridad y CSRF del backend en C-02), D9 (build del frontend a `build/resources/main/static` con `-PwithFrontend`) y D11 (`GET /api/v1/auth/me`).
