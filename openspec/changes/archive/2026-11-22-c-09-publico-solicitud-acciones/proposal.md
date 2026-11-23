# Proposal

## Why

OdontoGestión ataca el ausentismo y los huecos de agenda, pero hoy solo el odontólogo puede dar turnos (C-07): el paciente no tiene forma de pedir un turno sin llamar ni de confirmar o cancelar el suyo. C-09 abre la **página pública sin login** (US-030) y las **acciones por enlace de email con token** (US-031, US-032), con las medidas anti-abuso decididas en DD-14 (retención de 30 min con confirmación por email, limitación de tasa, señuelo, sin enumeración). Está en el camino crítico (`C-07 → C-09 → C-10 → C-12`): C-10 (lista de espera) consume el `HuecoLiberado` de la cancelación del paciente y la página pública, y C-11 (recordatorios) emite los tokens `CONFIRMAR_TURNO`/`CANCELAR_TURNO` que este change ejecuta.

## What Changes

- **[P1] Solicitud pública** (sin sesión, bajo `/api/v1/publico/**` ya habilitado en C-02):
  - `GET /api/v1/publico/prestaciones`: prestaciones activas (id, nombre, duración); sin datos internos.
  - `GET /api/v1/publico/disponibilidad`: solo huecos libres, con la política pública de anticipación mínima (`anticipacion_min_reserva_min`).
  - `POST /api/v1/publico/solicitudes-turno` → `202` con mensaje genérico (RN-PU-07). En una transacción: expira pendientes vencidas, `buscarOCrearPorDni` (C-04, sin sobrescribir contacto), turno `PENDIENTE_CONFIRMACION` con `pendiente_hasta` = ahora + `vencimiento_pendiente_min` (núcleo `ReservaTurnoService` de C-07) y notificación `CONFIRMAR_SOLICITUD` con su enlace (el token se emite al enviar, C-03).
  - Anti-abuso: límite por IP y por DNI/email (`PUBLIC_RATE_LIMIT_PER_HOUR`, `429` + `Retry-After`), máximo 2 pendientes vigentes por DNI o email (`429`), campo señuelo (`202` sin efectos), validación estricta (`400`), `409` con alternativas calculadas con la política pública, `422` fuera de horario, en bloqueo o antes de la anticipación mínima.
  - La expiración periódica a `EXPIRADO` **ya existe** (C-07, `ExpiracionPendientesScheduler`); C-09 solo la cubre con tests del flujo público.
- **[P2] Acciones por token** (sin sesión, bajo `/api/v1/acciones/**`):
  - `GET /api/v1/acciones/{token}`: resumen del turno y de la acción, **sin consumir** (RN-TK-04), indicando si la acción está disponible (p. ej. fuera del límite de cancelación).
  - `POST /api/v1/acciones/{token}` con la acción esperada: `CONFIRMAR_SOLICITUD` → `RESERVADO` + `CONFIRMACION_TURNO`; `CONFIRMAR_TURNO` → `CONFIRMADO`; `CANCELAR_TURNO` → `CANCELADO` (`cancelado_por = PACIENTE`) + `TurnoCancelado` + `HuecoLiberado`, respetando `limite_cancelacion_horas` (RN-TU-03). Token consumido dentro de la transacción de la acción; `410` uniforme para vencido/usado/inexistente/propósito incorrecto; doble `POST` concurrente → gana exactamente uno.
  - Seguridad: `Referrer-Policy: no-referrer` en todas las respuestas, ninguna respuesta de error de rutas por token incluye la ruta (con el token) en `instance`, sin logs de URLs con token. Los `POST` públicos **mantienen CSRF** (decisión a aprobar, ver `design.md`).
  - Nuevo método de solo lectura en `TokenAccesoService` para identificar el propósito de un token sin consumirlo.
