# Tasks

> **TDD estricto** (CONTRIBUTING §3.3 / `~/.claude/CLAUDE.md`): en cada tarea primero RED (escribir el test, ejecutarlo y confirmar que falla por la razón esperada), después GREEN (lo mínimo para pasar), TRIANGULATE (≥ 2 casos por comportamiento) y REFACTOR con los tests en verde. Entre corchetes van los escenarios de spec que cubre cada tarea (`capability › escenario`).
>
> **Governance CRITICO**: ninguna tarea de código arranca hasta que Thomas (P1), Martina (P2) y Juan Ignacio (P5) aprueben `design.md` y el contrato (grupo 1), con revisión de Jeremías (P3) para la parte de notificaciones (D2, D6). Decisiones 🔒 a aprobar: **D4** (limitador en memoria, claves y máximo de pendientes blando), **D5** (señuelo y `429` por identidad vs. `202` silencioso), **D6** (`identificar` en `TokenAccesoService`, consumo primero, límite inclusivo, resumen del `GET`) y **D7** (se mantiene CSRF en los `POST` públicos).
>
> ✅ **Aprobado** por P1 (Thomas), P2 (Martina), P3 (Jeremías) y P5 (Juan Ignacio) el 2026-11-22, con las decisiones de `design.md` tal como están (incluido el `429` por DNI/email de DD-14). **Por acuerdo del equipo, P2 (Martina) implementa todo C-09** (grupos 1–14, incluidos los marcados P1 y P5); el PR lo revisa otra persona.
>
> **Reparto y bloqueos** (rama `feat/c-09-publico-solicitud-acciones`, C-03, C-04, C-06 y C-07 ya en `main`):
>
> | Grupo | Dueño | Bloqueado por |
> |---|---|---|
> | 1 Contrato | P1 + P2 (revisa P5) | aprobación del design |
> | 2 Disponibilidad y prestaciones públicas | P1 | grupo 1 |
> | 3 Limitador de solicitudes | P1 | grupo 1 |
> | 4 Solicitud pública (servicio) | P1 | grupos 2 y 3 |
> | 5 Endpoint de solicitud e ITs | P1 | grupo 4 |
> | 6 Identificación de tokens | P2 | grupo 1 |
> | 7 Dominio de acciones | P2 | grupo 1 |
> | 8 Servicio de acciones | P2 | grupos 6 y 7 |
> | 9 Endpoints de acciones e ITs | P2 | grupo 8 |
> | 10 No filtrar el token | P2 | grupo 1 (10.1–10.4 no dependen de 8–9) |
> | 11 Router y cliente HTTP | P5 | grupo 1 |
> | 12 Feature `publico` | P5 | grupos 1 y 11 (contra fixtures; integración real tras el grupo 5) |
> | 13 Feature `acciones-token` | P5 | grupos 1 y 11 (contra fixtures; integración real tras el grupo 9) |
> | 14 Integración y cierre | todos | grupos 1–13 |
>
> P2 no depende del código de P1: sus ITs crean turnos con `ReservaTurnoService` (C-07) y emiten tokens con `TokenAccesoService.emitir` (C-03). P5 trabaja contra las fixtures del contrato.
>
> **Higiene de tests**: ITs sobre `AbstractWebIntegrationTest` / `AbstractTurnoIT` (contenedor compartido): no asumir base vacía, limpiar turnos/notificaciones/tokens creados (`TurnosFicticiosExtension`), sin `@DirtiesContext` ni `@MockBean` en ITs; tiempo con `MutableClock`/`FixedClockTestConfig`; solo datos ficticios (DNI `30…`, emails `@ejemplo.test`). Verificación backend: `cd backend && ./gradlew test`; frontend: `cd frontend && npm run typecheck && npm test`.

## 1. [P1 Thomas + P2 Martina] Contrato publicado (primero — desbloquea a P5)

