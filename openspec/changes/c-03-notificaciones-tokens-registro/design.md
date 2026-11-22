# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/`.

Estado actual en `main` (C-01 + C-02): backend Spring Boot 3.5 / Java 21 con paquetes `api / application / domain / infrastructure`; errores con `GlobalExceptionHandler` + `ProblemDetails.crear(...)` y excepciones en `domain/error` (cada una expone `codigo()`); `Clock` UTC inyectable (`ClockConfig`, en tests `FixedClockTestConfig` / `MutableClock`); `ConsultorioActual.id()` para el `consultorio_id`; `AppProperties` (ya trae `base-url`, `timezone`, `notification-channels`, `mail-from`) y properties separadas por tema (`LoginProperties`, `BootstrapProperties`) registradas en `AppConfig`; `spring-boot-starter-mail` ya es dependencia y `spring.mail.*` ya lee `MAIL_*`; `SecurityConfig` ya deja públicas `/api/v1/publico/**`, `/api/v1/acciones/**` y `/api/v1/avisos/**`. Tests de integración heredan de `AbstractIntegrationTest` (PostgreSQL 16 con Testcontainers, contenedor singleton `withReuse(true)`). Flyway: V1–V3 aplicadas; V6/V7 reservadas para C-03.

Restricción estructural: `turno` (V9, C-07), `aviso` y `entrada_lista_espera` (V10, C-10) todavía no existen, pero `token_acceso` y `notificacion` los referencian.

## Goals / Non-Goals

**Goals:**
- Mitad tokens (P2) aplicable y testeable **sola**, sin ninguna clase ni tabla de la mitad de notificaciones.
- Mitad notificaciones (P3) depende de tokens solo a través de un puerto propio, para poder testearla con un fake.
- Garantías de seguridad y concurrencia en la base (unicidad de hash, consumo condicional atómico, `CHECK` de referencias, índices únicos de idempotencia), no solo en Java.

**Non-Goals:**
- Endpoints públicos `/acciones/{token}` y `/avisos/{token}` y sus pantallas (C-09, C-10). C-03 solo entrega el servicio y el mapeo a `410`.
- Generación de recordatorios, vencimiento de pendientes/avisos/entradas (C-11 y C-09/C-10); C-03 solo procesa el outbox.
- Disparo de notificaciones desde operaciones de agenda (C-07 en adelante).
- Canal WhatsApp, limpieza periódica de tokens viejos, envío inmediato tras commit.

## Decisions

### D1 — Referencias a tablas futuras: columnas sin FK + `CHECK`, FK agregadas por quien crea la tabla
V6 y V7 crean `turno_id`, `aviso_id` y `entrada_lista_espera_id` como `bigint NULL` **sin `REFERENCES`**, con:
- `CHECK (num_nonnulls(turno_id, aviso_id, entrada_lista_espera_id) = 1)`;
- `CHECK` de coherencia tipo/propósito ↔ referencia (tokens: `CONFIRMAR_SOLICITUD|CONFIRMAR_TURNO|CANCELAR_TURNO` → turno, `ACEPTAR_AVISO` → aviso, `CONFIRMAR_LISTA_ESPERA` → entrada; notificaciones: `AVISO_HUECO` → aviso, `CONFIRMAR_LISTA_ESPERA` → entrada, resto → turno);
- índices parciales `WHERE <col> IS NOT NULL` en cada columna.

Las FK las agrega **la migración que crea cada tabla**:
- **C-07 / V9**: `ALTER TABLE token_acceso ADD CONSTRAINT fk_token_acceso_turno FOREIGN KEY (turno_id) REFERENCES turno (id) ON DELETE CASCADE;` y `ALTER TABLE notificacion ADD CONSTRAINT fk_notificacion_turno FOREIGN KEY (turno_id) REFERENCES turno (id) ON DELETE RESTRICT;` (el registro de envíos no debe perderse; los turnos no se borran físicamente).
- **C-10 / V10 (o la migración que cree `aviso`)**: ídem para `aviso_id` (token: `CASCADE`; notificación: `RESTRICT`) y `entrada_lista_espera_id`.

