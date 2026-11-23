# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos: `specs/waiting-list`, `specs/token-actions`, `specs/web-token-actions`, `specs/web-waiting-list`.

Lo que ya existe en `main` y C-10 **reutiliza sin modificar** (verificado en el código):

| Pieza | Clase / método real | Notas |
|---|---|---|
| Evento | `domain.turno.evento.HuecoLiberado(long consultorioId, long turnoOrigenId, long prestacionId, Instant inicio, Instant fin)` | Lo publican `GestionTurnoService` (cancelar/mover) y `AccionTokenService.cancelar` dentro de su transacción. |
| Origen | `OrigenTurno.LISTA_ESPERA` y `ck_turno_origen` (V9) | Ya existe. |
| Reserva | `ReservaTurnoService.reservar(SolicitudReserva)` → `TraductorViolacionAgenda` traduce `23P01`/`40P01` de `ex_turno_sin_superposicion` a `HuecoNoDisponibleException` (409) | Publica `TurnoCreado`; `NotificacionesTurnoListener.alCrear` ya encola `CONFIRMACION_TURNO` si nace `RESERVADO`. |
| Tokens | `TokenAccesoService.emitir/verificar/identificar/consumir/revocar`; `PropositoToken.ACEPTAR_AVISO` y `CONFIRMAR_LISTA_ESPERA`; `ReferenciaToken.Aviso` y `ReferenciaToken.EntradaListaEspera` | Ya existen, también en los CHECK de V7. Inválido → `TokenInvalidoException` → `410` (`GlobalExceptionHandler`). |
| Outbox | `NotificationService.encolar(TipoNotificacion, destinatario, ReferenciaToken, datos, List<EnlaceProgramado>)` | `TipoNotificacion.AVISO_HUECO` / `CONFIRMAR_LISTA_ESPERA` y sus plantillas (`PlantillaNotificacion`, variables `consultorio`,`prestacion`[,`inicio`]) ya existen. El token se emite **al enviar** (`EmisorEnlacesConTokens`), con ruta `/avisos/` para `ACEPTAR_AVISO` y `/acciones/` para el resto. Idempotencia: `uq_notificacion_aviso`, `uq_notificacion_entrada_tipo`. |
| Anti-abuso | `LimitadorSolicitudesPublicas.registrarIp(ip)` / `registrarIdentidad(dni, email)`; patrón `SolicitudPublicaComandos` (sin `@Transactional`, IP = `getRemoteAddr()`); `SolicitudRecibidaResponse.generica()` | |
| Paciente | `PacienteService.buscarOCrearPorDni(DatosPaciente)` → `ResultadoBuscarOCrear(PacienteVista paciente, boolean creado)` | No modifica datos de un existente. |
| Consultorio | `ConsultorioActual.id()`, `ConsultorioService.parametrosVigentes()` → `vencimientoAvisoMin()`, `anticipacionMinReservaMin()` | |
| Errores | `ConflictoException` (409), `ReglaNegocioException` / `ValidacionDominioException`+`ErrorCampo` (422), `RecursoNoEncontradoException` (404), `DemasiadasSolicitudesException` (429), `OptimisticLockingFailureException` → 409, `RestriccionesDb.viola(ex, nombre)` | |
| Seguridad | `SecurityConfig` ya hace `permitAll` de `/api/v1/publico/**`, `/api/v1/acciones/**` y `/api/v1/avisos/**`; el resto de `/api/**` exige sesión; CSRF en escrituras | Sin cambios. |
| Scheduler | `ExpiracionPendientesScheduler` (`@Scheduled(fixedDelayString=...)`, nunca propaga, apagable con `app.scheduling.enabled=false`) | Se copia el patrón. |
| Base | `AuditableEntity` (`id`, `creado_en`, `actualizado_en`); migraciones V1–V4, V6–V9 (V5 reservada a C-05) | Próximo libre: **V10**. |

## Goals / Non-Goals

**Goals:**
- Flujo completo inscripción → confirmación → aviso → aceptación con la menor superficie nueva posible, reutilizando los contratos de C-03/C-04/C-07/C-09.
- "El primero gana" garantizado por la base (bloqueo de filas + `EXCLUDE`), nunca por un chequeo solo en Java.

**Non-Goals:**
- Todo lo listado como "etapa posterior" en `proposal.md` (franja, filtros, alta manual, `VENCIDA` por fecha, cancelación por enlace del paciente).
- Reintentar la generación de avisos si el listener falla (se loguea; ver Riesgos).

## Decisions

