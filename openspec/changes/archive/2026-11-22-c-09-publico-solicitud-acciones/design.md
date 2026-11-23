# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos: `specs/public-booking`, `specs/token-actions`, `specs/web-public-booking`, `specs/web-token-actions` y los deltas de `patient-access-tokens`, `api-access-control`, `api-error-handling`, `web-app-shell` y `web-http-client`.

Estado del código en la rama (apilada sobre `feat/c-07-turnos-agenda-core`, con C-01…C-04, C-06 y C-07 archivados):

- **C-07** ya provee el núcleo de reserva `ReservaTurnoService.reservar(SolicitudReserva)` (expira pendientes vencidas, valida horario con `ValidadorHorarioTurno` + `PoliticaAnticipacion`, crea `Turno.pendiente(...)` con `pendiente_hasta = ahora + vencimiento_pendiente_min`, traduce `23P01` a `HuecoNoDisponibleException`, publica `TurnoCreado`), la fachada sin transacción `TurnoComandos` que calcula `alternativas` fuera de la transacción abortada, `DisponibilidadService` (solo política del odontólogo), la expiración periódica (`ExpiracionPendientesScheduler`, cada 1 min) y `NotificacionesTurnoListener` (`BEFORE_COMMIT`; hoy ignora las pendientes). `Turno` ya tiene `confirmar()`, `confirmarSolicitud()` y `cancelar(CanceladoPor, motivo)` con `@Version`.
- **C-03** provee `TokenAccesoService` (`emitir`/`verificar(valor, propósito)`/`consumir(valor, propósito)` con `REQUIRED`/`revocar`/`revocarToken`), el `410` uniforme con `instance` constante solo para `TokenInvalidoException`, y `NotificationService.encolar(tipo, destinatario, referencia, datos, enlaces)` donde **el token se emite recién al enviar** (`EnlaceProgramado(propósito, venceEn)`), con URL `APP_BASE_URL/acciones/{token}`. La plantilla `CONFIRMAR_SOLICITUD` exige datos `consultorio`, `inicio`, `prestacion` y exactamente un enlace `CONFIRMAR_SOLICITUD`.
- **C-04** provee `PacienteService.buscarOCrearPorDni(DatosPaciente)` (`REQUIRED`, `ON CONFLICT DO NOTHING`, nunca deja la transacción en rollback por duplicado) que devuelve `ResultadoBuscarOCrear(PacienteVista, creado)`; `PacienteVista.activo` informa la baja lógica.
- **C-02**: `SecurityConfig` ya deja sin sesión `/api/v1/publico/**`, `/api/v1/acciones/**`, `/api/v1/avisos/**`; CSRF con `CookieCsrfTokenRepository` + `CsrfCookieFilter` (cookie `XSRF-TOKEN` en toda respuesta). `LimitadorIntentosLogin` es el patrón de limitador en memoria. Los handlers de seguridad 401/403 ponen `request.getRequestURI()` en `instance` (filtraría el token en `/acciones/{token}`), y Spring MVC completa `instance` con la ruta cuando un `ProblemDetail` no lo trae.
- `AppProperties.publicRateLimitPerHour` (`PUBLIC_RATE_LIMIT_PER_HOUR`, por defecto 5) ya existe sin uso. `app.pending-request-ttl-min` también existe, pero C-07 usa el parámetro del consultorio `vencimiento_pendiente_min`.
- Frontend: router con coincidencia exacta de `path`; `apiRequest` con CSRF y `ApiError` sin código ni extensiones; `bootstrap.ts` llama a `me()` al arrancar (esa `401` entrega la cookie `XSRF-TOKEN`); `/publico` es un placeholder.

## Goals / Non-Goals