Es el mismo patrón que CHANGES.md ya usa para `evolucion_clinica.turno_id` (C-05/C-07). Hasta C-07, los tests usan ids ficticios (ej. 900001) **y borran sus filas** al terminar (ver Riesgos: contenedor reutilizable).

Alternativas: (a) tablas genéricas `referencia_tipo` + `referencia_id` → pierde las FK reales para siempre; (b) crear `turno` stub en V7 → invade C-07 y su `EXCLUDE`; (c) postergar V7 a después de V9 → rompe la numeración reservada y deja a C-07 sin tokens. Se elige columnas sin FK porque mantiene el modelo de la KB y las FK reales llegan en cuanto existe la tabla.

### D2 — Token: 256 bits, base64url, SHA-256 en `bytea`
- 32 bytes de un `SecureRandom` único compartido → `Base64.getUrlEncoder().withoutPadding()` (43 caracteres). Supera el mínimo de 128 bits sin costo.
- Se guarda `sha256(valor ASCII)` en `token_hash bytea NOT NULL CHECK (octet_length(token_hash) = 32)` con `UNIQUE`. Buscar por hash de un valor de alta entropía no filtra información por tiempo (el atacante no controla el preimagen), así que la búsqueda indexada es segura.
- Antes de hashear se valida el formato (`^[A-Za-z0-9_-]{43}$`); si no cumple → `TokenInvalidoException` (mismo rechazo).
- El valor en claro solo vive en `TokenEmitido` (record con `toString()` que lo oculta). Ningún log incluye el valor ni el hash.

Alternativas: UUID v4 (122 bits, por debajo del mínimo); HMAC con secreto del servidor (agrega gestión de secreto sin beneficio, el hash simple alcanza para valores aleatorios).

### D3 — Persistencia de tokens con `JdbcClient`, puerto en `domain`
`domain/token/TokenAccesoRepository` (interfaz) con implementación `infrastructure/token/JdbcTokenAccesoRepository` sobre `JdbcClient`. El consumo es:

```sql
UPDATE token_acceso
   SET usado_en = :ahora, actualizado_en = :ahora
 WHERE token_hash = :hash AND proposito = :proposito
   AND usado_en IS NULL AND vence_en > :ahora
RETURNING turno_id, aviso_id, entrada_lista_espera_id
```

Cero filas ⇒ inválido (no se distingue la causa). `:ahora` sale del `Clock` inyectado, **no** de `now()` de la base, para que los tests de vencimiento sean deterministas (la KB dice `vence_en > now()`; semánticamente es lo mismo con el reloj de la aplicación como fuente única, C-01). Bajo `READ COMMITTED`, PostgreSQL re-evalúa el `WHERE` de la segunda transacción tras el commit de la primera, así que exactamente una gana.

Sin entidad JPA: `ddl-auto=validate` no tiene nada que validar y se evita mezclar `RETURNING` con el contexto de persistencia. `JdbcClient` comparte la conexión de la transacción de Spring, así que el consumo participa de la transacción del llamador.

Alternativa: entidad JPA + `@Modifying` → no devuelve filas (`RETURNING`), requeriría `SELECT` + `UPDATE` o `@Version`, más idas a la base y más superficie.

### D4 — API del servicio de tokens (contrato para C-07/C-09/C-10/C-11)
`application/token/TokenAccesoService`:
- `TokenEmitido emitir(PropositoToken, ReferenciaToken, Instant venceEn)` — el llamador decide el vencimiento (TTL de pendiente, de aviso, inicio del turno…); rechaza `venceEn <= ahora` con `ValidacionDominioException`; valida coherencia propósito ↔ referencia también en Java (además del `CHECK`).
- `TokenVigente verificar(String valor, PropositoToken esperado)` — `@Transactional(readOnly = true)`, solo `SELECT`. Para el `GET`.
- `ReferenciaToken consumir(String valor, PropositoToken esperado)` — `@Transactional` (propagación `REQUIRED`): **el llamador MUST invocarlo dentro de su propia transacción** para que acción y consumo se confirmen o reviertan juntos. Para el `POST`.
- `int revocar(ReferenciaToken, Optional<PropositoToken>)` — `UPDATE … SET vence_en = :ahora WHERE <ref> AND usado_en IS NULL AND vence_en > :ahora`.