### D1. Esquema V10 (`V10__lista_espera.sql`)
- `entrada_lista_espera`: `id`, `consultorio_id` FK, `paciente_id` FK, `prestacion_id` FK, `fecha_desde date`, `fecha_hasta date`, `estado text` (CHECK 5 estados), `origen text` (CHECK `PUBLICA|ODONTOLOGO`, solo se usa `PUBLICA`), `pendiente_hasta timestamptz NULL`, `turno_resultante_id` FK `turno` NULL, `version bigint`, `creado_en`, `actualizado_en`. CHECK `fecha_desde <= fecha_hasta`.
- Único parcial `uq_entrada_lista_espera_vigente (paciente_id, prestacion_id) WHERE estado IN ('PENDIENTE_CONFIRMACION','ACTIVA')`. Índice `(consultorio_id, prestacion_id, estado)` para la búsqueda de compatibles.
- `aviso_hueco`: `id`, `consultorio_id`, `entrada_lista_espera_id` FK, `turno_origen_id` FK `turno`, `prestacion_id` FK, `inicio`, `fin` (`timestamptz`, CHECK `inicio < fin`), `estado` (CHECK 4 estados), `vence_en`, `version`, auditoría. Único `(turno_origen_id, inicio, entrada_lista_espera_id)` (idempotencia del listener y RN-LE-08 junto con el único de entradas); índice `(turno_origen_id, inicio)` (el "hueco"); índice parcial `(vence_en) WHERE estado='ENVIADO'` para el barrido.
- FK diferidas desde C-03: `token_acceso.aviso_id` / `entrada_lista_espera_id` → `ON DELETE CASCADE`; `notificacion.aviso_id` / `entrada_lista_espera_id` → `ON DELETE RESTRICT`.
- Entidades `EntradaListaEspera` y `AvisoHueco` extienden `AuditableEntity`, `@Version`, enums `@Enumerated(STRING)` (`EstadoEntrada`, `OrigenEntrada`, `EstadoAviso`), transiciones en la entidad (`confirmar`, `cancelar`, `cubrir`, `vencerPendiente`; `aceptar`, `cerrar`, `expirar`).
- *Alternativa descartada*: tabla `hueco` intermedia; la clave `(turno_origen_id, inicio)` alcanza para agrupar los avisos de un hueco.

### D2. Inscripción pública
- `PublicoController` suma `POST /api/v1/publico/lista-espera` (`202`, `SolicitudRecibidaResponse.generica()`), request `InscripcionListaEsperaRequest` (mismas validaciones que `SolicitudTurnoPublicaRequest`, `sitioWeb` señuelo, `prestacionId`, `fechaDesde`, `fechaHasta`).
- `ListaEsperaPublicaComandos` (sin `@Transactional`, espejo de `SolicitudPublicaComandos`): `registrarIp` → señuelo (descarta y loguea sin datos) → `registrarIdentidad` → `ListaEsperaService.inscribir(...)`; captura `EntradaDuplicadaException` y la convierte en el mismo `202` (sin enumeración, DD-14).
- `ListaEsperaService.inscribir` (`@Transactional`): valida rango y prestación activa (`PrestacionRepository.findByIdAndConsultorio_Id`), `buscarOCrearPorDni` (paciente de baja → no crea, `202`), vence las pendientes cuyo `pendiente_hasta` pasó para ese paciente+prestación (UPDATE masivo), inserta con `saveAndFlush` y traduce la violación de `uq_entrada_lista_espera_vigente` (`RestriccionesDb.viola`) a `EntradaDuplicadaException extends ConflictoException` (409). Encola `CONFIRMAR_LISTA_ESPERA` en la misma transacción con enlace que vence en `pendiente_hasta`.
- **Plazo de confirmación: 24 h fijas** (constante `PLAZO_CONFIRMACION`). *Alternativa*: `vencimiento_pendiente_min` (30 min) es corto para un email; parametrizarlo queda para etapa posterior.

### D3. Confirmación por `/acciones/{token}` (cambio aditivo en C-09)
- `AccionTokenService.ACCIONES` suma `CONFIRMAR_LISTA_ESPERA`. En `resumen` y `ejecutar`, si la referencia es `ReferenciaToken.EntradaListaEspera` se delega en `ListaEsperaService.resumenConfirmacion(ref)` / `confirmar(ref)` (misma transacción, el token se consume primero como hoy).
- `confirmar`: si la entrada no está `PENDIENTE_CONFIRMACION` o venció `pendiente_hasta` → `TokenInvalidoException` (revierte el consumo, `410`); si no, `ACTIVA`.
- `ResumenAccionResponse` suma el componente `EntradaResumen entrada` (consultorio, prestación, `fechaDesde`, `fechaHasta`, `EstadoEntrada`); `turno` es `null` para esta acción. Cambio compatible del JSON para las acciones de turno (`entrada: null`).
- *Alternativa descartada*: endpoint propio `/lista-espera/confirmar/{token}`; duplicaría la página y el contrato de C-09, y `EmisorEnlacesConTokens` ya arma `/acciones/` para este propósito.

