# Arquitectura Propuesta

**Prioridad de calidad:** con ~7 días de plazo manda la **velocidad de entrega**, con un **mínimo no negociable de mantenibilidad**: capas limpias (api / application / domain / infrastructure) y la entidad `Consultorio` desde el día 1. Todo lo demás se simplifica (ver DD-10).

## Patrones aplicados

| Patrón | Dónde se usa | Por qué |
|--------|--------------|---------|
| Arquitectura en capas (api / application / domain / infrastructure) | Backend completo | Mantenibilidad y testabilidad; mínimo no negociable |
| Puertos y adaptadores (hexagonal liviana) | Notificaciones, reloj, repositorios | Permite cambiar email por WhatsApp sin tocar el dominio; facilita tests con dobles |
| Repository (Spring Data JPA) | Persistencia | Aislar el acceso a datos |
| Restricción de exclusión en DB | `turno` (agenda del odontólogo, por `consultorio_id`) | Garantía de no superposición aun bajo concurrencia |
| Optimistic locking (`@Version`) | `turno`, `entrada_lista_espera`, `aviso_hueco` | Detectar modificaciones concurrentes sin bloqueos largos |
| Eventos de dominio (`ApplicationEventPublisher`, `@TransactionalEventListener`) | `HuecoLiberado`, `TurnoCreado`, `TurnoAusente` | Desacoplar agenda, lista de espera y notificaciones |
| Outbox simple (tabla `notificacion`) | Envío de notificaciones | Persistir antes de enviar; reintentos e idempotencia, sin colas de mensajes |
| DTO + mapper | Capa API | No exponer entidades JPA |
| Strategy | Cálculo de disponibilidad, compatibilidad de lista de espera | Reglas aisladas y testeables |
| Clock inyectable | Toda lógica con tiempo | Tests deterministas |
| Módulos por feature en frontend | Frontend | Organización clara sin framework |

## Estructura de directorios

```
OdontoGestion/
├── knowledge-base/
├── openspec/
├── backend/
│   ├── build.gradle(.kts)        # tarea que copia el build del frontend a static/
│   └── src/
│       ├── main/java/ar/.../odontogestion/
│       │   ├── api/              # controllers REST (autenticados, publico, acciones, avisos), DTOs, errores
│       │   ├── application/      # casos de uso: agenda, turnos, página pública, lista de espera, ausentismo, HC
│       │   ├── domain/           # entidades (incl. Consultorio), reglas, eventos, puertos (NotificationChannel, etc.)
│       │   ├── infrastructure/   # JPA repos, mail, @Scheduled, Spring Security (sesión + CSRF), rate limiting, config
│       │   └── OdontoGestionApplication.java
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── static/           # build de Vite (demo/producción)
│       │   └── db/migration/     # Flyway V1__..., V2__...
│       └── test/                 # unit + integración (JUnit 5 + Testcontainers)
└── frontend/
    ├── package.json, tsconfig.json, vite.config.ts   # proxy /api -> http://localhost:8080
    └── src/
        ├── features/             # auth, agenda, publico (reserva + lista de espera), acciones-token, pacientes, historia-clinica, config
        ├── shared/               # cliente HTTP (CSRF), router, estado, componentes DOM, utilidades
        ├── pages/                # entradas por ruta
        └── main.ts
```

**Suposición:** paquete base y nombre de módulos a definir en el primer change de scaffolding. No hay `docker-compose` en el MVP: PostgreSQL corre instalado en la máquina (Docker solo es necesario para ejecutar los tests con Testcontainers; ver SU-11).

## Capas del backend (reglas de dependencia)

- `api` depende de `application`; no conoce `infrastructure`.
- `application` depende de `domain`; usa puertos.
- `domain` no depende de Spring ni de JPA en sus reglas puras (las entidades JPA pueden convivir en `domain` por pragmatismo; **Suposición:** se acepta para el alcance académico).
- `infrastructure` implementa puertos.
- Toda consulta y escritura lleva el `consultorio_id` (instancia única en el MVP), sin lógica multi-tenant.

## Estrategia de concurrencia y agenda

1. El cálculo de disponibilidad es una **consulta orientativa**; no garantiza el hueco.
2. La garantía es el `INSERT` bajo la restricción `EXCLUDE USING gist` sobre la agenda del odontólogo (única regla de no superposición; no hay sillones).
3. La violación se traduce a una excepción de dominio `HuecoNoDisponible` → `409`.
4. **El primero gana:** en lista de espera, todos los avisos apuntan al mismo intervalo; la primera aceptación que entra gana y las demás reciben `409`.
5. Las solicitudes públicas `PENDIENTE_CONFIRMACION` retienen el hueco hasta `pendiente_hasta`; las vencidas se marcan `EXPIRADO` antes de cada reserva y por el scheduler.
6. Los cambios de estado concurrentes sobre un mismo turno o aviso se controlan con `@Version`; el consumo de un token es una actualización condicional atómica (`WHERE usado_en IS NULL`).

## Estrategia de pruebas (TDD estricto)