`ReferenciaToken` es un `sealed interface` con `Turno(long id)`, `Aviso(long id)`, `EntradaListaEspera(long id)`. `PropositoToken` es un enum con `referenciaRequerida()`.

### D5 — Rechazo uniforme `410`
`domain/error/TokenInvalidoException` (`codigo() = "token-invalido"`, mensaje fijo "El enlace no es válido o ya fue utilizado."). Un único `@ExceptionHandler` en `GlobalExceptionHandler` → `ProblemDetails.crear(GONE, "Enlace no disponible", "token-invalido", mensaje)`. La excepción nunca recibe el valor ni el hash, por lo que no puede filtrarse en la respuesta ni en el log. Se testea en `@WebMvcTest` con un endpoint de prueba (patrón `ErrorTestController`).

Alternativa: `404` (también permitido por RN-TK-05) → se elige `410` porque CHANGES.md lo fija y distingue "enlace muerto" de "ruta inexistente" sin revelar la causa.

### D6 — Outbox `notificacion` (V6)
Columnas: `id`, `consultorio_id` (FK `RESTRICT`), `tipo text CHECK IN (7 tipos)`, `canal text CHECK IN ('EMAIL','WHATSAPP') DEFAULT 'EMAIL'`, `destinatario citext NOT NULL`, `turno_id / aviso_id / entrada_lista_espera_id` (D1), `datos jsonb NOT NULL DEFAULT '{}'` (variables de plantilla), `enlaces jsonb NOT NULL DEFAULT '[]'` (lista de `{proposito, venceEn}`), `estado text CHECK IN ('PENDIENTE','ENVIADA','FALLIDA') DEFAULT 'PENDIENTE'`, `intentos int ≥ 0`, `reintentos_manuales int ≥ 0`, `error text NULL` (truncado a 500), `programada_para timestamptz NOT NULL`, `enviada_en timestamptz NULL`, `creado_en`, `actualizado_en`. Índices: `(estado, programada_para)` para el procesador, `(creado_en DESC)` para el listado, y los únicos de D7.

Entidad JPA `Notificacion` (extiende `AuditableEntity`) con `@JdbcTypeCode(SqlTypes.JSON)` para `datos` (`Map<String,String>`) y `enlaces` (`List<EnlaceProgramado>`).

### D7 — Idempotencia: índices únicos parciales + `ON CONFLICT DO NOTHING`
```sql
CREATE UNIQUE INDEX uq_notificacion_turno_tipo ON notificacion (turno_id, tipo)
  WHERE turno_id IS NOT NULL AND tipo <> 'CONFIRMACION_TURNO';
CREATE UNIQUE INDEX uq_notificacion_aviso ON notificacion (aviso_id) WHERE aviso_id IS NOT NULL;
CREATE UNIQUE INDEX uq_notificacion_entrada_tipo ON notificacion (entrada_lista_espera_id, tipo)
  WHERE entrada_lista_espera_id IS NOT NULL;
```
`CONFIRMACION_TURNO` queda fuera porque RN-NO-07 exige confirmar cada movimiento. `encolar` inserta con SQL nativo `INSERT … ON CONFLICT DO NOTHING`: en PostgreSQL una violación de unicidad **aborta la transacción**, y la transacción es la de la operación de origen; `DO NOTHING` evita romperla (RN-NO-06). Devuelve `boolean` (insertada o duplicada).

Alternativa: columna `clave_idempotencia` única calculada por tipo (resuelve también el recordatorio de un turno movido) → se aparta del modelo de la KB; queda como pregunta para C-11.

### D8 — Tokens de los enlaces emitidos al enviar (puerto `EmisorEnlaces`)
RN-TK-02 exige que el token en claro exista **solo en el email**; un outbox que guardara el enlace lo violaría. Por eso la notificación guarda `enlaces = [{proposito, venceEn}]` y la referencia; al procesarla, `NotificationService` pide a `application/notificacion/EmisorEnlaces` (puerto) las URLs. El adaptador `infrastructure/notificacion/EmisorEnlacesConTokens` llama a `TokenAccesoService.emitir(...)` y arma `APP_BASE_URL + "/acciones/" + token` (o `/avisos/` para `ACEPTAR_AVISO`). La emisión y el cambio de estado ocurren en la transacción del procesamiento de esa notificación; si el envío falla, los tokens emitidos quedan sin usar y vencen solos (no se mandaron a nadie). Si algún `venceEn <= ahora`, la notificación pasa a `FALLIDA` con error `ENLACE_VENCIDO` sin emitir tokens.