- [x] 1.1 Escribir `openspec/changes/c-09-publico-solicitud-acciones/contrato-c09.md` (estilo de `contrato-c08.md`): convenciones (sin sesión, CSRF en `POST`, instantes UTC), los 5 endpoints de design D1 con request/response de ejemplo, tabla de errores por endpoint con `type` (`hueco-no-disponible` + `alternativas`, `demasiadas-solicitudes` + `Retry-After`, `token-invalido`, `transicion-invalida`, `limite-cancelacion`, `modificacion-concurrente`), forma de `ResumenAccion` y valores de `motivo`; verificar que P5 lo revisó y que cada escenario de `public-booking` y `token-actions` tiene su respuesta en el contrato _(contrato revisado y aprobado por P5 el 2026-11-22; implementado por P2 por acuerdo del equipo)_
- [x] 1.2 Crear las firmas compartidas sin lógica: records `SolicitudTurnoPublicaRequest` (Bean Validation con los mismos formatos que `PacienteRequest`, `sitioWeb` `@Size(max=200)` opcional), `SolicitudRecibidaResponse`, `PrestacionPublicaResponse`, `EjecutarAccionRequest(@NotNull PropositoToken accion)`, `ResumenAccionResponse`, `TokenIdentificado`, evento `SolicitudConfirmada(turnoId)`, `LimiteCancelacionException` (código `limite-cancelacion`) y `DemasiadasSolicitudesException` (código `demasiadas-solicitudes`); verificar con `./gradlew compileJava` y un unit test por excepción (código estable, mensaje sin datos)

## 2. [P1 Thomas] Disponibilidad y prestaciones públicas

- [x] 2.1 RED: `SolicitudReservaTest`: `SolicitudReserva.publica(pacienteId, prestacionId, inicio, anticipacionMin)` nace `PENDIENTE_CONFIRMACION`, origen `PUBLICA` y su política exige la anticipación; GREEN: agregar la fábrica; verificar que pasa
- [x] 2.2 RED: IT de `DisponibilidadService.huecosLibresPublicos`: anticipación 120 excluye un hueco a 60 min e incluye uno a 120; pendiente vigente ocupa y vencida no; prestación inactiva `422`, inexistente `404`, rango > 31 días `422` [public-booking › Huecos libres con anticipación pública, Pendientes vigentes ocupan y vencidas no, Prestación inactiva, inexistente o rango inválido]; GREEN; verificar que pasa
- [x] 2.3 RED → GREEN: IT de `alternativas(prestacionId, desde, maximo, política)` con política pública (excluye huecos antes de la anticipación) y la sobrecarga existente sin cambios (los ITs de C-07 siguen en verde); verificar con `./gradlew test --tests '*Disponibilidad*'`
- [x] 2.4 RED → GREEN: consulta de prestaciones activas ordenadas por nombre (servicio + test con una inactiva excluida y lista vacía) [public-booking › Solo prestaciones activas, Sin prestaciones activas]
- [x] 2.5 RED → GREEN: `PublicoApiIT` para `GET /api/v1/publico/prestaciones` y `GET /api/v1/publico/disponibilidad` sin sesión: `200`, solo `{id, nombre, duracionMin}` / `{inicio, fin}`, sin campos de turno ni paciente (aserción sobre el JSON crudo); verificar que pasa

## 3. [P1 Thomas] Limitador de solicitudes públicas (D4)

- [x] 3.1 RED: unit test `LimitadorSolicitudesPublicasTest` con `MutableClock`: límite 5 → la 6.ª de la misma IP lanza `DemasiadasSolicitudesException` con `retryAfterSegundos` hasta que sale el registro más antiguo; otra IP no afectada; tras 1 h la ventana se renueva [public-booking › Exceso por IP, La ventana se renueva]; GREEN; verificar que pasa
- [x] 3.2 RED → GREEN: triangular claves de identidad: mismo DNI con y sin puntos cuenta como la misma clave, email con mayúsculas igual, DNI y email independientes de la IP [public-booking › Exceso por DNI desde IP distintas]
- [x] 3.3 RED → GREEN: concurrencia (N hilos sobre la misma clave → exactamente `límite` aceptados) y tope de claves con purga de inactivas; verificar que pasa
- [x] 3.4 Leer el límite de `AppProperties.publicRateLimitPerHour` (sin propiedad nueva) y mapear `DemasiadasSolicitudesException` a `429` con `Retry-After` en `GlobalExceptionHandler`; verificar con un test WebMvc que el cuerpo del `429` es idéntico para límite de IP y de identidad

## 4. [P1 Thomas] Solicitud pública: servicio transaccional y fachada (D2, D5)

