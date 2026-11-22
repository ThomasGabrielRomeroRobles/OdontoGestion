# Proposal

## Why

Los pacientes de OdontoGestión no tienen cuenta (DD-08): su único canal de identificación es el email, y todo lo que ataca el ausentismo y los huecos de agenda (confirmar solicitudes, recordatorios 24 h, avisos de hueco, confirmar o cancelar turnos) depende de dos piezas que todavía no existen: **un envío de notificaciones confiable** (outbox con reintentos, sin revertir la operación de negocio, RN-NO-01…09, DD-07, DD-13) y **tokens de acceso de un solo uso** que identifiquen al paciente en los enlaces (RN-TK-01…05). C-07, C-09, C-10 y C-11 los consumen; si C-03 se atrasa, pasa a ser camino crítico.

## What Changes

Change compartido, con dos mitades independientes:

**Mitad tokens — [P2 Martina]**
- Migración **V7** `token_acceso`: propósito, referencia exclusiva a turno / aviso / entrada de lista de espera (columnas nullable **sin FK todavía**, con `CHECK num_nonnulls(...) = 1` y coherencia propósito ↔ referencia), `token_hash` SHA-256 único, `vence_en`, `usado_en`, `consultorio_id`.
- Servicio reutilizable de tokens: emisión (≥128 bits con `SecureRandom`, URL-safe; solo se guarda el hash), verificación de solo lectura (contrato `GET` no consume), consumo atómico de un solo uso con propósito esperado (contrato `POST` ejecuta), revocación por referencia.
- Token vencido, usado, inexistente o de otro propósito → misma excepción de dominio, mapeada a **`410 Gone`** con un `ProblemDetail` genérico (sin enumeración). Los endpoints públicos que la usan llegan en C-09/C-10.
- No depende de nada de la mitad de notificaciones (se puede aplicar sola).

**Mitad notificaciones — [P3 Jeremías] (backend) y [P6 Martín] (frontend)**
- Migración **V6** `notificacion` (outbox): tipo, canal, destinatario, referencias nullable sin FK a turno / aviso / entrada, datos mínimos (sin clínicos), enlaces programados (propósito + vencimiento, **nunca el token en claro**), estado `PENDIENTE | ENVIADA | FALLIDA`, intentos, error; índices únicos parciales para idempotencia (`turno_id, tipo`) y (`aviso_id`).
- Puerto `NotificationChannel`, adaptador `EmailChannel` (Spring Mail, `MAIL_*`), adaptador falso en memoria para tests y contrato común ejecutable.
- `NotificationService.encolar(...)` (idempotente, dentro de la transacción de origen) y `procesarPendientes()` (3 intentos con espera creciente, el fallo no revierte el origen); `@Scheduled` cada 5 min (configurable); plantillas por tipo sin datos clínicos; los tokens de los enlaces se emiten **en el momento del envío** a través de un puerto que implementa la mitad de tokens.
- `GET /api/v1/notificaciones?estado=&tipo=` paginado y `POST /api/v1/notificaciones/{id}/reintentar` acotado (solo con sesión).
- Frontend: feature `notificaciones` (registro de envíos con filtros y reintento).

## Capabilities

### New Capabilities
- `patient-access-tokens`: emisión, almacenamiento con hash, verificación sin consumo, consumo atómico de un solo uso por propósito, revocación y respuesta uniforme `410` para tokens inválidos.
- `notification-delivery`: outbox de notificaciones, puerto de canal y adaptador de email, encolado idempotente, procesamiento con reintentos acotados, scheduler y plantillas sin datos clínicos.
- `notification-log`: API autenticada para consultar el registro de envíos con filtros y reintentar manualmente un envío fallido de forma acotada.
- `web-notification-log`: pantalla del odontólogo con el registro de envíos, filtros por estado y tipo, y acción de reintento.

### Modified Capabilities
- `api-error-handling`: se agrega la traducción de "token de acceso inválido" a `410 Gone` con cuerpo genérico idéntico para todas las causas.

## Impact

- **Migraciones**: V6 (`notificacion`) y V7 (`token_acceso`), números reservados para C-03. Se mergean **en un único PR** desde `feat/c-03-notificaciones-tokens-registro` (Flyway rechaza versiones fuera de orden; ver design.md D9).
- **Referencias a tablas futuras**: `turno` (V9, C-07), `entrada_lista_espera` (V10, C-10) y `aviso` (C-10) no existen. V6 y V7 crean las columnas `turno_id`, `aviso_id`, `entrada_lista_espera_id` **sin FK**; **C-07 (V9) MUST agregar** las FK `turno_id` de `token_acceso` y `notificacion`, y **C-10 MUST agregar** las de `aviso_id` y `entrada_lista_espera_id` en la migración que crea cada tabla (mismo patrón que `evolucion_clinica.turno_id` en CHANGES.md). Hasta entonces los tests usan ids ficticios y limpian sus filas.
- **Código backend nuevo**: paquetes `token` y `notificacion` en `domain/`, `application/`, `infrastructure/` y `api/`; `TokenInvalidoException` en `domain/error`; `NotificationProperties`; configuración de scheduling dedicada.
- **Código compartido modificado**: `GlobalExceptionHandler` (410, P2), `AbstractIntegrationTest` (scheduler deshabilitado en tests, P3), `AppConfig`, `application.yml`, `.env.example`, `build.gradle.kts` (GreenMail en tests, P3).
- **API**: `GET /api/v1/notificaciones`, `POST /api/v1/notificaciones/{id}/reintentar` (privadas, ya cubiertas por "API privada por defecto" y CSRF de C-02).
- **Frontend**: `frontend/src/features/notificaciones/`, ruta privada `/notificaciones` y entrada de navegación.
- **Consumidores**: C-07 (confirmación/cancelación), C-09 (solicitud y acciones por token), C-10 (avisos y lista de espera), C-11 (recordatorios).
- **Governance**: CRITICO (tokens = seguridad). El plan debe aprobarlo una persona antes de escribir código; Jeremías debe confirmar el reparto P2/P3.
