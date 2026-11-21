# Proposal

## Why

Tras `C-01` la aplicación arranca pero la seguridad por defecto de Spring deniega todo: no existe el odontólogo, no hay sesión, ni CSRF del lado servidor, ni datos del consultorio. Sin `C-02` ningún otro change puede exponer endpoints protegidos ni leer los parámetros de agenda (granularidad, anticipación, vencimientos) que necesitan `C-03`, `C-04`, `C-06`, `C-07`, `C-09` y `C-11`. Es el segundo eslabón del camino crítico y abre el GATE 1 (tres ramas en paralelo).

## What Changes

- Migración Flyway **V2**: tablas `consultorio` y `usuario` (email `citext` único, `password_hash`, `consultorio_id`, timestamps `timestamptz`) e inserción del registro ficticio "Consultorio Demo", de modo que las migraciones de changes posteriores (V4+) puedan referenciarlo.
- Base `AuditableEntity` (id `BIGINT` identity, `creado_en`, `actualizado_en` con el `Clock` UTC), entidades `Consultorio` y `Usuario`, repositorios Spring Data y un proveedor único del consultorio actual (`ConsultorioActual`), sin lógica multi-tenant.
- Seed de arranque idempotente (US-003): crea el odontólogo ficticio con la contraseña de `BOOTSTRAP_ODONTOLOGO_PASSWORD` (BCrypt) solo si no existe ningún usuario; sin endpoint de registro.
- Autenticación por **sesión de servidor**: `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`; cookie `JSESSIONID` `HttpOnly` + `SameSite=Lax`, rotación del id al iniciar sesión, expiración por inactividad (`SESSION_TIMEOUT_MIN`), `401` genérico y limitación de intentos fallidos por IP y por email (RN-AU-09) con `429`.
- `SecurityFilterChain` real: CSRF con `CookieCsrfTokenRepository` (`XSRF-TOKEN` / `X-XSRF-TOKEN`), `/api/v1/**` autenticado salvo `publico/**`, `acciones/**`, `avisos/**` y `auth/login`; recursos del frontend accesibles sin sesión; sin CORS. Errores de seguridad (`401`, `403`, `429`) en formato `ProblemDetail`.
- Migración Flyway **V3**: parámetros operativos en `consultorio` con defaults de la KB (`granularidad_min` 15, `anticipacion_min_reserva_min` 120, `limite_cancelacion_horas` 2, `recordatorio_horas_antes` 24, `vencimiento_aviso_min` 60, `vencimiento_pendiente_min` 30, `tolerancia_ausente_min` 15).
- `GET/PUT /api/v1/consultorio`, `GET/PUT /api/v1/perfil`, `POST /api/v1/perfil/password`: parámetros enteros positivos (si no, `422`), cambios que aplican solo a operaciones nuevas, cambio de contraseña con verificación de la actual.
- Se cierra el contrato `auth/*` que el frontend de `C-01` ya consume (`{email}` en login y me), ampliándolo de forma compatible.

Fuera de alcance: pantallas de configuración y perfil (frontend de `C-06`), fallback SPA de rutas profundas en el perfil `demo` (`C-12`), CAPTCHA/OTP, recuperación de contraseña por email, invalidación de otras sesiones abiertas, cookie `Secure` forzada (sin HTTPS en la demo local).

## Capabilities

### New Capabilities
- `odontologo-auth`: inicio, consulta y cierre de sesión del odontólogo con sesión de servidor, rotación de id, expiración por inactividad y limitación de intentos fallidos.
- `api-access-control`: autorización de rutas de la API (privadas vs. públicas/por token), protección CSRF de las escrituras y exposición de los recursos estáticos del frontend.
- `odontologo-account`: alta inicial del odontólogo por bootstrap, almacenamiento seguro de la contraseña, consulta y edición del perfil y cambio de contraseña.
- `consultorio-config`: consultorio único del MVP, sus datos de contacto y sus parámetros operativos con valores por defecto y validación.

### Modified Capabilities
- `api-error-handling`: se agregan los errores emitidos por la capa de seguridad (`401` sin sesión o credenciales inválidas, `403` por CSRF, `429` por intentos excedidos) en formato `ProblemDetail`.
- `database-schema-management`: el escenario "sin tablas de dominio" pasa a referirse solo a la migración `1`; se agrega el requisito de tablas de dominio con referencia al consultorio y timestamps UTC.

## Impact

- **Backend** (`backend/`): nuevos paquetes `domain.{comun,consultorio,usuario}`, `application.{auth,consultorio,perfil,bootstrap}`, `api.{auth,consultorio,perfil}`, `infrastructure.{security,persistence}`; migraciones `V2__consultorio_usuario.sql` y `V3__parametros_consultorio.sql`; `application.yml` (cookie de sesión, `tracking-modes: cookie`, `app.bootstrap.*`, `app.security.login-*`); ajuste de `FlywayV1MigrationIT` (el listado "solo historial" se valida sobre un esquema aislado con V1).
- **API**: endpoints `auth/*`, `consultorio`, `perfil`, `perfil/password`; contrato publicado para `C-06` (frontend de configuración) y consumido ya por el shell de `C-01`.
- **Configuración**: variables `BOOTSTRAP_ODONTOLOGO_EMAIL`, `BOOTSTRAP_ODONTOLOGO_PASSWORD`, `SESSION_TIMEOUT_MIN` (ya en `.env.example`) y nueva `SESSION_COOKIE_SECURE` (placeholder no sensible).
- **Dependencias**: ninguna nueva (limitador de intentos en memoria, sin bucket4j).
- **Changes dependientes**: `C-03`, `C-04`, `C-06` (y transitivamente todos) usan `ConsultorioActual`, `AuditableEntity`, la cadena de seguridad y los parámetros del consultorio.
- **Governance**: CRITICO — el diseño requiere aprobación humana antes de escribir código.