- [x] 4.1 RED: IT de `SolicitudPublicaService.solicitar`: crea turno `PENDIENTE_CONFIRMACION` origen `PUBLICA` con `pendiente_hasta = ahora + vencimiento_pendiente_min`; DNI existente reutiliza el paciente sin tocar su email; DNI nuevo lo crea; pendiente vencida previa queda `EXPIRADO` [public-booking › Solicitud exitosa, DNI existente no sobrescribe el contacto, DNI nuevo crea al paciente, Pendiente vencida liberada antes de solicitar]; GREEN; verificar que pasa
- [x] 4.2 RED → GREEN: paciente dado de baja → resultado genérico sin turno ni notificación y paciente sin cambios [public-booking › Paciente dado de baja]
- [x] 4.3 RED → GREEN: consulta en `TurnoRepository` de pendientes vigentes por DNI o email registrado (IT del repositorio con vencidas, confirmadas y de otro consultorio excluidas) y regla de máximo 2 → `DemasiadasSolicitudesException` sin efectos [public-booking › Tercera pendiente con el mismo DNI, Tercera pendiente con el mismo email y otro DNI, Pendientes vencidas o confirmadas no cuentan]
- [x] 4.4 RED → GREEN: `422` con field `inicio` fuera de horario, en bloqueo y antes de la anticipación; prestación inactiva `422`, inexistente `404` [public-booking › Fuera del horario de trabajo, Dentro de un bloqueo, Antes de la anticipación mínima]
- [x] 4.5 RED → GREEN: `SolicitudPublicaComandos` (sin `@Transactional`): orden IP → señuelo → identidad → servicio; señuelo con texto devuelve el genérico sin llamar al servicio pero consume cupo de IP; ante `HuecoNoDisponibleException` completa `alternativas` con la política pública y no queda paciente nuevo ni turno [public-booking › Señuelo completado, Señuelo vacío, Sin efectos ante un error, Hueco tomado entre la consulta y la solicitud]; unit test con dobles + IT de no persistencia
- [x] 4.6 RED → GREEN: `NotificacionesTurnoListener.alCrear` encola `CONFIRMAR_SOLICITUD` para pendientes de origen `PUBLICA` al email registrado, con datos `consultorio`/`inicio`/`prestacion` y un `EnlaceProgramado(CONFIRMAR_SOLICITUD, pendienteHasta)`, sin token en la fila; un turno del odontólogo no la encola; los tests de C-07 del listener siguen en verde [public-booking › Notificación encolada con enlace, Turno del odontólogo sin confirmación de solicitud] (revisa P3) _(revisión de P3: aprobado 2026-11-22; implementado por P2 por acuerdo del equipo)_

## 5. [P1 Thomas] Endpoint de solicitud e ITs de punta a punta

- [x] 5.1 RED → GREEN: `POST /api/v1/publico/solicitudes-turno` en `PublicoController` (IP por `getRemoteAddr()`, nunca `X-Forwarded-For`) → `202` con `SolicitudRecibidaResponse`; `SolicitudPublicaApiIT` sin sesión con CSRF: feliz, `400` por DNI/email inválidos o faltantes, `409` con `alternativas`, `422`, `403` sin CSRF [public-booking › Solicitud exitosa, DNI inválido, Falta el email]
- [x] 5.2 RED → GREEN: no enumeración: cuerpos byte a byte idénticos para DNI nuevo, existente, dado de baja y señuelo; sin `id` de turno ni paciente [public-booking › Cuerpo idéntico para DNI nuevo y existente]
- [x] 5.3 RED → GREEN: rate limit por IP (6.ª → `429` + `Retry-After`, sin turno) y por DNI desde IPs distintas (`with(remoteAddr(...))`) [public-booking › Exceso por IP, Exceso por DNI desde IP distintas]
- [x] 5.4 RED → GREEN: concurrencia con PostgreSQL real: dos solicitudes simultáneas al mismo horario con DNI distintos → exactamente un `202` y un `409`, un solo turno [public-booking › Solicitudes simultáneas del mismo horario]
- [x] 5.5 RED → GREEN: expiración con `MutableClock`: solicitud pendiente, avanzar 30 min, correr `ExpiracionTurnosService` → `EXPIRADO` y el hueco reaparece en `GET /publico/disponibilidad` [public-booking › Hueco liberado tras el vencimiento]
- [x] 5.6 RED → GREEN: `OutputCaptureExtension` en solicitud exitosa, limitada y con señuelo: los logs no contienen DNI, email, teléfono ni nombre enviados [public-booking › Solicitud registrada sin datos personales]

