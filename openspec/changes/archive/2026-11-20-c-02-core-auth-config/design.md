# Design

> Governance **CRITICO** (auth/seguridad): este documento debe ser aprobado por una persona antes de escribir código. Las decisiones marcadas con 🔒 son las de seguridad que requieren revisión explícita.

## Context

Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/`.

Estado actual observado en `backend/` (tras `C-01`):

- Spring Boot **3.5.16**, Java 21, paquete base `ar.edu.odontogestion` con capas `api`, `application`, `domain`, `infrastructure` y subpaquetes por feature (C-01 D1).
- `spring-boot-starter-security` presente **sin** `SecurityFilterChain`; `UserDetailsServiceAutoConfiguration` excluida en `application.yml` (C-01 D8) → hoy todo está denegado.
- `application.yml` ya define `server.servlet.session.timeout: ${SESSION_TIMEOUT_MIN:30}m`, `ddl-auto: validate`, `hibernate.jdbc.time_zone: UTC`, `open-in-view: false`.
- `AppProperties` (record `@ConfigurationProperties("app")`) con `reminder-hours-before` y `pending-request-ttl-min`, que se superponen con parámetros que la KB ubica en `consultorio`.
- `Clock` UTC único (`ClockConfig`) y `FixedClockTestConfig` (`2026-11-20T12:00:00Z`).
- `GlobalExceptionHandler` (`@RestControllerAdvice`) con `DomainException` → `type` = `https://odontogestion.example/problemas/<codigo>`; subclases `RecursoNoEncontrado` (404), `Conflicto` (409), `ReglaNegocio` (422).
- Migración `V1__extensiones.sql` (`btree_gist`, `citext`). `FlywayV1MigrationIT.soloExisteLaTablaDeHistorialDeMigraciones` lista las tablas de `public` con `containsExactly("flyway_schema_history")` → **se rompe con V2** y debe adaptarse (delta de `database-schema-management`).
- Tests de integración: `AbstractIntegrationTest` (`@SpringBootTest`, PostgreSQL 16 Testcontainers singleton `withReuse(true)`, `@ServiceConnection`). `ConfiguracionSinSecretosTest` exige que toda clave `*password*` de los `application*.yml` sea `${...}`.
- Frontend (C-01) ya consume `POST auth/login {email,password}` → `{email}`, `POST auth/logout` → `204`, `GET auth/me` → `{email}` | `401`, lee la cookie `XSRF-TOKEN` en cada escritura y la envía en `X-XSRF-TOKEN`.

Restricciones: reglas duras 1–12 de `CLAUDE.md`; migraciones de este change solo **V2 y V3** (V4 → C-06, V5 → C-04, V6/V7 → C-03); sin nuevas dependencias salvo necesidad justificada.

## Goals / Non-Goals

**Goals:**
- Contrato compartido mínimo (entidades, repositorios, `ConsultorioActual`, `PasswordEncoder`) disponible el primer medio día para que P1 y P2 avancen en paralelo y para que C-03/C-04/C-06 arranquen al cerrar el GATE 1.
- Cadena de seguridad completa y testeada (sesión, CSRF, autorización, errores `ProblemDetail`), que los changes posteriores solo extienden agregando endpoints bajo los prefijos ya definidos.
- Convenciones de persistencia que heredan todos los changes: `AuditableEntity`, auditoría con el `Clock`, repositorios, `consultorio_id`.

**Non-Goals:**
- Frontend de configuración/perfil (C-06), fallback SPA de rutas profundas en `demo` (C-12).
- Sesiones persistentes o distribuidas (Spring Session), "recordarme", recuperación de contraseña, MFA/CAPTCHA.
- Invalidar otras sesiones abiertas al cambiar la contraseña (no hay registro de sesiones; una sola instancia).
- Rate limiting de la página pública (C-09, aunque podrá reutilizar el patrón del limitador).

## Decisions

### D1. Ubicación de clases y repositorios (convención para todos los changes)