- **[P5] Frontend sin sesión**:
  - Feature `publico` en `/publico`: prestación → huecos libres → formulario (nombre, apellido, DNI, email, teléfono) con señuelo oculto; manejo de `202`, `400`, `409` (alternativas), `422` y `429`.
  - Feature `acciones-token` en `/acciones/{token}`: resumen + botón que hace `POST`; páginas informativas para éxito, enlace no disponible (`410`), fuera de límite y estado que ya no admite la acción.
  - Router con rutas parametrizadas; cliente HTTP que expone código de error, propiedades extra del `ProblemDetail` y `Retry-After`; `<meta name="referrer" content="no-referrer">`.
- Sin migración Flyway (todo se apoya en V9 de C-07 y V7 de C-03). Sin cambios de alcance fuera de CHANGES.md; el SPA fallback del backend para enlaces profundos sigue en C-12.

## Capabilities

### New Capabilities
- `public-booking`: solicitud de turno desde la página pública — prestaciones y disponibilidad públicas, solicitud pendiente con retención, anti-abuso (tasa, máximo de pendientes, señuelo, validación, no enumeración) y notificación de confirmación.
- `token-actions`: acciones del paciente por enlace con token sobre su turno — resumen sin consumo, confirmar solicitud, confirmar turno, cancelar con límite de horas, rechazo uniforme y concurrencia.
- `web-public-booking`: pantalla pública de solicitud de turno del frontend.
- `web-token-actions`: páginas del frontend para los enlaces `/acciones/{token}`.

### Modified Capabilities
- `patient-access-tokens`: se agrega la identificación de un token vigente (propósito, referencia y vencimiento) entre un conjunto de propósitos admitidos, sin consumirlo.
- `api-access-control`: se agrega la política `Referrer-Policy: no-referrer` en todas las respuestas.
- `api-error-handling`: se agrega que ninguna respuesta de error de rutas por token (`/api/v1/acciones/**`, `/api/v1/avisos/**`) incluya el token (en `instance` ni en otro campo).
- `web-app-shell`: las rutas admiten un segmento variable (`/acciones/{token}`) y se clasifican como públicas.
- `web-http-client`: el error tipado expone además el código del `type`, las propiedades adicionales del `ProblemDetail` (p. ej. `alternativas`) y el `Retry-After` de un `429`.

## Impact

- **Backend**: nuevos `api.publico` (controller + DTOs), `api.acciones`, `application.publico` (`SolicitudPublicaService`, `SolicitudPublicaComandos`, `LimitadorSolicitudesPublicas`), `application.acciones` (`AccionTokenService`); cambios acotados en C-07 (`SolicitudReserva.publica`, `DisponibilidadService` con política pública y alternativas por política, `Turno.cancelarPorPaciente`, evento `SolicitudConfirmada`, `NotificacionesTurnoListener`), C-03 (`TokenAccesoService.identificar` + método del repositorio JDBC), C-02 (`SecurityConfig` con `Referrer-Policy`) y `GlobalExceptionHandler` (`instance` saneado). `TurnoRepository` suma un conteo de pendientes vigentes por DNI/email.
- **Frontend**: `features/publico`, `features/acciones-token`, `shared/router` (parámetros), `shared/http/client` (extensiones del error), `index.html`, `bootstrap.ts` (rutas). Coordinar con C-08 (mismo carril P5) los cambios en `client.ts` y `bootstrap.ts`.
- **Configuración**: usa `PUBLIC_RATE_LIMIT_PER_HOUR` (ya existe en `AppProperties`) y los parámetros del consultorio `vencimiento_pendiente_min`, `anticipacion_min_reserva_min`, `limite_cancelacion_horas`. `app.pending-request-ttl-min` queda sin uso (manda el parámetro del consultorio, como en C-07).
- **Base de datos**: sin migración; V10 sigue libre para C-05/C-10.
- **Dependencias**: ninguna librería nueva (limitador en memoria, como el de login de C-02).
- **Consumidores**: C-10 (consume `HuecoLiberado` de la cancelación del paciente y reutiliza el patrón público/anti-abuso para la lista de espera), C-11 (emite `CONFIRMAR_TURNO`/`CANCELAR_TURNO` que ejecuta este change), C-12 (SPA fallback y E2E del flujo 2 y 5).