## 6. [P2 Martina] Identificación de tokens sin consumo (D6, C-03)

- [x] 6.1 RED: IT de `TokenAccesoService.identificar(valor, admitidos)`: token `CANCELAR_TURNO` vigente → propósito, referencia y vencimiento, `usado_en` sigue nulo; `ACEPTAR_AVISO` fuera del conjunto → `TokenInvalidoException` y sigue consumible; usado, vencido (con `MutableClock`) e inexistente → mismo rechazo [patient-access-tokens › Identificar un token vigente, Propósito fuera del conjunto, Token usado o vencido]; confirmar que falla
- [x] 6.2 GREEN: `buscarVigentePorHash(hash, ahora)` en `TokenAccesoRepository` y `JdbcTokenAccesoRepository` (devuelve `proposito`) + `identificar` `@Transactional(readOnly = true)`; verificar que 6.1 y los tests de C-03 pasan

## 7. [P2 Martina] Dominio de las acciones del paciente

- [x] 7.1 RED: `TurnoTest`: `limiteCancelacion(2)` = inicio − 2 h; `cancelarPorPaciente(ahora, 2)` permitido a 3 h y **exactamente** a 2 h (inclusivo), rechazado a 1 h 59 min con `LimiteCancelacionException` sin cambiar estado; deja `cancelado_por = PACIENTE` y motivo nulo; desde `ATENDIDO` → `TransicionInvalidaException` [token-actions › Cancelación dentro del límite, Cancelación justo en el límite, Cancelación fuera del límite]; GREEN; verificar que pasa
- [x] 7.2 RED → GREEN: `Turno.pendienteVencida(ahora)` (true si `PENDIENTE_CONFIRMACION` y `pendiente_hasta <= ahora`) con casos antes/en/después del instante
- [x] 7.3 RED → GREEN: `HuecoLiberado.de(turno, rango)` (fábrica estática que reemplaza el helper privado de `GestionTurnoService`); los tests de C-07 de eventos siguen en verde

## 8. [P2 Martina] Servicio de acciones por token (D6)

- [x] 8.1 RED: IT de `AccionTokenService.resumen`: `CANCELAR_TURNO` vigente → acción, `venceEn`, turno (consultorio, prestación, inicio, fin, estado) sin datos del paciente, `disponible` verdadero; fuera del límite → `FUERA_DE_LIMITE` con `limiteCancelacion`; turno cancelado por el odontólogo → `ESTADO_NO_PERMITE`; `ACEPTAR_AVISO` → `410`; tres llamadas no consumen [token-actions › Resumen de un token vigente, Mostrar varias veces no consume, Token de otro tipo de ruta, Cancelación fuera del límite, Turno ya cancelado por el odontólogo]; GREEN; verificar que pasa
- [x] 8.2 RED → GREEN: `ejecutar` con `CONFIRMAR_TURNO` (consume primero, `RESERVADO → CONFIRMADO`) y con acción distinta del propósito → `410` sin consumir; turno `CANCELADO` → `422` y el token sigue vigente; turno `ATENDIDO` → `422` [token-actions › Acción y consumo juntos, La acción falla y el token sigue vigente, Acción que no coincide con el propósito, Confirmación de asistencia, Turno ya atendido]
- [x] 8.3 RED → GREEN: `CONFIRMAR_SOLICITUD`: a tiempo → `RESERVADO` + evento `SolicitudConfirmada`; con `pendiente_hasta` ya pasado (sin expiración corrida) → `410` y sigue pendiente [token-actions › Confirmación a tiempo, Solicitud vencida]
- [x] 8.4 RED → GREEN: listener `alConfirmarSolicitud` (`BEFORE_COMMIT`) encola `CONFIRMACION_TURNO` para ese turno; IT que verifica la fila encolada tras confirmar (revisa P3) [token-actions › Confirmación a tiempo] _(revisión de P3: aprobado 2026-11-22; implementado por P2 por acuerdo del equipo)_
- [x] 8.5 RED → GREEN: `CANCELAR_TURNO`: `CANCELADO` con `PACIENTE`, eventos `TurnoCancelado` + `HuecoLiberado` con el intervalo, `CANCELACION` encolada, los demás tokens del turno revocados; confirmar no revoca el de cancelar [token-actions › Hueco liberado y tokens invalidados, Confirmar no invalida la cancelación]