| Capa | Paquete | Contenido de C-02 |
|------|---------|-------------------|
| domain | `domain.comun` | `AuditableEntity` |
| domain | `domain.consultorio` | `Consultorio`, `ParametrosConsultorio` (`@Embeddable`), `ConsultorioRepository` |
| domain | `domain.usuario` | `Usuario`, `UsuarioRepository`, `Emails` (normalización), `PoliticaPassword` (`violacion`/`exigir`) |
| domain | `domain.error` | + `CredencialesInvalidasException` (401), `DemasiadosIntentosException` (429) |
| application | `application.consultorio` | `ConsultorioActual` (interfaz `id()`), `ConsultorioService`, `ConsultorioVista`, `DatosConsultorio`, `BootstrapService` |
| application | `application.auth` | `AutenticacionService`, `LimitadorIntentosLogin`, `UsuarioAutenticado` |
| application | `application.perfil` | `PerfilService`, `PerfilVista`, `DatosPerfil` |
| api | `api.auth`, `api.consultorio`, `api.perfil` | controllers + request/response records (`ParametrosRequest`/`ParametrosResponse` anidados en consultorio) |
| infrastructure | `infrastructure.security` | `SecurityConfig`, `PasswordEncoderConfig`, `AuthenticationConfig`, `UsuarioDetailsService`, `SesionHttp`, `SpaCsrfTokenRequestHandler`, `ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler`, `CsrfCookieFilter` |
| infrastructure | `infrastructure.config` | + `JpaAuditingConfig`, `BootstrapProperties`, `LoginProperties` |
| infrastructure | `infrastructure.consultorio` | `ConsultorioActualUnico` (implementa `ConsultorioActual`) |
| infrastructure | `infrastructure.seed` | `BootstrapSeeder` (`ApplicationRunner`, `@Order(100)`) |

- Los repositorios Spring Data viven en `domain.<feature>` junto a su entidad (la KB ya acepta entidades JPA en `domain` por pragmatismo); así `application` depende solo de `domain`, como pide KB 08 §Capas.
  - *Alternativa*: `infrastructure.persistence` con puertos en `domain` → duplica interfaces sin beneficio en el alcance académico.
- Los services devuelven **records de aplicación** (`ConsultorioVista`, `PerfilVista`, `UsuarioAutenticado`; `ConsultorioService.parametrosVigentes()` devuelve el record `ParametrosConsultorio`); los controllers mapean a records de respuesta. Ninguna entidad cruza a `api` (regla 2).

### D2. Esquema: V2 (`consultorio`, `usuario` + fila demo) y V3 (parámetros)

`V2__consultorio_usuario.sql`:

```sql
CREATE TABLE consultorio (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre          TEXT NOT NULL CHECK (char_length(nombre) BETWEEN 1 AND 120),
  direccion       TEXT,
  telefono        TEXT,
  email_contacto  TEXT,
  zona_horaria    TEXT NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
  creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE usuario (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  consultorio_id  BIGINT NOT NULL REFERENCES consultorio (id) ON DELETE RESTRICT,
  email           CITEXT NOT NULL,
  password_hash   TEXT NOT NULL,
  nombre          TEXT NOT NULL,
  apellido        TEXT NOT NULL,
  matricula       TEXT,
  activo          BOOLEAN NOT NULL DEFAULT true,
  ultimo_acceso   TIMESTAMPTZ,
  creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_usuario_email UNIQUE (email)
);
CREATE INDEX ix_usuario_consultorio ON usuario (consultorio_id);
INSERT INTO consultorio (nombre, direccion, telefono, email_contacto)
VALUES ('Consultorio Demo', 'Calle Ficticia 123, CABA', '+54 11 0000-0000', 'contacto@consultorio.example');
```

`V3__parametros_consultorio.sql`: `ALTER TABLE consultorio ADD COLUMN ... INTEGER NOT NULL DEFAULT <d> CONSTRAINT ck_consultorio_<p>_positivo CHECK (<p> > 0)` para `granularidad_min` 15, `anticipacion_min_reserva_min` 120, `limite_cancelacion_horas` 2, `recordatorio_horas_antes` 24, `vencimiento_aviso_min` 60, `vencimiento_pendiente_min` 30, `tolerancia_ausente_min` 15. La fila demo existente recibe los defaults (tabla de 1 fila: sin riesgo de bloqueo).

- **El consultorio se inserta por Flyway, no por el seed Java**: las migraciones V4+ de C-06/C-04 (prestaciones, horarios, pacientes ficticios) necesitan un `consultorio_id` existente **durante** la migración, antes de que corra cualquier `ApplicationRunner`. Con identidad sobre base vacía, el id resultante es 1; aun así nadie debe hardcodearlo (ver D3).
  - *Alternativa*: crear el consultorio en el seed Java → rompe los seeds por migración de los changes siguientes.