### D4. Avisos al liberarse un hueco
- `ListaEsperaHuecoListener` con `@TransactionalEventListener(phase = AFTER_COMMIT)` sobre `HuecoLiberado` llama a `AvisoHuecoService.generarAvisos(evento)` con `@Transactional(propagation = REQUIRES_NEW)` (en `AFTER_COMMIT` la transacción original ya no admite escrituras). Si la cancelación se revierte, no hay evento.
- Compatibilidad (spec "Compatibilidad…"): consulta JPQL `EntradaListaEsperaRepository.buscarCompatibles(consultorioId, prestacionId, fechaLocal)` con `estado = ACTIVA`, `fechaDesde <= fechaLocal <= fechaHasta`, paciente activo; `fechaLocal` = `inicio` en `America/Argentina/Buenos_Aires`. Antes, si `inicio <= ahora + anticipacionMinReservaMin` no se hace nada (RN-LE-10). La duración siempre encaja porque la prestación es la misma (variante por duración: etapa posterior). Se encapsula en `CompatibilidadHueco` (función pura, testeable sin base) más la consulta.
- Por cada entrada: `AvisoHueco` `ENVIADO` con `vence_en = min(ahora + vencimientoAvisoMin, inicio)` y `encolar(AVISO_HUECO, email, new ReferenciaToken.Aviso(id), {consultorio, inicio, prestacion}, [EnlaceProgramado(ACEPTAR_AVISO, vence_en)])`. Paciente sin email: se omite (log con id). Todos se encolan en la misma transacción → salen en el mismo ciclo del procesador (a la vez).
- `Clock` inyectado en todos los servicios.

### D5. Aceptación concurrente (el primero gana) — **requiere aprobación**
`AvisoController`: `GET /api/v1/avisos/{token}` → `ResumenAvisoResponse(consultorio, prestacion, inicio, fin, venceEn, disponible)`; `POST /api/v1/avisos/{token}/aceptar` (CSRF) → `AvisoAceptadoResponse(prestacion, inicio, fin, estado)`.

`AvisoComandos` (sin `@Transactional`, como `SolicitudPublicaComandos`) envuelve a `AvisoHuecoService.aceptar(token)` (`@Transactional`), cuyo orden es:
1. `tokens.verificar(token, ACEPTAR_AVISO)` (solo lectura) → id del aviso.
2. `avisos.bloquearDelHueco(consultorioId, turnoOrigenId, inicio)`: `@Lock(PESSIMISTIC_WRITE)` (`SELECT … FOR UPDATE`) de **todos** los avisos del hueco ordenados por id. Serializa las aceptaciones del mismo hueco en la base.
3. Aviso vencido (`vence_en <= ahora`) → `AvisoVencidoException`; estado ≠ `ENVIADO` o entrada ≠ `ACTIVA` → `AvisoNoDisponibleException extends ConflictoException` (409).
4. `tokens.consumir(token, ACEPTAR_AVISO)`.
5. `reservas.reservar(SolicitudReserva.desdeListaEspera(pacienteId, prestacionId, inicio))` → `RESERVADO`, origen `LISTA_ESPERA`, `PoliticaAnticipacion.odontologo()` (la anticipación ya se exigió al crear el aviso y `vence_en <= inicio`). **La superposición la decide `ex_turno_sin_superposicion`**: si una reserva normal tomó el rango → `HuecoNoDisponibleException` (409) y se revierte todo, incluido el consumo.
6. `aviso.aceptar()`, `entrada.cubrir(turnoId)`, UPDATE masivo de los demás avisos `ENVIADO` del hueco a `CERRADO` (incrementando `version`; las filas ya están bloqueadas).
7. La confirmación al ganador la encola `NotificacionesTurnoListener.alCrear` (ya existente, `BEFORE_COMMIT`).

`AvisoComandos` traduce, en una **transacción nueva** tras el rollback: `AvisoVencidoException` → marca el aviso `EXPIRADO` y lanza `TokenInvalidoException` (410); `HuecoNoDisponibleException` → cierra (`CERRADO`) los avisos `ENVIADO` del hueco y relanza (409).

Orden de bloqueos: avisos del hueco → token propio → inserción en `turno`. El ganador **no revoca** los tokens de los perdedores (tocar sus filas de token invertiría el orden y podría interbloquear con un perdedor que ya consumió el suyo); sus avisos `CERRADO` responden `409` en el `POST` y `disponible=false` en el `GET`, y el token vence solo en `vence_en`.

