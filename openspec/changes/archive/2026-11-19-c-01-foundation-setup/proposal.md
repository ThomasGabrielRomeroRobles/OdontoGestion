# Proposal

## Why

El repositorio todavía no tiene código: solo la base de conocimiento, el roadmap y la configuración de OpenSpec. Los 6 integrantes van a trabajar en paralelo por carriles y los 11 changes restantes asumen un esqueleto común (proyectos backend y frontend, perfiles, migraciones, base de tests con PostgreSQL real, manejo de errores, cliente HTTP con CSRF y shell de la SPA). Sin esa fundación cada carril reinventaría lo mismo de forma incompatible, y con 7 días de plazo no hay margen para retrabajo. Es el primer eslabón del camino crítico (`C-01 → C-02 → ...`).

## What Changes

- Nuevo proyecto Gradle `backend/` (Spring Boot 3.x, Java 21) con las dependencias del stack (Web, Security, Data JPA, Validation, Mail, Flyway, PostgreSQL) y los paquetes por capa `api / application / domain / infrastructure` más `OdontoGestionApplication`.
- Configuración externa: `application.yml` que lee las variables de entorno de la KB (§Variables de entorno), perfiles `dev` y `demo`, `.env.example` sin valores reales y `.gitignore` que excluye `.env`, builds y dependencias.
- Hibernate en modo `validate` y migración Flyway `V1` que habilita las extensiones `btree_gist` y `citext` (sin tablas de dominio).
- Base de tests: JUnit 5 + Testcontainers PostgreSQL reutilizable (con `btree_gist`), `Clock` inyectable en UTC y manejador global de errores que responde `application/problem+json` (`ProblemDetail`).
- Spring Security queda en el classpath con su configuración por defecto: C-01 NO define `SecurityFilterChain`, CSRF ni sesión. Eso es alcance de `C-02` (governance CRITICO).
- Nuevo proyecto `frontend/` (Vite + TypeScript strict vanilla, Vitest + jsdom, proxy `/api` → `http://localhost:8080`) con `features/`, `shared/`, `pages/`, `main.ts`, e integración opcional en el build de Gradle para servir el build desde Spring Boot (mismo origen, DD-12).
- Shell del frontend: `shared/http` (CSRF, `401` → `/login`, errores `ProblemDetail`), `shared/router` (rutas públicas/privadas con guardia), estado de sesión, layout base, página `/login` y logout contra el contrato de Flujo 1 (probados con mocks; el backend real llega en `C-02`).

## Capabilities

### New Capabilities
- `runtime-configuration`: cómo arranca y se configura la aplicación (perfiles, variables de entorno, secretos fuera del repo, reloj en UTC, servido del frontend desde el mismo origen).
- `database-schema-management`: versionado del esquema por migraciones, validación del mapeo contra el esquema y extensiones de PostgreSQL requeridas.
- `api-error-handling`: formato uniforme `ProblemDetail` de los errores de la API (validación, recurso inexistente, errores inesperados sin filtrar detalles internos).
- `web-http-client`: contrato del cliente HTTP del frontend con la API (CSRF en escrituras, manejo de `401`, errores tipados).
- `web-app-shell`: navegación de la SPA, guardia de rutas privadas, estado de sesión, pantalla de login y logout.

### Modified Capabilities
<!-- Ninguna: no existen specs previas en openspec/specs/. -->

## Impact

- **Código nuevo**: `backend/` y `frontend/` completos (esqueleto); `.gitignore`, `.env.example` en la raíz.
- **API**: no se publica ningún endpoint de negocio. El frontend consume (con mocks) el contrato de Flujo 1: `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` (nombre según CHANGES.md C-02; la KB 02 lo llama `/auth/sesion`), que implementa `C-02`.
- **Dependencias**: Spring Boot BOM, Testcontainers, Vite, TypeScript, Vitest, jsdom. Requiere Docker para tests de integración (SU-11) y Node para el frontend.
- **Equipo**: desbloquea `C-02`, `C-03`, `C-04` y el trabajo de frontend en paralelo.
- **Fuera de alcance**: entidades de dominio (incluida `Consultorio`, que llega con la migración V2 de `C-02`), autenticación real, `SecurityFilterChain`/CSRF del backend, email, schedulers.