- Defaults en la base (no en Java) para que el seed por SQL y el mapeo coincidan; los `CHECK > 0` son la red de seguridad de la regla de `422` (validada antes en dominio).
- `email` como `citext` + `UNIQUE` (regla RN-AU-03 garantizada en la base). Mapeo JPA con `@Column(columnDefinition = "citext")`: sin eso `ddl-auto=validate` falla porque pgjdbc reporta `citext` como `Types.OTHER`. Las columnas `TEXT` validan contra `String` sin `columnDefinition` (pgjdbc las reporta como `VARCHAR`).
- 🔒 **Normalización del email en Java además de `citext`**: el driver envía los `String` como `varchar`, y `citext = varchar` se resuelve como comparación de `text` (sensible a mayúsculas). Por eso `Emails.normalizar` (`trim` + `toLowerCase(Locale.ROOT)`) se aplica al guardar y al buscar; `citext UNIQUE` queda como garantía de integridad.
- Rollback (solo local): `DROP TABLE usuario; DROP TABLE consultorio;` para V2 y `ALTER TABLE consultorio DROP COLUMN ...` para V3, documentados como comentario en cada script (Flyway es forward-only).

### D3. `AuditableEntity`, auditoría y `ConsultorioActual`

- `@MappedSuperclass AuditableEntity`: `id Long` (`GenerationType.IDENTITY`), `creadoEn`/`actualizadoEn` `Instant` con `@CreatedDate` (`updatable=false`) / `@LastModifiedDate`, `@EntityListeners(AuditingEntityListener.class)`. `equals/hashCode` por id no nulo.
- `JpaAuditingConfig` (clase `@Configuration` dedicada, no en `@SpringBootApplication`) con `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")` y un `DateTimeProvider` que usa el `Clock` → los timestamps respetan el reloj fijo en tests.
- `Usuario.consultorio` es `@ManyToOne(fetch = LAZY, optional = false)`; `Consultorio` **no** mapea la colección inversa.
- `ConsultorioActual` (`@Component` en `application.consultorio`): resuelve y cachea **solo el id** del consultorio (`findFirstByOrderByIdAsc`) en el primer uso; `obtener()` relee la entidad en cada llamada. Si no hay consultorio → `IllegalStateException` (base mal migrada). Es el único punto que sabe cómo se elige el consultorio; si un día hay multi-consultorio, cambia solo esta clase.
  - *Alternativa*: propiedad `app.consultorio-id=1` → acopla configuración a un valor de identidad.
- `ConsultorioService.parametrosVigentes()` (`@Transactional(readOnly = true)`) devuelve `ParametrosConsultorio` leído de la base en cada llamada: es **el contrato** para C-06/C-07/C-09/C-11. "Cambios solo para operaciones nuevas" se cumple porque nadie cachea parámetros y los consumidores persisten los valores derivados (p. ej. vencimiento de un pendiente) al crear cada registro; C-02 no recalcula nada.

### D4. 🔒 `SecurityFilterChain`

```text
csrf:            CookieCsrfTokenRepository.withHttpOnlyFalse() (cookie XSRF-TOKEN, path "/", SameSite=Lax; cabecera X-XSRF-TOKEN)
                 + CsrfTokenRequestAttributeHandler (token "plano", sin máscara XOR)
                 + CsrfCookieFilter (después de CsrfFilter) que fuerza la carga del token diferido
authorize:       dispatcherTypeMatchers(ERROR, FORWARD).permitAll()
                 POST /api/v1/auth/login                         permitAll
                 /api/v1/auth/logout                             permitAll (lo atiende el LogoutFilter)
                 /api/v1/publico/**, /api/v1/acciones/**, /api/v1/avisos/**   permitAll
                 /api/**                                         authenticated   ← deny-by-default de la API
                 anyRequest                                      permitAll       ← index.html, /assets/**, rutas SPA
exceptionHandling: ProblemDetailAuthenticationEntryPoint (401) + ProblemDetailAccessDeniedHandler (403)
logout:          POST /api/v1/auth/logout → invalida sesión, borra JSESSIONID, CsrfLogoutHandler, 204 (HttpStatusReturningLogoutSuccessHandler)
securityContext: DelegatingSecurityContextRepository(RequestAttribute + HttpSession) como bean compartido
requestCache:    NullRequestCache (API REST: no se guardan solicitudes para redirigir)
formLogin, httpBasic: deshabilitados.   cors: no se configura (sin CorsConfigurationSource).
headers:         defaults de Spring Security (nosniff, X-Frame-Options DENY, Cache-Control no-store).
```

