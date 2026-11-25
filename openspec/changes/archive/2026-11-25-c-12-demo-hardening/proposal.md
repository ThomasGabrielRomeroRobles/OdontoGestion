# Proposal

## Why

La entrega del 25/11 se presenta como una demo local (DD-11): un solo proceso en `http://localhost:8080` que sirve el frontend y la API, con PostgreSQL local y SMTP real. Hoy esa demo no se puede mostrar completa. El empaquetado del frontend ya existe (C-01, `./gradlew bootJar -PwithFrontend`) y el perfil `demo` arranca, pero:

- El frontend navega con rutas reales (`history.pushState`). En el perfil `demo`, entrar directo o recargar en `/agenda`, `/publico`, `/acciones/{token}` o `/avisos/{token}` responde `404`, porque Spring solo sirve `index.html` en `/`. Los enlaces de los emails (confirmar, cancelar, aceptar un aviso) llevan justamente a esas rutas, así que los flujos 2, 3 y 5 se cortan en el primer clic.
- La base de la demo solo tiene el consultorio, el odontólogo, las prestaciones y los horarios ficticios (V2, V4 y bootstrap). No hay pacientes ni turnos para mostrar la agenda, el contador de ausencias o la lista de espera sin cargar todo a mano frente al docente.
- No hay una guía única de cómo levantar la demo y correr los tests, ni una verificación registrada de que el SMTP real envía y de que no hay secretos versionados ni datos sensibles en logs o emails.

Es el último change obligatorio del corte por valor (DD-16) y cierra la Fase 6.

## What Changes

- **Fallback de la SPA en el perfil `demo`**: un `GET` a una ruta del frontend que no es un archivo ni está bajo `/api` devuelve `static/index.html` (con `200`), y el router del frontend resuelve la pantalla. `/api/**` inexistente sigue respondiendo `404` en `ProblemDetail`, y un archivo estático inexistente (por ejemplo `/assets/x.js`) sigue dando `404`. Sin cambios en `SecurityConfig`: las rutas no `/api` ya son públicas y el `FORWARD` está permitido.
- **Seed de demo**: un `ApplicationRunner` activado por la propiedad `app.seed-demo.enabled` (en `true` solo en `application-demo.yml`) que, si el consultorio no tiene pacientes, crea 10 pacientes ficticios y turnos de ejemplo de la semana en curso (reservados, confirmados, atendidos y ausentes, con el contador de ausencias coherente). Es idempotente y no encola notificaciones, para que no salgan emails a direcciones ficticias.
- **Smoke de punta a punta** (test de integración con perfil `demo`, Testcontainers y GreenMail): solicitud pública → email de confirmación → confirmación por token → turno `RESERVADO`. El tramo **→ recordatorio** se agrega cuando C-11 esté en `main`.
- **Verificación de la demo**: prueba manual de los flujos 2, 3 y 5 con el SMTP real (envío y límite diario de la cuenta, SU-10), revisión de secretos versionados y de datos sensibles en logs y emails (reglas duras 10, 11 y 12). Los resultados se registran en el PR.
- **Guía de ejecución local y de pruebas**: `README.md` en la raíz con los pasos para levantar la demo (PostgreSQL con `btree_gist`, `.env`, build con frontend, perfil `demo`) y para correr los tests (Docker para Testcontainers, PA-07).

Fuera de alcance: Playwright u otros tests de navegador (opcionales en `CHANGES.md`; se reemplazan por el smoke de backend más la prueba manual), un tope propio de envíos diarios en el código, despliegue público (US-115), y cualquier pantalla nueva de frontend.

## Supuestos a confirmar

- **Ausencias en la ficha del paciente**: el frontend no tiene una pantalla de ficha de paciente. El historial y el contador de ausencias ya se ven en el detalle del turno (C-08, requisito "Historial de ausencias del paciente" de `web-agenda`). Este change da ese ítem de `CHANGES.md` por cubierto y no agrega pantalla. Si el equipo quiere una ficha propia, se suma como grupo aparte.
- **Historia clínica en la agenda**: C-05 no está cerrado, así que, como prevé `CHANGES.md`, no se integra.
- **Recordatorio en el smoke**: depende de C-11 (pendiente). Ese tramo queda en un grupo bloqueado, igual que hizo C-08 con su grupo 9.

## Capabilities

### New Capabilities
- `demo-environment`: datos de demostración ficticios e idempotentes y verificación de punta a punta de la demo local.

### Modified Capabilities
- `runtime-configuration`: suma el fallback de la SPA para que las rutas del frontend funcionen con entrada directa, recarga y enlaces de email en el perfil `demo`.

## Impact

- **Backend**: un componente nuevo para el fallback de la SPA (`infrastructure/web`), `DemoSeedService` + `DemoSeeder` (`infrastructure/seed`, orden posterior al bootstrap), propiedad `app.seed-demo.enabled` en `AppProperties` y en `application-demo.yml`, y tests nuevos (`SpaFallbackIT`, `DemoSeedIT`, `DemoSmokeIT`).
- **Base de datos**: sin migraciones. El seed usa las prestaciones y horarios de V4.
- **Frontend**: sin cambios de código.
- **Documentación**: `README.md` en la raíz, ajustes menores a `backend/README.md` y `.env.example` (`SPRING_PROFILES_ACTIVE=demo` para la demo, `NOTIFICATION_POLL_INTERVAL=PT1M` recomendado).
- **Dependencias**: C-01 a C-04 y C-06 a C-10 en `main`; C-11 solo para el tramo del recordatorio.
- **Responsables** (según el plan de G6; confirmar en el equipo): P5 build y fallback de la SPA + guía, P4 seed de demo, P1 smoke y flujos de punta a punta, P2 revisión de secretos y logs.
- **Governance**: MEDIO.