**Goals:**
- Reutilizar el núcleo de C-07 sin duplicar lógica de reserva ni de disponibilidad.
- Que P2 (acciones) no dependa del código de P1 (solicitud pública): ambos se apoyan solo en servicios de C-07/C-03/C-04 existentes y en el contrato publicado (tarea 1).
- Que P5 avance contra el contrato con fixtures tipadas antes de que exista el backend.
- Seguridad: sin enumeración, sin token en respuestas/logs/Referer, consumo del token atómico con la acción.

**Non-Goals:**
- SPA fallback del backend para enlaces profundos (`/acciones/{token}` servido por Spring en `demo`): es de C-12. En desarrollo el enlace del email debe apuntar a Vite (`APP_BASE_URL=http://localhost:5173`).
- Lista de espera pública y `/avisos/{token}` (C-10); emisión de recordatorios con `CONFIRMAR_TURNO`/`CANCELAR_TURNO` (C-11).
- CAPTCHA/OTP (US-113), limitador distribuido o persistente.
- Revocar tokens cuando **el odontólogo** cancela o mueve un turno (el resumen muestra el estado y el `POST` responde `422`; queda para C-11 si hace falta).

## Decisions

### D1 — Contrato publicado primero (tarea 1, P1 + P2)
Antes de cualquier código se publica `openspec/changes/c-09-publico-solicitud-acciones/contrato-c09.md` con el mismo estilo que `contrato-c08.md` de C-07: endpoints, cuerpos de ejemplo, tabla de errores por endpoint y códigos `type`. Resumen:

| Endpoint | Request | Éxito | Errores |
|---|---|---|---|
| `GET /api/v1/publico/prestaciones` | — | `200 [{id, nombre, duracionMin}]` | — |
| `GET /api/v1/publico/disponibilidad?prestacionId&desde&hasta` | fechas `YYYY-MM-DD` inclusivas, ≤ 31 días | `200 [{inicio, fin}]` | `400`, `404`, `422` |
| `POST /api/v1/publico/solicitudes-turno` | `{prestacionId, inicio, nombre, apellido, dni, email, telefono, sitioWeb}` (`sitioWeb` = señuelo, opcional) | `202 {mensaje}` | `400`, `403` (CSRF), `404`, `409 hueco-no-disponible + alternativas`, `422`, `429 demasiadas-solicitudes + Retry-After` |
| `GET /api/v1/acciones/{token}` | — | `200 ResumenAccion` | `410 token-invalido` |
| `POST /api/v1/acciones/{token}` | `{accion}` (`CONFIRMAR_SOLICITUD`\|`CONFIRMAR_TURNO`\|`CANCELAR_TURNO`) | `200 ResumenAccion` (estado nuevo, `disponible: false`) | `400`, `403`, `409 modificacion-concurrente`, `410`, `422 transicion-invalida`\|`limite-cancelacion` |

`ResumenAccion`: `{accion, venceEn, disponible, motivo: null|"ESTADO_NO_PERMITE"|"FUERA_DE_LIMITE", limiteCancelacion: instant|null, turno: {consultorio, prestacion, inicio, fin, estado}}`. Nunca datos del paciente ni ids. Instantes ISO-8601 UTC con `Z`.