- **CSRF también en rutas públicas y en el login**: evita login-CSRF y no cuesta nada porque el cliente HTTP de C-01 siempre envía la cabecera. RN-AU-07 habla de "peticiones con sesión"; extenderlo es más estricto, no contradictorio.
- **Token plano en lugar del XOR por defecto de Spring Security 6**: el frontend lee el valor crudo de la cookie y lo devuelve en la cabecera; el XOR (mitigación BREACH) solo aporta cuando el token se incrusta en HTML comprimido, que no es el caso. Si la versión resuelta de Spring Security ofrece `csrf.spa()`, es equivalente y se puede usar en su lugar.
- **`CsrfCookieFilter`**: en Security 6 el token es diferido y la cookie solo se escribe cuando alguien lo lee; sin el filtro, el `GET /auth/me` anónimo inicial (que responde `401`) no entregaría `XSRF-TOKEN` y el primer login fallaría con `403` (Flujo 1, paso 1).
- `401` vs `403`: sin sesión en `/api/**` → `401` (entry point, aunque la ruta no exista: no se enumeran rutas); CSRF ausente/incorrecto → `403`.
- **Recursos fuera de `/api/` sin sesión**: la SPA protege su UI con la guardia de rutas (C-01) y los datos viven tras la API; así no hace falta listar rutas SPA en la seguridad.

### D5. 🔒 Sesión de servidor y cookie

`application.yml`:

```yaml
server.servlet.session:
  timeout: ${SESSION_TIMEOUT_MIN:30}m      # ya existe
  tracking-modes: cookie                    # nunca ;jsessionid= en URLs (regla 10)
  cookie: { name: JSESSIONID, http-only: true, same-site: lax, secure: ${SESSION_COOKIE_SECURE:false} }
```

- `SameSite=Lax` (KB RN-AU-06) en lugar del `Strict` de la compact rule `springboot-security`: `Lax` mantiene la sesión al llegar desde un enlace externo (p. ej. el odontólogo abre la app desde un email) y CSRF ya cubre las escrituras. Requiere aprobación.
- `Secure` desactivado por defecto porque la demo es HTTP local (DD-11); `SESSION_COOKIE_SECURE=true` al desplegar con HTTPS. Nueva variable en `.env.example` (no sensible).
- *(Ver "Adopción": finalmente solo lo aplica el contenedor.)* **Timeout aplicado también en código**: al iniciar sesión `SesionWeb` fija `session.setMaxInactiveInterval(...)` con el valor de `ServerProperties` (misma fuente que el YAML). Así es verificable con MockMvc (donde el contenedor no aplica `server.servlet.session.*`) y no hay dos propiedades.
- Estado de sesión en memoria de Tomcat (DD-03): un reinicio cierra la sesión. Aceptado.

### D6. 🔒 Login, logout y `me`

Flujo de `POST /api/v1/auth/login` (`LoginRequest(@NotBlank @Email email, @NotBlank password)` → `400` si falta algo):

1. `AuthController` → `AutenticacionService.autenticar(email, password, ip)` con `ip = request.getRemoteAddr()` (sin confiar en `X-Forwarded-For`: no hay proxy en la demo).
2. El service normaliza el email, consulta `LimitadorIntentosLogin.verificar(email, ip)` → si está bloqueado lanza `IntentosExcedidosException(retryAfter)` **sin evaluar la contraseña**.
3. Delegación a `AuthenticationManager` (`ProviderManager` + `DaoAuthenticationProvider` con `UsuarioDetailsService` y BCrypt). `DaoAuthenticationProvider` ya hace una comparación de hash ficticia cuando el usuario no existe (mitiga enumeración por tiempo). Cualquier `AuthenticationException` (`BadCredentials`, `Disabled`, `UsernameNotFound`) → `limitador.registrarFallo` + `CredencialesInvalidasException` (mismo `ProblemDetail` `401`, `detail` "Credenciales inválidas").
4. Éxito → `limitador.registrarExito(email, ip)`, actualiza `ultimo_acceso` con el `Clock` (`@Transactional`), devuelve `Authentication` + `UsuarioSesionVista`.
5. `SesionHttp.iniciar(auth, request, response)` (en `infrastructure.security`): crea el `SecurityContext`, aplica un `CompositeSessionAuthenticationStrategy` (`ChangeSessionIdAuthenticationStrategy` + `CsrfAuthenticationStrategy`) → **rota el id de sesión y el token CSRF**, fija el timeout (D5) y guarda el contexto con el `SecurityContextRepository` compartido (en Security 6 el guardado es explícito).
6. `200` con `{email, nombre, apellido}` (superset compatible del `{email}` que consume C-01).

