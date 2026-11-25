> Responsable: P1 (Thomas). TDD estricto: cada tarea de test se escribe y se ve FALLAR antes de la de producción. IT con `AbstractTurnoIT` (Testcontainers PostgreSQL, `MutableClock`, `CanalEnMemoria`, scheduling apagado); solo datos ficticios (`TurnosFicticios`). Sin migración (D3).

## 1. Safety net

- [x] 1.1 Correr `./gradlew test` en `backend/` y registrar la línea base (todos verdes); si algo falla, frenar y reportar como falla preexistente

## 2. Consulta de candidatos (D1, D2, D7)

- [x] 2.1 RED: IT `TurnoRepositoryRecordatorioIT` — con `H = 24` y reloj fijo: incluye `RESERVADO` en `inicio = ahora + 24 h` y en `ahora + 1 h` (creados hace > 24 h vía JDBC sobre `creado_en`); excluye `inicio = ahora`, `ahora + 24 h + 1 min`, creado con 10 h de anticipación (RN-NO-09), `CONFIRMADO`/`PENDIENTE_CONFIRMACION`/`CANCELADO`, y un turno que ya tiene `RECORDATORIO`
- [x] 2.2 GREEN: `TurnoRepository.idsParaRecordatorio(consultorioId, ahora, hasta, horas)` con la `@Query` nativa de D7

## 3. RecordatorioService (D3, D5)

- [x] 3.1 RED: IT `RecordatorioServiceIT` — `encolarRecordatorios()` crea una notificación `PENDIENTE` `RECORDATORIO` por candidato, con datos `consultorio`/`inicio`/`prestacion` y enlaces `CONFIRMAR_TURNO` + `CANCELAR_TURNO` que vencen en `inicio`; usa `parametrosVigentes().recordatorioHorasAntes()` (cambiar el parámetro cambia la ventana); devuelve la cantidad encolada
- [x] 3.2 GREEN: `application/recordatorio/RecordatorioService` (inyección por constructor, lectura de ids + `TransactionTemplate` `REQUIRES_NEW` por turno, `NotificationService.encolar`)
- [x] 3.3 TRIANGULATE: paciente sin email se omite y el otro candidato se encola; un turno que falla al encolar no impide los demás
- [x] 3.4 TRIANGULATE (idempotencia): dos llamadas seguidas → una sola fila por turno; la segunda devuelve 0

## 4. Concurrencia y envío efectivo (D4)

- [x] 4.1 RED→GREEN: IT concurrente — dos hilos (`CountDownLatch`) ejecutan `encolarRecordatorios()` a la vez → una sola fila `RECORDATORIO` por turno, ningún hilo lanza excepción
- [x] 4.2 IT de envío efectivo — dos corridas del servicio + dos `procesarPendientes()` → `CanalEnMemoria` recibe exactamente un recordatorio por turno; el cuerpo contiene consultorio, prestación, fecha/hora y dos URLs `/acciones/`, y no contiene DNI, teléfono ni datos clínicos del paciente ficticio

## 5. Job periódico (D6)

- [x] 5.1 RED: `RecordatoriosSchedulerIT` — `ejecutar()` encola los candidatos; una excepción del servicio no se propaga (test unitario con mock); la anotación `@Scheduled` usa `${app.recordatorios.intervalo:PT5M}`
- [x] 5.2 GREEN: `infrastructure/scheduling/RecordatoriosScheduler` (patrón de `ExpiracionPendientesScheduler`, logs sin datos personales) y `app.recordatorios.intervalo: PT5M` en `application.yml`
- [x] 5.3 IT de reinicio/retoma — con un `RECORDATORIO` `PENDIENTE` previo y otro candidato sin encolar, una nueva corrida del job + outbox envía ambos una sola vez; un turno cuyo inicio ya pasó no recibe recordatorio

## 6. Extremo a extremo y cierre

- [x] 6.1 IT e2e (si sale barato reutilizando `AbstractWebIntegrationTest`): tomar el token `CANCELAR_TURNO` de la URL del email enviado y hacer `POST /acciones/{token}` → turno `CANCELADO`
- [x] 6.2 REFACTOR y `./gradlew test` completo en verde; revisar que ningún log nuevo incluya tokens, emails ni datos del paciente