### D2 — Solicitud pública: fachada sin transacción + servicio transaccional (P1)
Espejo de `TurnoComandos` (C-07 D6):
- `application.publico.SolicitudPublicaComandos` (sin `@Transactional`): (1) `limitador.registrarIp(ip)` → `429`; (2) si `sitioWeb` no está en blanco, registra un contador en log sin datos y devuelve el resultado genérico (D5); (3) `limitador.registrarIdentidad(dni, email)` → `429`; (4) llama a `SolicitudPublicaService.solicitar`; ante `HuecoNoDisponibleException` completa `alternativas` con `DisponibilidadService.alternativas(prestacionId, inicio, 3, políticaPública)` en una transacción de solo lectura nueva.
- `SolicitudPublicaService.solicitar` (`@Transactional`): expira pendientes vencidas (antes de contar), cuenta pendientes vigentes por DNI normalizado o email (D4) → `429`, `buscarOCrearPorDni`; si el paciente existe y está dado de baja devuelve el resultado genérico **sin reservar** (evita el `422 pacienteId` de C-07, que revelaría la existencia); si no, `reservas.reservar(SolicitudReserva.publica(pacienteId, prestacionId, inicio, anticipacionMin))` (nueva fábrica: `PENDIENTE_CONFIRMACION`, origen `PUBLICA`, `PoliticaAnticipacion.publica(...)`). Todo en la misma transacción: un `409` revierte también el paciente creado.
- La IP es `HttpServletRequest.getRemoteAddr()`; **nunca** `X-Forwarded-For` (demo local sin proxy; aceptar la cabecera permitiría evadir el límite).
- Orden de chequeos: Bean Validation (`400`, sin efectos y sin consumir cupo) → límite IP → señuelo → límite identidad → pendientes → negocio. Alternativa descartada: contar también las inválidas con un filtro de servlet; agrega un segundo formato de `429` fuera de MVC sin beneficio real (una inválida no tiene efectos).
- Notificación: `NotificacionesTurnoListener.alCrear` pasa a encolar `CONFIRMAR_SOLICITUD` cuando `estado = PENDIENTE_CONFIRMACION` y `origen = PUBLICA`, con `EnlaceProgramado(CONFIRMAR_SOLICITUD, turno.pendienteHasta)` y los tres datos de la plantilla, dirigido al **email registrado** del paciente (RN-PU-04, PA-03). Mismo mecanismo `BEFORE_COMMIT` de C-07: no hay notificación fantasma si la transacción se revierte. El token de ese enlace vence exactamente en `pendiente_hasta`.

### D3 — Disponibilidad y prestaciones públicas (P1)
`DisponibilidadService` suma `huecosLibresPublicos(prestacionId, desde, hasta)` (misma validación de rango y prestación, política `publica(consultorio.anticipacionMinReservaMin)`) y una sobrecarga `alternativas(prestacionId, desde, maximo, PoliticaAnticipacion)`; la versión actual delega con la política del odontólogo, así C-07 no cambia de comportamiento. Prestaciones públicas: método de consulta de activas ordenadas por nombre en `PrestacionService` (o repositorio), expuesto como DTO `{id, nombre, duracionMin}`. Controller `api.publico.PublicoController`.

### D4 — Limitación de tasa en memoria y máximo de pendientes en base (P1) — 🔒 requiere aprobación
- `application.publico.LimitadorSolicitudesPublicas`: ventana deslizante de 1 h por clave (`ip:`, `dni:` con DNI normalizado por `Dni`, `email:` en minúsculas), `ConcurrentHashMap<String, Deque<Instant>>` con actualización atómica por clave (`compute`), límite `AppProperties.publicRateLimitPerHour` para las tres claves y tope de claves con purga de inactivas (como `LimitadorIntentosLogin`). `Retry-After` = segundos hasta que salga de la ventana el registro más antiguo. Reloj inyectado (`Clock`) para los tests.
- Máximo 2 pendientes: consulta en `TurnoRepository` (`count` de `PENDIENTE_CONFIRMACION` con `pendiente_hasta > ahora` cuyo paciente tiene ese DNI **o** ese email registrado), dentro de la transacción de la solicitud. Al excederse, `429` con `Retry-After` = `vencimiento_pendiente_min × 60`.
- Mismo cuerpo `429` para todos los límites (`type …/demasiadas-solicitudes`, detalle genérico), reutilizando el handler de `DemasiadosIntentosException` con una subclase de código propio.
- Alternativas: (a) persistir contadores en una tabla (sobrevive a reinicios, necesita migración V10 y limpieza) — descartada para el MVP de demo local; (b) Bucket4j — dependencia nueva sin necesidad.
- Trade-off aceptado: el máximo de pendientes es **blando** ante dos solicitudes simultáneas del mismo DNI (ambas pueden contar 1 y crear la 3.ª); el límite por DNI en memoria lo acota. Los contadores en memoria se pierden al reiniciar.