- `UsuarioPrincipal` (implementa `UserDetails`, `CredentialsContainer`, `Serializable`): `id`, `consultorioId`, `email`, `activo`; el hash se borra tras autenticar (`eraseCredentials`), de modo que la sesión no lo conserva.
- `GET /api/v1/auth/me`: lee el usuario por id del principal (`@AuthenticationPrincipal`) para devolver datos frescos tras editar el perfil.
- Logout: lo resuelve el `LogoutFilter` (D4), idempotente (`204` también sin sesión).
- *Alternativa considerada*: filtro JSON propio (`AbstractAuthenticationProcessingFilter`). Se descarta: un controller es más fácil de testear con MockMvc, valida con Bean Validation y reutiliza el `GlobalExceptionHandler`.
- `CredencialesInvalidasException` (401, código `credenciales-invalidas`) e `DemasiadosIntentosException` (429, código `intentos-excedidos`, agrega cabecera `Retry-After` en segundos redondeados hacia arriba) se suman a `domain.error` y al `GlobalExceptionHandler`.

### D7. 🔒 Limitación de intentos (RN-AU-09)

- **Almacenamiento en memoria** (`ConcurrentHashMap` en un `@Component` singleton), con claves independientes `email:<normalizado>` e `ip:<remoteAddr>`. Sin dependencia nueva (bucket4j no modela backoff por fallos consecutivos de forma directa) y suficiente con una instancia (DD-03). Se pierde al reiniciar: aceptado.
- Parámetros en `application.yml` (`app.security.login.*`, record `LoginLimitProperties` validado): `max-fallos: 5`, `bloqueo-inicial: 1m`, `bloqueo-maximo: 15m`, `olvido-tras: 15m`.
- Algoritmo por clave (operaciones atómicas con `compute`, tiempo del `Clock`):
  - `verificar`: bloqueada si `bloqueadoHasta > ahora`; `Retry-After` = máximo de ambas claves.
  - `registrarFallo`: si la última actividad (último fallo o fin del último bloqueo) es anterior a `ahora - olvido-tras`, se reinicia el estado. `fallos++`; si `fallos >= max-fallos` **o** la clave ya tuvo un bloqueo dentro de la ventana, se bloquea por `min(bloqueo-inicial × 2^(bloqueos), bloqueo-maximo)` y `bloqueos++` → 1, 2, 4, 8, 15 min.
  - `registrarExito`: elimina el estado de ambas claves.
  - Higiene: al registrar un fallo, si el mapa supera 10 000 entradas se purgan las inactivas (cota de memoria ante IPs/emails aleatorios).
- Cuenta emails inexistentes igual que existentes (no revela existencia). La respuesta bloqueada es `429` con `Retry-After`; los logs registran "bloqueo por intentos (clave: email|ip)" sin el email ni la contraseña.
- *Alternativa*: bloqueo de la cuenta en la base (`usuario.bloqueado_hasta`) → permite a un atacante bloquear al odontólogo indefinidamente y no cubre IPs; descartada.

### D8. 🔒 Contraseñas y política

- `PasswordEncoderConfig`: `BCryptPasswordEncoder(12)` (≈ 200–300 ms por hash; costo ≥ 10 exigido por la spec). Bean en `infrastructure.security`, usado por el seed, el login y el cambio de contraseña.
- `PoliticaPassword` (dominio, puro): longitud ≥ 8 caracteres y ≤ 72 **bytes UTF-8** (límite de BCrypt: lo que exceda se ignoraría en silencio). Sin reglas de composición (NIST 800-63B). Lanza `ReglaNegocioException` (`422`).
- Cambio de contraseña (`PerfilService.cambiarPassword`, `@Transactional`): verifica la actual con `matches` (si falla → `422` "La contraseña actual no es correcta"), aplica la política, rechaza nueva == actual (`422`), guarda el nuevo hash. El controller luego llama `SesionWeb.rotar(request)` (`request.changeSessionId()`), conservando la sesión.
- Las contraseñas nunca se loguean: los request records de login/cambio sobrescriben `toString()` enmascarando el campo; ningún `log` recibe el request completo.

### D9. Seed de arranque (US-003)

