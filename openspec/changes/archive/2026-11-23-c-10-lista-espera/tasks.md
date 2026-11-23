# Tasks

> Responsable: P1 (Thomas) salvo indicación. TDD estricto: cada tarea es RED (test que falla) → GREEN → TRIANGULATE → REFACTOR. Tests de integración con Testcontainers (`AbstractIntegrationTest` / `AbstractTurnoIT` / `AbstractWebIntegrationTest`), nunca H2. `Clock` fijo en los tests de vencimiento. Solo datos ficticios.
> Governance ALTO: no empezar hasta que una persona apruebe `design.md` (en especial D2 duplicado→202, D4 compatibilidad, D5 concurrencia, D6 vencimiento).

## 1. Esquema y entidades

- [x] 1.1 RED: test de migración (Testcontainers) que verifica tablas `entrada_lista_espera` y `aviso_hueco`, los CHECK, el único parcial `uq_entrada_lista_espera_vigente` (segunda entrada `ACTIVA` del mismo paciente+prestación falla; una `CUBIERTA` no) y las FK diferidas de `token_acceso`/`notificacion`. GREEN: `V10__lista_espera.sql` según design D1 con encabezado de rollback. Verifica: test verde y arranque con `ddl-auto=validate`.
- [x] 1.2 RED: tests unitarios de transiciones de `EntradaListaEspera` (`confirmar`, `cancelar`, `cubrir`, `vencerPendiente`) y `AvisoHueco` (`aceptar`, `cerrar`, `expirar`), incluidas las inválidas. GREEN: entidades (`AuditableEntity`, `@Version`), enums `EstadoEntrada`, `OrigenEntrada`, `EstadoAviso` y repositorios. Verifica: tests verdes + test de repositorio que persiste y relee ambas entidades.

## 2. Inscripción pública y confirmación

- [x] 2.1 RED: test de servicio `ListaEsperaService.inscribir` (crea `PENDIENTE_CONFIRMACION` con `pendiente_hasta` = ahora + 24 h, encola `CONFIRMAR_LISTA_ESPERA` con enlace; paciente existente no se modifica; paciente de baja no crea; rango inválido 422; prestación inexistente 404 / inactiva 422). GREEN: servicio con `PacienteService.buscarOCrearPorDni` y `NotificationService.encolar`. Verifica: tests verdes.
- [x] 2.2 RED: duplicado → `EntradaDuplicadaException` (409) decidido por `uq_entrada_lista_espera_vigente` (traducción con `RestriccionesDb.viola`); pendiente vencida pasa a `VENCIDA` y permite reinscribir. GREEN. Verifica: tests de integración verdes.
- [x] 2.3 RED: test web de `POST /api/v1/publico/lista-espera` (202 genérico; duplicado también 202; señuelo 202 sin crear nada; 429 por IP con `Retry-After`; 400 por validación; sin sesión y con CSRF). GREEN: `InscripcionListaEsperaRequest`, `ListaEsperaPublicaComandos` (reusa `LimitadorSolicitudesPublicas`) y endpoint en `PublicoController`. Verifica: tests verdes.
- [x] 2.4 RED: tests de `/api/v1/acciones/{token}` con `CONFIRMAR_LISTA_ESPERA` (GET devuelve `entrada` y `turno` nulo sin consumir; POST → `ACTIVA` y token usado; entrada `CANCELADA` → 410 sin activar) y regresión de las acciones de turno (`entrada` nula). GREEN: `ACCIONES` + delegación en `AccionTokenService`, `EntradaResumen` en `ResumenAccionResponse`. Verifica: tests nuevos y existentes de acciones verdes.

## 3. HuecoLiberado → avisos

- [x] 3.1 RED: tests unitarios de `CompatibilidadHueco` (misma prestación, fecha local AR dentro del rango incluidos los bordes, fuera de rango, hueco sin anticipación mínima, `vence_en = min(ahora + vencimientoAvisoMin, inicio)`). GREEN: función pura. Verifica: tests verdes.
- [x] 3.2 RED: test de integración de `AvisoHuecoService.generarAvisos`: 3 entradas `ACTIVA` compatibles → 3 avisos `ENVIADO` + 3 `AVISO_HUECO` pendientes con enlace `ACEPTAR_AVISO`; entradas pendientes/canceladas/otra prestación/paciente de baja no reciben; sin compatibles no crea nada; reprocesar el mismo evento no duplica. GREEN: `buscarCompatibles` (JPQL) + servicio `REQUIRES_NEW`. Verifica: tests verdes.
- [x] 3.3 RED: test de punta a punta: el odontólogo cancela un turno (`GestionTurnoService`) → avisos creados tras el commit; una cancelación que se revierte no crea avisos. GREEN: `ListaEsperaHuecoListener` (`@TransactionalEventListener(AFTER_COMMIT)`). Verifica: tests verdes.

## 4. Aceptación (concurrencia)

