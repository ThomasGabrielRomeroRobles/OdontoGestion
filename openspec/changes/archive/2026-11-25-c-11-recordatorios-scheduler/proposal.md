## Why

El ausentismo es el problema central de OdontoGestión. Sin un recordatorio previo con enlaces para confirmar o cancelar, el paciente olvida el turno y el hueco no se libera a tiempo para la lista de espera (C-10). C-03 dejó el outbox, la plantilla `RECORDATORIO` y los tokens; C-09 dejó las acciones `CONFIRMAR_TURNO`/`CANCELAR_TURNO`. Falta el job que, `recordatorio_horas_antes` horas antes de cada turno, encole el recordatorio una sola vez (RN-NO-02, RN-NO-03, Flujo 5, DD-13). Es el último change obligatorio antes de C-12.

## What Changes

- Nuevo job `@Scheduled` `RecordatoriosScheduler` (intervalo configurable `app.recordatorios.intervalo`, por defecto `PT5M`) que delega en un nuevo `RecordatorioService`.
- `RecordatorioService.encolarRecordatorios()` busca turnos `RESERVADO` del consultorio actual con `inicio` en la ventana `(ahora, ahora + recordatorio_horas_antes]`, creados con al menos esa anticipación (RN-NO-09) y sin notificación `RECORDATORIO` previa, y encola por cada uno una notificación `RECORDATORIO` con enlaces `CONFIRMAR_TURNO` y `CANCELAR_TURNO` (vencen al inicio del turno) usando `NotificationService.encolar`.
- Idempotencia y concurrencia garantizadas por la base con el índice único parcial **ya existente** `uq_notificacion_turno_tipo` (V6) + `INSERT ... ON CONFLICT DO NOTHING`: dos corridas (secuenciales o concurrentes) o un reinicio nunca producen dos recordatorios. **Sin migración nueva.**
- Los tokens se emiten recién al enviar (outbox de C-03, `FOR UPDATE SKIP LOCKED`), así que cada recordatorio enviado lleva exactamente un par de tokens; los pendientes se retoman tras un reinicio.
- Nueva consulta en `TurnoRepository` para los candidatos de la ventana.

## Capabilities

### New Capabilities
- `turno-reminders`: selección de turnos en la ventana de recordatorio, encolado idempotente del `RECORDATORIO` con enlaces de confirmar/cancelar, job periódico configurable y retoma tras reinicio.

### Modified Capabilities
(ninguna: se reutilizan sin cambios de requisitos `notification-delivery`, `patient-access-tokens` y `token-actions`)

## Impact

- Backend: `application/recordatorio/RecordatorioService` (nuevo), `infrastructure/scheduling/RecordatoriosScheduler` (nuevo), `domain/turno/TurnoRepository` (consulta nueva), `application.yml` (`app.recordatorios.intervalo`).
- Sin cambios de esquema, API REST ni frontend.
- Governance **ALTO**: las decisiones de `design.md` requieren aprobación humana antes de implementar.

### Fuera de alcance (etapa posterior)
- Descartar un `RECORDATORIO` pendiente si el turno se cancela/mueve antes del envío (hoy el enlace llega y la acción responde genéricamente).
- Recordatorio nuevo tras mover un turno que ya lo recibió.
- Eliminar la propiedad duplicada `app.reminder-hours-before` de `AppProperties` (la fuente de verdad es el parámetro de base).
- Recordatorios por WhatsApp u otros canales; varios recordatorios por turno.