### D5 — Señuelo y no enumeración (P1) — 🔒 requiere aprobación
- Campo `sitioWeb` (`@Size(max = 200)`, opcional). Con contenido: `202` con el mismo cuerpo genérico, sin efectos, cuenta para el límite por IP, log `INFO` "solicitud pública descartada por señuelo" sin IP ni datos.
- `202` idéntico (`{"mensaje": "Si los datos son correctos, vas a recibir un email para confirmar el turno."}`) para: DNI nuevo, DNI existente, paciente dado de baja y señuelo.
- **Desviación consciente de RN-PU-07**: el `429` por máximo de pendientes o por límite de DNI/email sí permite inferir que ese DNI/email tuvo actividad reciente; se mantiene porque RN-PU-03/DD-14 piden explícitamente `429`. Se mitiga con cuerpo idéntico al del límite por IP. Alternativa (a aprobar si se prefiere): responder `202` genérico silencioso al exceder los límites de identidad.
- `409` y `422` no revelan datos del paciente (dependen solo del horario).

### D6 — Acciones por token: identificar en GET, consumir primero en POST (P2) — 🔒 requiere aprobación
- `TokenAccesoService.identificar(valor, Set<PropositoToken> admitidos)` → `TokenIdentificado(propósito, referencia, venceEn)`, `@Transactional(readOnly = true)`, nuevo método `buscarVigentePorHash(hash, ahora)` en `TokenAccesoRepository`/`JdbcTokenAccesoRepository` (devuelve también `proposito`). Propósito fuera del conjunto → `TokenInvalidoException`. Alternativa descartada: probar `verificar` con los tres propósitos (tres consultas y control de flujo por excepción).
- `application.acciones.AccionTokenService`:
  - `resumen(token)` (`readOnly`): `identificar` con `{CONFIRMAR_SOLICITUD, CONFIRMAR_TURNO, CANCELAR_TURNO}`; carga el turno por id y consultorio (si no existe → `410`); para `CONFIRMAR_SOLICITUD` con `pendiente_hasta <= ahora` → `410`; calcula `disponible`/`motivo` con `EstadoTurno.puedePasarA` y `Turno.limiteCancelacion(horas)`.
  - `ejecutar(token, accion)` (`@Transactional`): si `accion` no es uno de los tres → `410`; **primero** `tokens.consumir(token, accion)` (la `UPDATE` condicional bloquea la fila: un segundo `POST` concurrente espera y luego recibe `410`); después carga el turno y aplica:
    - `CONFIRMAR_SOLICITUD`: si `pendiente_hasta <= ahora` → `TokenInvalidoException` (revierte el consumo); `turno.confirmarSolicitud()`; `saveAndFlush`; publica `SolicitudConfirmada(turnoId)` → el listener encola `CONFIRMACION_TURNO`.
    - `CONFIRMAR_TURNO`: `turno.confirmar()`; `saveAndFlush`.
    - `CANCELAR_TURNO`: `turno.cancelarPorPaciente(ahora, limiteCancelacionHoras)` (nuevo método de dominio: valida el límite y delega en `cancelar(PACIENTE, null)`; lanza `LimiteCancelacionException extends ReglaNegocioException`, código `limite-cancelacion`); `saveAndFlush`; publica `TurnoCancelado(id, PACIENTE)` y `HuecoLiberado` (el listener de C-07 encola `CANCELACION`; C-10 consumirá `HuecoLiberado`); `tokens.revocar(Turno(id), empty)` invalida los demás enlaces del turno.
  - Un fallo después del consumo (`422`, `409` por `@Version`, `410` por vencida) revierte la transacción completa: el token sigue vigente (spec `token-actions › La acción falla y el token sigue vigente`).