- [x] 4.1 RED: test unitario de `SolicitudReserva.desdeListaEspera` (`RESERVADO`, `LISTA_ESPERA`, política odontólogo). GREEN: fábrica aditiva. Verifica: test verde.
- [x] 4.2 RED: test de integración de `AvisoHuecoService.aceptar`: turno `RESERVADO` origen `LISTA_ESPERA`, aviso `ACEPTADO`, entrada `CUBIERTA` con `turno_resultante_id`, demás avisos `CERRADO`, `CONFIRMACION_TURNO` encolada al ganador; aviso `CERRADO` o entrada no `ACTIVA` → 409 sin turno. GREEN: servicio con bloqueo `PESSIMISTIC_WRITE` de los avisos del hueco (design D5). Verifica: tests verdes.
- [x] 4.3 RED: dos aceptaciones simultáneas del mismo hueco (dos hilos, `CountDownLatch`, Testcontainers) → exactamente un 200 y un 409, un solo turno en el rango, una sola entrada `CUBIERTA`. GREEN: ajustar orden de bloqueos si hiciera falta. Verifica: test verde repetido 5 veces sin fallos intermitentes.
- [x] 4.4 RED: hueco tomado por una reserva normal del odontólogo → aceptar da 409, token sin consumir, entrada sigue `ACTIVA`, avisos del hueco `CERRADO`; token usado/de otro propósito → 410. GREEN: `AvisoComandos` (traducción en transacción nueva). Verifica: tests verdes.
- [x] 4.5 RED: tests web de `GET /api/v1/avisos/{token}` (200 `disponible` verdadero/falso sin consumir, 410 genérico) y `POST /api/v1/avisos/{token}/aceptar` (200/409/410, sin sesión, con CSRF, el token no aparece en el cuerpo de error). GREEN: `AvisoController` + DTOs. Verifica: tests verdes.

## 5. Vencimiento de avisos

- [x] 5.1 RED: con `Clock` fijo, aviso vencido → `GET` y `POST` responden 410 y el aviso queda `EXPIRADO` (persistido aunque la operación falle). GREEN: rama `AvisoVencidoException` en `AvisoComandos`. Verifica: tests verdes.
- [x] 5.2 RED: `expirarVencidos()` pasa a `EXPIRADO` solo los `ENVIADO` vencidos; el scheduler nunca propaga excepciones. GREEN: `AvisosVencidosScheduler` copiando `ExpiracionPendientesScheduler` (`app.lista-espera.expiracion-intervalo`). Verifica: tests verdes.

## 6. Listado y retiro del odontólogo

- [x] 6.1 RED: tests web de `GET /api/v1/lista-espera` (200 con entradas ordenadas por alta descendente y sin DNI/email; 401 sin sesión). GREEN: `ListaEsperaController` + `EntradaListaEsperaResponse`. Verifica: tests verdes.
- [x] 6.2 RED: `DELETE /api/v1/lista-espera/{id}` (204 y `CANCELADA` + token de confirmación revocado; 404 otro consultorio/inexistente; 422 si `CUBIERTA`; 403 sin CSRF). GREEN: `ListaEsperaService.cancelar`. Verifica: tests verdes.

## 7. Frontend (TS vanilla, strict, sin `any`)

- [x] 7.1 RED (Vitest): narrowing de `lista-espera-api.ts` (inscribir 202/429/400, resumen de aviso, aceptar 200/409/410, listar, retirar 204) desde `unknown`. GREEN: API + mocks en `dev/`. Verifica: `npm test` verde.
- [x] 7.2 RED (Vitest): `inscripcion-page` (envío válido un solo POST y mensaje genérico; rango inválido sin petición; 429; señuelo oculto presente). GREEN: página reutilizando `features/publico/validators.ts`, ruta `/publico/lista-espera` y enlace desde `/publico`. Verifica: tests verdes.
- [x] 7.3 RED (Vitest): `aviso-page` (abrir no envía POST; aceptar 200 → reservado; 409 → "ya no disponible"; `disponible` falso → sin botón; 410 → vencido; doble clic = un POST). GREEN: página y ruta `/avisos/:token`. Verifica: tests verdes.
- [x] 7.4 RED (Vitest): `acciones-page` con `CONFIRMAR_LISTA_ESPERA` (resumen de entrada, confirmar → activa) y regresión de acciones de turno. GREEN: tipos `ResumenAccion` (`turno` nullable, `entrada`) y variante de la vista. Verifica: tests nuevos y existentes de `acciones-token` verdes.
- [x] 7.5 RED (Vitest): `lista-espera-page` (filas, "Retirar" solo en `PENDIENTE_CONFIRMACION`/`ACTIVA`, 204 → `CANCELADA`). GREEN: página, ruta privada `/lista-espera` y enlace en `app-layout.ts`. Verifica: tests verdes. (Puede tomarlo P5/P6 si P1 lo delega.)

## 8. Verificación

- [x] 8.1 Correr la suite completa backend (`./gradlew test`, Docker activo) y frontend (`npm test`, `npm run build` con `tsc` strict); todo verde.
- [x] 8.2 Revisar que ningún log nuevo contenga token, URL de enlace, DNI, email, teléfono ni nombre (grep de los `log.` agregados) y correr `openspec validate c-10-lista-espera --strict` sin errores.

## Workflow follow-up

- `/opsx:archive c-10-lista-espera`, marcar `[x]` C-10 en `CHANGES.md` y abrir PR `feat/c-10-lista-espera` revisado por otra persona.