*Alternativas descartadas*: (a) solo `@Version` en el aviso: no coordina avisos distintos del mismo hueco; (b) depender solo del `EXCLUDE`: funciona para el turno pero dejaría dos entradas intentando cubrirse y avisos sin cerrar coherentemente; (c) consumir el token antes de bloquear: obliga a revocar los ajenos con riesgo de interbloqueo.

### D6. Vencimiento de avisos
- Perezoso: `GET` y `POST` detectan `vence_en <= ahora` (D5 paso 3; el `GET` usa el mismo `AvisoComandos`) → `EXPIRADO` + `410`.
- Barrido: `AvisoHuecoService.expirarVencidos()` (UPDATE masivo `ENVIADO`→`EXPIRADO` con `vence_en <= ahora`) invocado por `AvisosVencidosScheduler` (`@Scheduled(fixedDelayString = "${app.lista-espera.expiracion-intervalo:PT1M}")`), copia de `ExpiracionPendientesScheduler`.

### D7. Odontólogo
- `ListaEsperaController` (`/api/v1/lista-espera`, con sesión): `GET` → `List<EntradaListaEsperaResponse>(id, paciente "Apellido, Nombre", prestacion, fechaDesde, fechaHasta, estado, creadoEn)` ordenado por `creado_en DESC`; `DELETE /{id}` → `204`.
- `ListaEsperaService.cancelar(id)`: 404 si no existe en el consultorio; `entrada.cancelar()` desde `PENDIENTE_CONFIRMACION|ACTIVA`, si no `ReglaNegocioException` (422); `tokens.revocar(new ReferenciaToken.EntradaListaEspera(id), Optional.of(CONFIRMAR_LISTA_ESPERA))`. Avisos `ENVIADO` de una entrada cancelada responden `409` (D5 paso 3).

### D8. Frontend
- Feature nueva `features/lista-espera/`: `lista-espera-api.ts` (tipos + narrowing desde `unknown`, sin `any`), `inscripcion-page.ts` (ruta pública `/publico/lista-espera`; reutiliza `features/publico/validators.ts` y `listarPrestaciones` de `publico-api.ts`, señuelo oculto como en `publico-page.ts`), `aviso-page.ts` (ruta pública `/avisos/:token`, mismo esquema que `acciones-page.ts`: resumen → botón → estados `reservado | noDisponible(409) | vencido(410)`), `lista-espera-page.ts` (ruta privada `/lista-espera`, tabla + "Retirar"). Registro en `bootstrap.ts` y enlace en `app-layout.ts`; enlace "Anotarme en lista de espera" desde `/publico`.
- `acciones-token`: `ACCIONES` suma `CONFIRMAR_LISTA_ESPERA`; `ResumenAccion.turno` pasa a `TurnoResumen | null` y se agrega `entrada: EntradaResumen | null`; la página muestra la variante de inscripción.
- Mocks de desarrollo en `dev/` siguiendo `publico-mocks.ts` / `acciones-mocks.ts`.

## Risks / Trade-offs

- [El listener `AFTER_COMMIT` falla (p. ej. base caída) y no se generan avisos] → se loguea solo el id del turno; el hueco sigue libre para reserva normal (RN-LE-07). Sin reintento en el MVP.
- [Bloqueo `FOR UPDATE` serializa aceptaciones del mismo hueco] → son pocas filas y transacciones cortas; es exactamente la semántica buscada.
- [Tokens de perdedores siguen "vigentes" hasta `vence_en`] → el estado del aviso manda: `409`/`disponible=false`; el token nunca crea turno.
- [`entrada` nueva en `ResumenAccionResponse` y `turno` nullable rompen el narrowing actual del front] → se ajusta `esResumenAccion` en la misma tarea, con tests.
- [Duplicado invisible para el paciente] → decisión deliberada (no enumeración); el odontólogo ve las entradas en su listado.
- [24 h de plazo de confirmación fijo] → suposición documentada; parametrizar es etapa posterior.

## Migration Plan

V10 es aditiva (dos tablas y cuatro FK sobre columnas hoy siempre nulas para avisos/entradas, así que no hay datos que violen las FK). Rollback solo en desarrollo local: quitar las FK, `DROP TABLE aviso_hueco, entrada_lista_espera` y borrar la fila `10` de `flyway_schema_history` (documentado en el encabezado del SQL).

## Open Questions

- Si la demo necesita cargar entradas sin email, el alta manual del odontólogo (etapa posterior) lo resolvería; no cambia este diseño.