- `BootstrapRunner` (`ApplicationRunner`, `infrastructure.bootstrap`) → `BootstrapOdontologoService.ejecutar()` (`@Transactional`).
- 🔒 Regla: **si existe al menos un usuario, no hace nada** (ni siquiera actualiza la contraseña). Así un cambio de contraseña o de `BOOTSTRAP_ODONTOLOGO_EMAIL` nunca crea un segundo usuario ni pisa la contraseña elegida.
- Si no hay usuarios: exige `BOOTSTRAP_ODONTOLOGO_PASSWORD` no vacía y conforme a `PoliticaPassword`; si no, lanza `IllegalStateException("Falta o es inválida la variable BOOTSTRAP_ODONTOLOGO_PASSWORD")` y el arranque falla (sin el valor en el mensaje). Crea el usuario activo con email normalizado, hash BCrypt, `nombre` "Alex", `apellido` "Demo", `matricula` "MN-00000" (ficticios, configurables por `app.bootstrap.*`), vinculado a `ConsultorioActual`.
- `BootstrapProperties` (`app.bootstrap.email: ${BOOTSTRAP_ODONTOLOGO_EMAIL:odontologo@consultorio.example}`, `app.bootstrap.password: ${BOOTSTRAP_ODONTOLOGO_PASSWORD:}`): el record sobrescribe `toString()` para no exponer la contraseña. Cumple `ConfiguracionSinSecretosTest` (placeholder `${...}`).
- No existe endpoint de registro (verificado por test: `POST /api/v1/auth/registro` → `401`).

### D10. Contratos REST

| Método y ruta | Request | Respuesta OK | Errores |
|---|---|---|---|
| `POST /api/v1/auth/login` | `{email, password}` | `200 {email, nombre, apellido}` + `Set-Cookie` `JSESSIONID`, `XSRF-TOKEN` | `400`, `401` genérico, `403` CSRF, `429` + `Retry-After` |
| `POST /api/v1/auth/logout` | — | `204` | `403` CSRF |
| `GET /api/v1/auth/me` | — | `200 {email, nombre, apellido}` | `401` |
| `GET /api/v1/consultorio` | — | `200 ConsultorioResponse` | `401` |
| `PUT /api/v1/consultorio` | `ConsultorioRequest` | `200 ConsultorioResponse` | `400`, `401`, `403`, `422` |
| `GET /api/v1/perfil` | — | `200 {email, nombre, apellido, matricula}` | `401` |
| `PUT /api/v1/perfil` | `{nombre, apellido, matricula?}` | `200` perfil | `400`, `401`, `403` |
| `POST /api/v1/perfil/password` | `{passwordActual, passwordNueva}` | `204` | `400`, `401`, `403`, `422` |

`ConsultorioResponse`: `{nombre, direccion, telefono, emailContacto, zonaHoraria, parametros: {granularidadMin, anticipacionMinReservaMin, limiteCancelacionHoras, recordatorioHorasAntes, vencimientoAvisoMin, vencimientoPendienteMin, toleranciaAusenteMin}}`. `ConsultorioRequest` igual sin `zonaHoraria` (si llega, Jackson la ignora).

Validación en dos niveles (alineada con `api-error-handling`):
- **Estructural → `400`** (Bean Validation en records): `nombre` `@NotBlank @Size(max=120)`, `direccion` `@Size(max=200)`, `telefono` `@Size(max=40)`, `emailContacto` `@Email @Size(max=120)`, `parametros` `@NotNull @Valid` y cada parámetro `@NotNull Integer`; perfil `nombre`/`apellido` `@NotBlank @Size(max=80)`, `matricula` `@Size(max=40)`.
- **Regla de negocio → `422`**: parámetros `> 0` en `ParametrosConsultorio.validar()` (el `detail` nombra todos los parámetros incumplidos); política de contraseña; contraseña actual incorrecta.

### D11. Errores de seguridad como `ProblemDetail`

`ProblemDetailAuthenticationEntryPoint` y `ProblemDetailAccessDeniedHandler` escriben `application/problem+json` con el `ObjectMapper` de Spring (`type` `.../no-autenticado` y `.../csrf-invalido`, `title`, `status`, `detail` fijo en español, `instance` = URI). No incluyen el token esperado ni el email. `429` y el `401` del login salen del `GlobalExceptionHandler` (D6) con el mismo formato.

### D12. Estrategia de pruebas (TDD estricto)