## 9. [P2 Martina] Endpoints de acciones e ITs de concurrencia

- [x] 9.1 RED → GREEN: `AccionesController` `GET/POST /api/v1/acciones/{token}`; `AccionesApiIT` sin sesión: `200` del resumen, `POST` con CSRF ejecuta, `400` con `accion` ausente o inválida, `410` idéntico para usado/vencido/inexistente en `GET` y `POST` [token-actions › Token usado, Token vencido]
- [x] 9.2 RED → GREEN: CSRF: `POST` sin `X-XSRF-TOKEN` → `403`, turno sin cambios y token vigente; `GET` sin cookies entrega `XSRF-TOKEN` y un `POST` con ese valor no es rechazado por CSRF [token-actions › POST sin cabecera CSRF, GET entrega la cookie CSRF]
- [x] 9.3 RED → GREEN: dos `POST` simultáneos con el mismo token `CANCELAR_TURNO` (PostgreSQL real, `CountDownLatch`) → exactamente un `200` y un `410`, un único `HuecoLiberado` y una única `CANCELACION` [token-actions › Mismo token dos veces a la vez]
- [x] 9.4 RED → GREEN: `CONFIRMAR_TURNO` y `CANCELAR_TURNO` simultáneos sobre el mismo turno: el estado final es coherente con las respuestas (cancelación `200` → `CANCELADO`; `409` → `CONFIRMADO` con `CANCELAR_TURNO` vigente) [token-actions › Confirmar y cancelar a la vez]
- [x] 9.5 RED → GREEN: límite de cancelación con `MutableClock` por la API: dentro → `200`, en el límite → `200`, fuera → `422 limite-cancelacion` y token vigente [token-actions › Cancelación dentro del límite, Cancelación justo en el límite, Cancelación fuera del límite]

## 10. [P2 Martina] No filtrar el token (D7, D8)

- [x] 10.1 RED → GREEN: `Referrer-Policy: no-referrer` global en `SecurityConfig`; IT que lo verifica en `GET /api/v1/acciones/{token}` (`200` y `410`), `GET /` (perfil con frontend o recurso estático de prueba) y una ruta privada con sesión [api-access-control › Cabecera en la API por token, Cabecera en la página del enlace, Cabecera en rutas privadas]
- [x] 10.2 RED → GREEN: `instance` constante para todo `ProblemDetail` de `/api/v1/acciones/**` y `/api/v1/avisos/**` (advice del handler + handlers de seguridad 401/403); IT: `422` y `403` de un `POST` por token no contienen el token; `409` de `POST /api/v1/turnos` conserva su `instance` [api-error-handling › Regla de negocio en una acción por token, CSRF rechazado en una acción por token, Otras rutas conservan su instance]
- [x] 10.3 RED → GREEN: test de configuración: perfiles `dev` y `demo` sin `DEBUG`/`TRACE` en `org.springframework.web`, sin `server.tomcat.accesslog.enabled` y sin `spring.mvc.log-request-details`; verificar que pasa
- [x] 10.4 RED → GREEN: `OutputCaptureExtension` en `GET`/`POST` exitosos y con token inválido: los logs no contienen el valor del token [token-actions › Acción registrada sin token]
- [x] 10.5 Documentar la decisión de CSRF (D7) en el contrato (grupo 1) y en `knowledge-base/10_preguntas_abiertas.md` (PA-02, nota de implementación); verificar que el contrato y la KB mencionan que los `POST` públicos exigen `X-XSRF-TOKEN`

## 11. [P5 Juan Ignacio] Router con parámetros y cliente HTTP