- Límite de cancelación **inclusivo**: se permite si `ahora <= inicio − N h` (RN-TU-03 "hasta N horas antes"). Exactamente en el límite se permite.
- Controller `api.acciones.AccionesController`: `GET /{token}` y `POST /{token}` con `EjecutarAccionRequest(@NotNull PropositoToken accion)`. El `POST` exige la acción en el cuerpo para que la intención sea explícita y un token usado con otra acción responda `410` sin consumirse (spec de propósito incorrecto).
- Independencia de P1: P2 solo toca `Turno` (métodos nuevos), `TokenAccesoService`, el listener (método nuevo `alConfirmarSolicitud`) y la seguridad; no usa `SolicitudPublica*`. Sus ITs crean los turnos y emiten los tokens directamente (`ReservaTurnoService`/`TokenAccesoService.emitir`), sin pasar por el endpoint público.

### D7 — CSRF en los POST públicos: se mantiene (P2) — 🔒 requiere aprobación
Se **mantiene** la protección CSRF de C-02 en `POST /publico/solicitudes-turno` y `POST /acciones/{token}` (no se agrega excepción). Motivos:
- El flujo de la SPA ya obtiene la cookie: `bootstrap` llama a `GET /auth/me` y cada página pública hace un `GET` previo (`/publico/prestaciones`, `/acciones/{token}`), y `CsrfCookieFilter` emite `XSRF-TOKEN` en toda respuesta; `apiRequest` ya envía `X-XSRF-TOKEN`. Costo de implementación cero.
- Sin CSRF, cualquier sitio podría hacer que el navegador de una víctima dispare solicitudes públicas (emails a terceros, retención de huecos) consumiendo el cupo por IP de la víctima, o hacer un `POST` de acción si conoce el token (p. ej. filtrado). Con CSRF, solo el origen propio puede escribir.
- Sin sesión, el token CSRF no protege identidad sino **origen**; es la defensa razonable junto con `SameSite=Lax` del cookie.
- Alternativa descartada: excluir `/api/v1/publico/**` y `/api/v1/acciones/**` de CSRF (`csrf.ignoringRequestMatchers`) para simplificar clientes no-SPA; contradice el spec `api-access-control › Protección CSRF en escrituras` vigente y no hay clientes no-SPA.
- Riesgo: un `403` por cookie ausente si el usuario bloquea cookies; la página muestra error genérico.

### D8 — No filtrar el token (P2)
- `Referrer-Policy: no-referrer` **global** desde `SecurityConfig` (`headers.referrerPolicy(NO_REFERRER)`): cubre API, `index.html` y rutas de la SPA servidas por Spring. En desarrollo Vite sirve `index.html`, por eso P5 agrega `<meta name="referrer" content="no-referrer">`. Alternativa descartada: solo en `/acciones/**` y `/avisos/**`; la página que lleva el token en la URL es la SPA (`/acciones/{token}`, fuera de `/api`) y además no hay ningún uso legítimo del `Referer` en la app.
- `instance` saneado: un `ResponseBodyAdvice` (o ajuste en `ProblemDetails`) fija `instance` a una URN constante (`urn:odontogestion:ruta-por-token`) para cualquier `ProblemDetail` de `/api/v1/acciones/**` y `/api/v1/avisos/**`; los handlers de seguridad 401/403 usan la misma regla en lugar de `request.getRequestURI()`. Resto de rutas sin cambios.
- Logs: ningún log nuevo incluye token, URL de token, DNI, email, teléfono, nombre ni IP. Test de configuración: los perfiles `dev`/`demo` no habilitan `DEBUG`/`TRACE` en `org.springframework.web` ni `server.tomcat.accesslog.enabled`, y `spring.mvc.log-request-details` no está activo; ITs con `OutputCaptureExtension` verifican que el token y los datos personales no aparecen.