- **Unit (Mockito / puros)**: `PoliticaPassword`, `Emails`, `ParametrosConsultorio.validar`, `LimitadorIntentosLogin` (con un `Clock` mutable de test, sin Spring), `BootstrapOdontologoService` (casos de falla y logs con `OutputCaptureExtension`), `AutenticacionService`.
- **Persistencia** (`AbstractIntegrationTest`, PostgreSQL real): V2/V3 aplicadas, `citext UNIQUE`, `consultorio_id NOT NULL`, `CHECK > 0`, defaults, auditoría con reloj fijo, mapeo validado.
- **Web + seguridad** (`@SpringBootTest @AutoConfigureMockMvc` sobre la base común, `spring-security-test`): rutas públicas/privadas, CSRF (`.with(csrf())` vs sin token vs token incorrecto), login/logout/me, `401`/`403`/`429` `ProblemDetail`, cambio de contraseña, consultorio y perfil. Se usa la cadena real (no `addFilters = false`).
- **Servidor real** (`webEnvironment = RANDOM_PORT`, una sola clase): atributos de cookie de Tomcat (`HttpOnly`, `SameSite=Lax`), rotación ante un `JSESSIONID` fijado, `XSRF-TOKEN` sin `HttpOnly`. El escenario `Secure` se verifica por binding de `ServerProperties` con `SESSION_COOKIE_SECURE=true` (sin levantar otro contexto).
- **Higiene de contexto compartido**: `AbstractIntegrationTest` agrega `app.bootstrap.password` ficticia por `@TestPropertySource` (una sola variante de contexto). El contenedor se reutiliza entre ejecuciones → los tests no asumen base fresca: los que cambian perfil/contraseña/consultorio restauran el estado en `@AfterEach` (o en `@BeforeEach` vía `JdbcTemplate`), y los del limitador usan IP/email únicos por test (`.with(remoteAddr(...))`) porque el limitador es un singleton.
- Sin `@DirtiesContext` ni variantes de `@MockBean` en ITs.

## Risks / Trade-offs

- [Contexto de seguridad rompe tests de C-01] → `GlobalExceptionHandlerWebMvcTest` ya usa `addFilters = false`; los ITs existentes no hacen HTTP. Se corre la suite completa en el cierre de cada grupo.
- [`citext` vs parámetros `varchar` → login sensible a mayúsculas] → normalización en Java (D2) + test "email con mayúsculas y espacios".
- [Limitador en memoria se reinicia con la app / no escala a varias instancias] → aceptado (una instancia, DD-03); el algoritmo queda aislado en una clase para migrar a almacenamiento compartido si se despliega.
- [Bloqueo por IP afecta a usuarios legítimos detrás de la misma IP] → irrelevante con un único usuario; espera máxima 15 min.
- [BCrypt 12 enlentece tests] → pocos hashes por test; si molesta, el costo es configurable solo en tests sin bajar el de producción por debajo de 10.
- [Sesión en memoria: reinicio desloguea] → aceptado (DD-03).
- [El frontend de C-01 no distingue `429` en login] → coordinar con P5: hoy mostraría un error genérico; mejora en C-06/C-12.
- [`AppProperties.reminderHoursBefore`/`pendingRequestTtlMin` duplican parámetros de `consultorio`] → ver Open Questions; mientras tanto, la fuente de verdad de negocio es `consultorio` (D3) y ningún código nuevo debe leer esas propiedades.
- [Seed por Flyway de "Consultorio Demo" también corre en una base "real"] → solo hay demo local con datos ficticios (DD-11, regla 12).

## Migration Plan

1. Merge de C-02 en `main` → al arrancar, Flyway aplica V2 y V3 sobre bases con V1; el seed crea al odontólogo si `BOOTSTRAP_ODONTOLOGO_PASSWORD` está definida (cada integrante la carga en su `.env` local).
2. Bases locales de desarrollo con datos previos: no hay (V1 no tiene tablas).
3. Rollback: revertir el merge y, en la base local, `flyway clean` o los `DROP` documentados en los scripts (solo desarrollo; Flyway es forward-only).
4. Comunicar a P3/P4/P5/P6: próxima migración libre V4 (C-06), usar `ConsultorioActual` y `AuditableEntity`, endpoints nuevos bajo `/api/v1/**` quedan protegidos automáticamente.

## Open Questions

Requieren decisión humana pero no cambian specs ni tareas (se aplica el valor indicado si no hay respuesta):

1. **Variables `REMINDER_HOURS_BEFORE` y `PENDING_REQUEST_TTL_MIN`** (C-01, `AppProperties`) vs. parámetros editables en `consultorio`: ¿se eliminan en C-12 o se usan como valores iniciales? Propuesta: la base es la fuente de verdad; se eliminan en C-12.
2. **Cota superior de parámetros** (p. ej. `granularidadMin` divisor de 60, máximos razonables): la spec solo exige `> 0`. Propuesta: C-06 agrega la regla de granularidad compatible con duraciones al validar prestaciones.
3. **¿Contraseña actual incorrecta debe contar para el limitador?** Propuesta: no en el MVP (requiere sesión ya autenticada).
4. **SPA fallback en `demo`** (`/agenda` recargado devuelve `index.html`): fuera de C-02; propuesta: C-12.
5. **Datos ficticios del odontólogo y del consultorio demo** ("Alex Demo", "MN-00000", "Calle Ficticia 123, CABA"): confirmar o reemplazar por otros ficticios.