- [x] 11.1 RED: `router.test.ts`: `/acciones/:token` resuelve `/acciones/abc123` con `params.token = 'abc123'` sin sesión ni layout autenticado; `/acciones/` → no encontrada; las rutas exactas existentes siguen igual [web-app-shell › Ruta pública con segmento variable, Segmento variable vacío, Ruta pública sin sesión]; GREEN; verificar con `npm test`
- [x] 11.2 RED → GREEN: `client.test.ts`: `ApiError` expone `code` (último segmento de `type`), `extensions` (`unknown`) con `alternativas`, y `retryAfterSeconds` en `429` (ausente/no numérico → indefinido); los tests existentes siguen en verde (coordinar con C-08) [web-http-client › Código y propiedades adicionales, Retry-After en un 429, Retry-After ausente o inválido]
- [x] 11.3 Agregar `<meta name="referrer" content="no-referrer">` a `index.html` con un test que lo lea [web-token-actions › Política de referer en el documento]; verificar con `npm test && npm run typecheck`

## 12. [P5 Juan Ignacio] Feature `publico`

- [x] 12.1 RED → GREEN: `features/publico/test-fixtures.ts` tipadas desde el contrato y `publico-api.test.ts`: `listarPrestaciones`, `consultarDisponibilidad`, `solicitarTurno` con resultado discriminado (`aceptada | conflicto(alternativas) | limite(retryAfterSeconds) | invalida(fieldErrors) | horarioInvalido`) y narrowing de `alternativas` desde `unknown`; sin `any`
- [x] 12.2 RED → GREEN: `validators.test.ts`: DNI 7–8 dígitos con o sin puntos (`12AB` y `123456` inválidos), email, teléfono y obligatorios [web-public-booking › DNI inválido en el cliente]
- [x] 12.3 RED → GREEN: `publico-page.test.ts` (jsdom): prestación → día → huecos en hora de Buenos Aires (reutilizar `zoned-time`), sin huecos → mensaje y sin formulario; formulario con señuelo `sitioWeb` oculto (`tabindex=-1`, `aria-hidden`, `autocomplete=off`) enviado vacío [web-public-booking › Huecos mostrados en hora local, Sin horarios, Señuelo no visible]
- [x] 12.4 RED → GREEN: resultados: `202` → mensaje genérico y formulario limpio; `409` → alternativas seleccionables conservando datos; `422 inicio` → aviso y recarga de huecos; `429` → minutos de espera y envío deshabilitado; red → genérico con reintento [web-public-booking › Solicitud aceptada, Conflicto con alternativas, Límite excedido, Horario rechazado por la agenda]
- [x] 12.5 RED → GREEN: ruta `/publico` pública en `bootstrap.ts` (reemplaza el placeholder) sin layout autenticado y llamando solo a `/api/v1/publico/**`; mocks de desarrollo para trabajar sin backend [web-public-booking › Acceso sin sesión]; verificar con `npm test && npm run typecheck`

## 13. [P5 Juan Ignacio] Feature `acciones-token`

- [x] 13.1 RED → GREEN: `acciones-api.test.ts` con fixtures del contrato: `obtenerResumen(token)` y `ejecutar(token, accion)` tipados; `410` como resultado `noDisponible`; token codificado en la URL con `encodeURIComponent`
- [x] 13.2 RED → GREEN: `acciones-page.test.ts`: abrir la página muestra el resumen en hora local y un único botón según la acción, sin `POST`; `disponible: false` con `FUERA_DE_LIMITE` → explicación sin botón [web-token-actions › Resumen de cancelación, Acción no disponible]
- [x] 13.3 RED → GREEN: pulsar ejecuta un solo `POST` aunque haya doble clic, muestra el resultado y no vuelve a ofrecer el botón; `410` → página genérica con enlace a `/publico` sin datos del turno; `422`/`409` → "ya no admite la acción" y resumen recargado [web-token-actions › Confirmar la solicitud, Doble clic, Enlace vencido, Estado cambiado al ejecutar]
- [x] 13.4 RED → GREEN: ruta `/acciones/:token` pública en `bootstrap.ts` sin layout autenticado [web-token-actions › Acceso sin sesión]; verificar con `npm test && npm run typecheck`

## 14. [Todos] Integración y cierre