### D9 — Frontend (P5)
- `shared/router`: `path` con segmentos `:param` (p. ej. `/acciones/:token`), coincidencia por segmentos, `params` en el contexto de render; las rutas exactas existentes no cambian.
- `shared/http/client`: `ApiError` http suma `code` (último segmento de `type`), `extensions: Readonly<Record<string, unknown>>` y `retryAfterSeconds?: number`; las features hacen el narrowing (p. ej. `parseAlternativas(unknown): Hueco[]`). Coordinar con C-08 (mismo carril, también necesita `alternativas`).
- `features/publico`: `publico-api.ts` tipado contra el contrato (D1) con un resultado discriminado (`aceptada | conflicto(alternativas) | limite(retryAfter) | invalida(fieldErrors) | horarioInvalido`), validadores (DNI 7–8 dígitos con o sin puntos, email, teléfono, obligatorios), página con señuelo (`sitioWeb`, fuera de pantalla, `tabindex=-1`, `aria-hidden`, `autocomplete=off`), horas en `America/Argentina/Buenos_Aires` (reutilizar `features/config/zoned-time.ts`).
- `features/acciones-token`: `acciones-api.ts`, página `/acciones/:token` con máquina de estados (`cargando → resumen → ejecutando → hecho | noDisponible(410) | estadoCambiado(409/422) | error`), botón único deshabilitado al enviar.
- Fixtures tipadas del contrato en `test-fixtures.ts` y mocks de desarrollo (patrón `dev/config-mocks.ts`) para trabajar sin backend.
- Rutas públicas en `bootstrap.ts` reemplazando el placeholder de `/publico`.

### D10 — Sin migración
Todo cabe en V9 (turno, estados, `pendiente_hasta`, origen, `cancelado_por`) y V7 (token). V10 sigue libre para C-05/C-10. El conteo de pendientes por DNI/email usa el `JOIN` con `paciente` (DNI con índice único por consultorio); el volumen de la demo no justifica índices nuevos.

## Risks / Trade-offs

- [Contadores en memoria se pierden al reiniciar] → aceptable en demo local; el máximo de pendientes vive en la base y no se pierde.
- [Máximo de pendientes blando ante concurrencia del mismo DNI] → límite por DNI en memoria + TTL de 30 min; documentado en D4.
- [`429` por identidad permite inferir actividad de un DNI/email] → cuerpo idéntico al límite por IP; alternativa silenciosa disponible si se aprueba (D5).
- [Diferencias de tiempo de respuesta entre DNI nuevo y existente] → irrelevante para la demo; no se agregan retardos artificiales.
- [El job de notificaciones envía después de `pendiente_hasta` (p. ej. backend caído)] → `emitir` rechaza un vencimiento pasado y la notificación queda fallida; la solicitud ya expiró de todos modos (comportamiento correcto).
- [Enlace del email en desarrollo apunta a `:8080` sin SPA fallback] → documentar `APP_BASE_URL=http://localhost:5173` en desarrollo; fallback en C-12.
- [Conflictos de merge con C-07 (rama no mergeada) y con C-08 en `client.ts`/`bootstrap.ts`] → cambios aditivos y acotados; rebase al mergear C-07.
- [Cancelación del odontólogo no revoca los tokens del paciente] → el resumen muestra `ESTADO_NO_PERMITE` y el `POST` responde `422` sin consumir; sin riesgo de estado inconsistente.
- [Un escáner de email que ejecute JavaScript podría pulsar el botón] → el `POST` exige CSRF y un clic explícito; riesgo residual aceptado (RN-TK-04).

## Migration Plan

Sin migración de base. Despliegue = merge de la rama tras C-07. Rollback: revertir el merge; no deja datos incompatibles (los turnos `PUBLICA` y tokens usados son válidos para el esquema existente).

## Open Questions

- Texto final de los mensajes al paciente (202, 410, fuera de límite): se puede ajustar sin cambiar specs mientras siga siendo genérico.