Dirección de dependencias: `notificacion → (puerto) ← adaptador → token`. El módulo de tokens no conoce notificaciones (requisito del reparto). P3 testea con un `EmisorEnlaces` falso.

### D9 — Orden de merge de V6 y V7: un único PR
Flyway con `outOfOrder=false` (default) falla la validación si aparece una versión menor que la última aplicada; si V7 llegara a `main` antes que V6, nadie podría aplicar V6 después. Decisión: **las dos mitades viven en la misma rama `feat/c-03-notificaciones-tokens-registro` y se mergean en un único PR** cuando ambas están en verde. Martina aplica y commitea la mitad de tokens primero en esa rama; Jeremías continúa sobre la misma rama (o una sub-rama que mergea a ella).

Plan B (solo si la mitad de notificaciones se atrasa y C-07 necesita tokens en `main`, y **antes** de que cualquiera de las dos esté mergeada): intercambiar los números dentro del rango reservado de C-03 (`token_acceso` → V6, `notificacion` → V7) y mergear la mitad de tokens sola. No requiere tocar reservas de otros changes. Nunca habilitar `outOfOrder`.

### D10 — Procesamiento: bloqueo por fila, una transacción por notificación
`procesarPendientes()` (sin transacción propia) toma ids con `SELECT id … WHERE estado='PENDIENTE' AND programada_para <= :ahora ORDER BY programada_para LIMIT :lote` y procesa cada uno con `TransactionTemplate` en `REQUIRES_NEW`: `SELECT … FOR UPDATE SKIP LOCKED` sobre la fila (re-verificando estado y momento), renderiza, emite enlaces, llama al canal y actualiza. Así un fallo o excepción en una notificación no afecta al resto ni a ninguna operación de negocio, y dos ejecuciones superpuestas no envían dos veces. Mantener la transacción abierta durante el envío SMTP es aceptable con una sola instancia local (DD-13).

Reintentos: `maxIntentos = 3`; esperas `[1 min, 5 min]` entre intentos (configurables en `NotificationProperties`); al tercer fallo → `FALLIDA`. Se interpreta "3 reintentos" de CHANGES.md como **3 intentos en total** (KB 12: "3 intentos con espera creciente").

Canal: `NotificationChannel { Canal canal(); ResultadoEnvio enviar(NotificacionSaliente) }`; `ResultadoEnvio` sellado (`Enviado` | `Fallido(String motivo)`); el adaptador captura sus excepciones. `EmailChannel` usa `JavaMailSender` con texto plano UTF-8 y `From = AppProperties.mailFrom()`. Selección por `AppProperties.notificationChannels()` (solo `EMAIL` en el MVP).

### D11 — Plantillas en código, con lista blanca de variables
`PlantillaNotificacion` por tipo: asunto + cuerpo de texto plano (text blocks), variables declaradas (ej. `consultorio`, `fecha`, `hora`, `prestacion`) y propósitos de enlace admitidos. `encolar` rechaza variables no declaradas (`ValidacionDominioException`) — barrera contra datos clínicos. Las fechas llegan como `Instant` en `datos` (ISO-8601) y se formatean al renderizar con `AppProperties.timezone()`. Sin motor de plantillas (Thymeleaf) para no sumar dependencias.

### D12 — Scheduler y configuración
`infrastructure/config/SchedulingConfig` con `@EnableScheduling` y `@ConditionalOnProperty(app.scheduling.enabled, matchIfMissing = true)`; `ProcesadorNotificacionesJob` con `@Scheduled(fixedDelayString = "${app.notifications.poll-interval:PT5M}")`. `AbstractIntegrationTest` agrega `app.scheduling.enabled=false` para que el job no corra en segundo plano durante los tests (los tests invocan `procesarPendientes()` directamente). Nueva `NotificationProperties` (`app.notifications.*`: `poll-interval`, `max-intentos`, `esperas`, `lote`) registrada en `AppConfig`; nueva variable `NOTIFICATION_POLL_INTERVAL` en `.env.example`.