- [x] 14.1 Integración backend + frontend en desarrollo (`APP_BASE_URL=http://localhost:5173`, canal de email en memoria o SMTP de prueba): solicitar desde `/publico`, abrir el enlace de `CONFIRMAR_SOLICITUD`, confirmar y ver el turno `RESERVADO` en la agenda; emitir a mano tokens `CONFIRMAR_TURNO`/`CANCELAR_TURNO` y ejecutarlos desde `/acciones/{token}`; verificar en las herramientas del navegador que no se envía `Referer` y que el token no aparece en logs — Prueba manual omitida por decisión de P2 (Martina) el 2026-11-22, igual que la 11.1 de C-03; cubierta por los ITs de punta a punta del backend y los tests de Vitest de ambas pantallas (backend 1001 tests, frontend 347, typecheck y build OK).
- [x] 14.2 Actualizar el contrato `contrato-c09.md` con cualquier diferencia entre lo publicado en el grupo 1 y lo implementado (tabla "diferencias", como en C-07); verificar que P5 la revisó
- [x] 14.3 Verificación completa: `cd backend && ./gradlew test` y `cd frontend && npm run typecheck && npm test` en verde; `openspec validate c-09-publico-solicitud-acciones --strict` válido

> **Nota 14.1 (pendiente de prueba manual)**: no se ejecutó en un navegador real (requiere Postgres + SMTP/canal en memoria con credenciales que no se tocan desde la sesión del agente). Verificación automatizada equivalente: `SolicitudPublicaApiIT` (solicitud → `PENDIENTE_CONFIRMACION` + email `CONFIRMAR_SOLICITUD`), `AccionesApiIT` (confirmar solicitud → `RESERVADO`, confirmar y cancelar con tokens emitidos), `RutasPorTokenIT` (`Referrer-Policy: no-referrer`, token fuera de cuerpos y logs), `index-html.test.ts` (meta `referrer`), `bootstrap.test.ts` (`/publico` y `/acciones/:token` sin sesión ni layout) y los tests de páginas (un solo `POST`, mensajes). Falta confirmar a mano con `APP_BASE_URL=http://localhost:5173`: que el navegador no envíe `Referer` al abrir el enlace y que el token no aparezca en logs.

## Archivos compartidos del frontend tocados por C-09 (para C-08 / Juan Ignacio)

- `frontend/index.html`: `<meta name="referrer" content="no-referrer">` (el token de `/acciones/:token` no sale en `Referer`).
- `frontend/src/shared/router/router.ts`: rutas con segmentos variables (`/acciones/:token`) y `RouteContext.params` (`{}` en rutas exactas); las exactas tienen prioridad.
- `frontend/src/shared/http/client.ts`: `ApiError` (`kind: 'http'`) suma `extensions` (miembros no estándar, p. ej. `alternativas`), `code` (último segmento de `type`) y `retryAfterSeconds` (cabecera `Retry-After` de un `429`).
- `frontend/src/shared/http/problem.ts`: `extensionMembers()` para extraer esos miembros.
- `frontend/src/bootstrap.ts`: `BootstrapDeps` suma `publicoApi?` y `accionesApi?` (dobles para tests); registra `createPublicoRoute` (reemplaza el placeholder de `/publico`) y `createAccionesRoute` (`/acciones/:token`), ambas `public`.
- `frontend/src/main.ts`: con `VITE_CONFIG_MOCKS=true` (solo desarrollo) también inyecta `dev/publico-mocks.ts` y `dev/acciones-mocks.ts`.
- `frontend/src/dev/config-mocks.ts` y los tests `features/config/form-errors.test.ts`, `features/config/pages/config-page.test.ts`, `features/notificaciones/notificaciones-api.test.ts`, `features/notificaciones/notificaciones-page.test.ts`, `pages/login-page.test.ts`, `shared/layout/app-layout.test.ts`: solo se agregó `extensions: {}` a los `ApiError` de fixtures por el campo nuevo.
- Tests nuevos/ampliados: `shared/router/router.test.ts`, `shared/http/client.test.ts`, `index-html.test.ts`, `bootstrap.test.ts`.

## Workflow follow-up

- Revisión del PR por otra persona (CONTRIBUTING); mergear después de C-07 (rebase si hace falta).
- `/opsx:archive c-09-publico-solicitud-acciones` y marcar `[x]` en `CHANGES.md`.
- Avisar a C-10 (consume `HuecoLiberado` con `cancelado_por = PACIENTE` y reutiliza el limitador/señuelo para la lista de espera pública) y a C-11 (emite `CONFIRMAR_TURNO`/`CANCELAR_TURNO` hacia `/acciones/{token}`); C-12 agrega el SPA fallback para los enlaces en `demo`.