## Adopción — diferencias inocuas entre la implementación y este diseño

La implementación de los grupos 1–9 se adoptó de una copia previa y se parchó con TDD estricto. Lo que sigue describe la realidad donde difiere del texto de las decisiones anteriores (ninguna diferencia cambia el comportamiento exigido por las specs, salvo lo indicado):

- **D2 (esquema)**: los opcionales (`direccion`, `telefono`, `email_contacto`, `matricula`) se guardan como `''` con `NOT NULL DEFAULT ''` (no `NULL`, para evitar tres estados); `email_contacto` es `citext`; la unicidad del email es el constraint `uq_usuario_email` y el índice se llama `ix_usuario_consultorio_id`. El `INSERT` de "Consultorio Demo" está en V2 (no en Java); no hay restricción de fila única: la unicidad la resuelve `ConsultorioActual`.
- **D3**: `ConsultorioActual` expone solo `id()`; `ConsultorioActualUnico` (en `infrastructure.consultorio`) lo resuelve perezosamente, lo cachea y falla con `IllegalStateException` si hay 0 o más de 1 consultorio. Los servicios releen la entidad en cada operación. `parametrosVigentes()` devuelve el record `domain.consultorio.ParametrosConsultorio` (inmutable, sin proxies JPA) leído de la base en cada llamada. La positividad se valida en `ConsultorioService.actualizar` (no hay `ParametrosConsultorio.validar()`), devolviendo `422` con `errors[{field: "parametros.<campo>"}]`.
- **D4 (CSRF)**: se usa `SpaCsrfTokenRequestHandler` propio (token plano desde la cookie `XSRF-TOKEN`) con `CookieCsrfTokenRepository.withHttpOnlyFalse()`, y `CsrfCookieFilter` registrado después de `BasicAuthenticationFilter`; logout elimina `JSESSIONID` y expira `XSRF-TOKEN`.
- **D5 (timeout)**: el timeout lo aplica el contenedor con `server.servlet.session.timeout` (`SESSION_TIMEOUT_MIN`); el código no llama a `setMaxInactiveInterval`. Se verifica por binding de propiedades en `SessionConfigIT`. La expiración real por inactividad no es observable con MockMvc: se simula con una sesión invalidada (`AuthFlowIT`).
- **D6/D8**: `SesionHttp` (en `infrastructure.security`) reemplaza a `SesionWeb` y expone `iniciar` y `rotar`; el principal es un `User` de Spring con `ROLE_ODONTOLOGO` (no hay `UsuarioPrincipal`) y `/auth/me` y los perfiles se resuelven por el email de la sesión leyendo la base. El `detail` del `401` de login es "Email o contraseña incorrectos." (texto genérico único). `POST /perfil/password` responde `204` y rota el id de sesión (`request.changeSessionId()`); el email se normaliza (`Emails.normalizar`) en bootstrap, login (servicio y `UsuarioDetailsService`) y perfil. `BCryptPasswordEncoder(12)`; `PoliticaPassword` es la única política (usada por seed y perfil).
- **D7**: `LoginProperties` (`app.security.login.*`) tiene `maxIntentos` 5, `esperaInicial` 60 s, `esperaMaxima` 900 s y `maxClaves` 10000 como valores por defecto del record (sin claves en `application.yml` ni variables de entorno). La clase de excepción es `DemasiadosIntentosException`.
- **D9**: el seed es `BootstrapService` + `BootstrapSeeder` (`infrastructure.seed`); `BootstrapProperties` usa `app.bootstrap.odontologo-email`/`odontologo-password`; el odontólogo ficticio es "Odontólogo Demo" / "Ficticio" / "MN 0000". El log del seed es "Seed: odontólogo creado" sin el email.
- **D10**: `ConsultorioRequest` exige los siete `parametros` (incluida `toleranciaAusenteMin`) y `direccion`/`telefono`/`emailContacto` no nulos (`""` es válido); el `422` expone el campo en `errors[].field` (`parametros.<campo>`) y el `detail` es el texto genérico de regla de negocio.