### D13 — API del registro y frontend
`api/notificacion/NotificacionController`: `GET /api/v1/notificaciones?estado=&tipo=&page=0&size=20` (`size` 1–100, enums inválidos → `400`), respuesta `PaginaResponse<NotificacionResponse>` (record propio, no `Page` de Spring) y `POST /api/v1/notificaciones/{id}/reintentar` → `200` con la notificación. Reglas del reintento: inexistente → `RecursoNoEncontradoException` (404), no `FALLIDA` → `ConflictoException` (409), `reintentos_manuales >= 3` o enlace vencido → `ReglaNegocioException` (422).

Frontend (P6): `frontend/src/features/notificaciones/` con `notificaciones-api.ts` (tipos del contrato, usa `shared/http/client`), `notificaciones-page.ts` (filtros, tabla, paginación, reintento), ruta privada `/notificaciones` y enlace en `app-layout`. Avanza contra un fixture tipado del contrato antes de que el backend esté listo.

## Risks / Trade-offs

- [Contenedor de tests reutilizable (`withReuse(true)`) conserva filas con ids ficticios; cuando C-07 agregue la FK, `ALTER TABLE … ADD CONSTRAINT` fallaría por huérfanos] → todo test que inserte tokens/notificaciones borra sus filas (`@AfterEach`); C-07 documenta en V9 que en bases de desarrollo sin datos reales puede limpiar huérfanos antes de agregar la FK.
- [El consumo no se hace dentro de una transacción del llamador y la acción falla después] → contrato explícito en D4 + test de "acción falla, token sigue vigente"; revisión de C-09/C-10.
- [Un token emitido para un envío que luego falla queda en la base] → vence solo; nunca salió del proceso. Limpieza de tokens viejos queda para C-11 o etapa posterior.
- [Transacción abierta durante el envío SMTP] → aceptable con una instancia y lotes chicos (`lote = 50`); si se escalara, separar "reclamar" y "enviar" con un estado `EN_ENVIO`.
- [Recordatorio de un turno movido después del primer recordatorio queda bloqueado por `uq_notificacion_turno_tipo`] → pregunta abierta para C-11.
- [Límite diario del SMTP real (SU-10) al avisar a toda la lista de espera] → los fallos quedan registrados y son reintentables manualmente; fuera de alcance limitar la tasa.
- [Cambios en archivos compartidos (`GlobalExceptionHandler`, `AbstractIntegrationTest`, `AppConfig`, `application.yml`)] → cada uno tiene un dueño en tasks.md; los cambios son aditivos.

## Migration Plan

1. P2 crea `V7__token_acceso.sql`; P3 crea `V6__notificacion.sql`. Nunca se edita una migración ya aplicada en `main`.
2. Ambas se mergean juntas en un único PR (D9). Rollback local (documentado en el encabezado de cada migración, como V2/V3): `DROP TABLE token_acceso;` / `DROP TABLE notificacion;` y borrar su fila de `flyway_schema_history`.
3. C-07 (V9) y C-10 agregan las FK pendientes (D1).

## Open Questions

- C-11: ¿cómo se reprograma el recordatorio de un turno movido después de haber recibido el suyo? (posible `clave_idempotencia` o borrar el `RECORDATORIO` pendiente al mover).
- RN-LE-09 menciona que el paciente puede cancelar su entrada de lista de espera "desde un enlace": no hay propósito `CANCELAR_LISTA_ESPERA`. Agregarlo (nuevo valor en el `CHECK`) queda para C-10 si se implementa.
- C-09: con el intervalo de 5 min, el email de `CONFIRMAR_SOLICITUD` puede tardar hasta 5 min sobre un TTL de 30 min. Si molesta en la demo, bajar `NOTIFICATION_POLL_INTERVAL` o agregar envío inmediato tras commit en C-09.