| Nivel | Qué cubre | Herramientas |
|-------|-----------|--------------|
| Unitarias de dominio | Reglas RN (compatibilidad, transiciones, duración, tolerancia, TTL de pendientes) | JUnit 5, sin Spring |
| Integración de persistencia | Restricción de exclusión, repositorios, migraciones Flyway, hash y consumo de tokens | JUnit 5 + **Testcontainers PostgreSQL** (con `btree_gist`) |
| Concurrencia | Dos aceptaciones simultáneas del mismo hueco; dos solicitudes del mismo horario | Hilos + Testcontainers |
| API / seguridad | Contratos, sesión, CSRF, rutas públicas vs. autenticadas, rate limiting, códigos HTTP | MockMvc / `@WebMvcTest` + Spring Security Test |
| Scheduler | Recordatorio una sola vez ante dos ejecuciones; expiración de pendientes | JUnit 5 + `Clock` inyectable + Testcontainers |
| Frontend | Lógica de módulos, cliente HTTP (CSRF), renderizado | Vitest + jsdom |
| E2E (opcional) | Flujos 2 y 3 | **Suposición:** Playwright solo si sobra tiempo |

## Seguridad

- **Autenticación (odontólogo):** email + contraseña con **Spring Security y sesión de servidor** (cookie `JSESSIONID` `HttpOnly`, `SameSite=Lax`, `Secure` con HTTPS); rotación del id de sesión al login; expiración por inactividad (**Suposición:** 30 min). **No se usa JWT.**
- **CSRF:** protección activa; token en cookie `XSRF-TOKEN` leída por el frontend y enviada en `X-XSRF-TOKEN` en las escrituras (`CookieCsrfTokenRepository`).
- **Autorización:** todo `/api/v1/**` autenticado salvo `publico/**`, `acciones/**` y `avisos/**`; un solo tipo de usuario con login.
- **Contraseñas:** BCrypt (o Argon2) con costo adecuado.
- **Validación de input:** Bean Validation en DTOs; validación de dominio en servicios; consultas parametrizadas vía JPA.
- **Tokens de paciente** (confirmar solicitud, confirmar/cancelar turno, aceptar aviso): únicos por turno o aviso, aleatorios de 128+ bits, almacenados con hash, con vencimiento y uso único; `GET` no consume, `POST` ejecuta (RN-TK-01 a RN-TK-05).
- **Página pública (anti-abuso, decidido en DD-14):** confirmación por email antes de reservar, TTL de retención, limitación de tasa por IP y por DNI/email (filtro en memoria, bucket4j o similar), campo señuelo, respuestas sin enumeración (RN-PU-02, RN-PU-03, RN-PU-06, RN-PU-07).
- **Transporte:** HTTP local en la demo; HTTPS y cookie `Secure` al desplegar (etapa posterior). Mismo origen (Spring sirve el frontend; en desarrollo Vite hace proxy), por lo que no se habilita CORS.
- **Protección de datos de salud:** acceso clínico solo con sesión del odontólogo; sin datos clínicos en logs, URLs ni emails; datos ficticios (RN-GL-01).
- **Secrets management:** credenciales de SMTP y base solo por variables de entorno; nunca en el repositorio; `.env.example` sin valores reales.
- **Auditoría (etapa posterior):** log de accesos a HC y HC *append-only* (RN-HC-05).

## Despliegue

- **Desarrollo:** PostgreSQL instalado en la máquina; backend con `./gradlew bootRun`; frontend con el dev server de Vite (proxy `/api` al backend).
- **Demo / "producción" académica:** **local**. El build de Vite se copia a `static/` y Spring Boot sirve frontend y API desde un solo proceso (`http://localhost:8080`); PostgreSQL local y SMTP real (ej. Gmail con contraseña de aplicación, credenciales solo por variables de entorno). **No se requiere despliegue público.**
- **Scheduler:** `@Scheduled` corre mientras el proceso local esté levantado (no hay cold start ni suspensión que mitigar).
- **Etapa posterior (opción):** despliegue público en hosting con HTTPS (US-115); evaluar entonces proveedor, `btree_gist` y un scheduler persistente (ShedLock si hay varias instancias).

## Variables de entorno

| Variable | Descripción | Ejemplo | Sensible |
|----------|-------------|---------|----------|
| `SPRING_PROFILES_ACTIVE` | Perfil activo | `dev` / `demo` | N |
| `DB_URL` | URL JDBC de PostgreSQL local | `jdbc:postgresql://localhost:5432/odontogestion` | N |
| `DB_USER` | Usuario de base | `odonto` | N |
| `DB_PASSWORD` | Contraseña de base | (secreto) | Y |
| `BOOTSTRAP_ODONTOLOGO_EMAIL` | Email del odontólogo creado por seed | `odontologo@consultorio.example` | N |
| `BOOTSTRAP_ODONTOLOGO_PASSWORD` | Contraseña inicial del odontólogo | (secreto) | Y |
| `SESSION_TIMEOUT_MIN` | Inactividad máxima de la sesión | `30` | N |
| `MAIL_HOST` / `MAIL_PORT` | Servidor SMTP real | `smtp.gmail.com` / `587` | N |
| `MAIL_USER` / `MAIL_PASSWORD` | Credenciales SMTP (ej. contraseña de aplicación de Gmail) | (secreto) | Y |
| `MAIL_FROM` | Remitente | `no-reply@consultorio.example` | N |
| `APP_BASE_URL` | URL base para armar enlaces de email | `http://localhost:8080` | N |
| `APP_TIMEZONE` | Zona horaria del consultorio | `America/Argentina/Buenos_Aires` | N |
| `REMINDER_HOURS_BEFORE` | Anticipación del recordatorio | `24` | N |
| `PENDING_REQUEST_TTL_MIN` | Retención de solicitudes públicas sin confirmar | `30` | N |
| `PUBLIC_RATE_LIMIT_PER_HOUR` | Solicitudes públicas por IP por hora | `5` | N |
| `NOTIFICATION_CHANNELS` | Canales activos | `EMAIL` (luego `EMAIL,WHATSAPP`) | N |
