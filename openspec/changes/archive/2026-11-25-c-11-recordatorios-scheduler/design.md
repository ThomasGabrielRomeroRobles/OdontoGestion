## Context

C-11 cierra el Flujo 5 (recordatorio + confirmación). Casi todo existe; este change solo agrega la **selección periódica** y el **encolado**. Código real verificado en `main` (8fefc97):

| Ya existe (se reutiliza sin cambios) | Dónde |
|---|---|
| Tipo `RECORDATORIO` en `TipoNotificacion` y en el `CHECK` de V6 | `domain/notificacion/TipoNotificacion`, `V6__notificacion.sql` |
| Plantilla `PlantillaNotificacion.RECORDATORIO` (variables `consultorio`, `inicio`, `prestacion`; enlaces `CONFIRMAR_TURNO` + `CANCELAR_TURNO`; sin datos clínicos) | `application/notificacion/PlantillaNotificacion` |
| Índice único parcial `uq_notificacion_turno_tipo ON notificacion (turno_id, tipo) WHERE turno_id IS NOT NULL AND tipo <> 'CONFIRMACION_TURNO'` | V6 |
| `NotificationService.encolar(tipo, destinatario, ReferenciaToken, datos, List<EnlaceProgramado>)` → `NotificacionRepository.insertarSiNoExiste` (`INSERT ... ON CONFLICT DO NOTHING`), devuelve `false` si ya existía | `application/notificacion/NotificationService` |
| Outbox: `procesarPendientes()` cada `app.notifications.poll-interval` (PT5M), una TX por fila con `FOR UPDATE SKIP LOCKED`, reintentos (`PoliticaReintento`), descarte si un enlace venció (`ENLACE_VENCIDO`) | `NotificationService`, `infrastructure/notificacion/ProcesadorNotificacionesJob` |
| Emisión de tokens al enviar y revocación si el envío falla | puerto `EmisorEnlaces` + adaptador `infrastructure/notificacion/EmisorEnlacesConTokens` → `TokenAccesoService` |
| Acciones `CONFIRMAR_TURNO` (`turno.confirmar()`) y `CANCELAR_TURNO` (`cancelarPorPaciente` + límite de cancelación → `HuecoLiberado` → lista de espera) | `application/acciones/AccionTokenService` (C-09) |
| Patrón de jobs: `@Component` en `infrastructure/scheduling`, `@Scheduled(fixedDelayString=...)`, captura `RuntimeException` y loguea solo la clase; `@EnableScheduling` condicionado por `app.scheduling.enabled` en `SchedulingConfig`; los IT corren con scheduling apagado e invocan `ejecutar()` | `ExpiracionPendientesScheduler`, `AvisosVencidosScheduler`, `SchedulingConfig` |
| `Clock` inyectable; `MutableClock`, `CanalEnMemoria`, `AbstractTurnoIT`, `TurnosFicticios` en tests | `support/` |
| Parámetro `recordatorioHorasAntes` | `ConsultorioService.parametrosVigentes()` |
| Armado de datos de la notificación de un turno (email del paciente, nombre de consultorio y prestación, `inicio`) | `NotificacionesTurnoListener.encolar(...)` (privado) |

| Falta (lo agrega C-11) |
|---|
| Consulta de candidatos en `TurnoRepository` |
| `application/recordatorio/RecordatorioService.encolarRecordatorios()` |
| `infrastructure/scheduling/RecordatoriosScheduler` + propiedad `app.recordatorios.intervalo` |

## Goals / Non-Goals

**Goals:** encolar exactamente un `RECORDATORIO` por turno elegible, con enlaces confirmar/cancelar, idempotente y seguro ante corridas concurrentes y reinicios, garantizado por la base.

**Non-Goals:** cambios de esquema, API o frontend; descartar recordatorios de turnos cancelados/movidos antes del envío; re-recordar turnos movidos; otros canales (ver "etapa posterior" en `proposal.md`).

## Decisions (requieren aprobación — Governance ALTO)

### D1. Regla de ventana
Candidato ⇔ `estado = 'RESERVADO'` ∧ `ahora < inicio <= ahora + H` ∧ `creado_en <= inicio - H` ∧ no existe `notificacion` con (`turno_id`, `tipo='RECORDATORIO'`), con `H = parametrosVigentes().recordatorioHorasAntes()` y `ahora = clock.instant()`.
- Ventana semiabierta "hacia adelante": el borde superior se incluye, `inicio = ahora` no (el turno ya empieza).
- La condición `creado_en <= inicio - H` implementa **RN-NO-09** (turno creado con menos de H h de anticipación no recibe recordatorio; solo la confirmación). Se usa `creado_en` de V9 (no se mira la fecha de un `mover`: etapa posterior).
- Es **sin estado**: no se guarda "última ejecución". Si el proceso estuvo caído, la siguiente corrida toma todo lo que siga en ventana; un turno que ya empezó queda fuera (no tiene sentido recordarlo).
- **Fuente de verdad de H**: el parámetro de base (`parametros_consultorio`). `AppProperties.reminderHoursBefore` (`REMINDER_HOURS_BEFORE`, C-01) queda duplicado y **no se usa**; eliminarlo es etapa posterior.
- El `NOT EXISTS` es solo optimización (evita reintentos de insert inútiles); la garantía es D3.

### D2. Estados: solo `RESERVADO`
Un turno `CONFIRMADO` ya fue confirmado (por el odontólogo o por el paciente): mandarle un enlace de confirmar es ruido y la acción no aplicaría. `PENDIENTE_CONFIRMACION` aún no es turno. Los estados finales no aplican. Alternativa descartada: incluir `CONFIRMADO` con solo el enlace de cancelar — requeriría otra plantilla/tipo y rompe el índice (turno_id, tipo); se deja para etapa posterior.

### D3. Idempotencia por la base, sin migración
Se reutiliza `uq_notificacion_turno_tipo` (V6) vía `NotificationService.encolar` → `insertarSiNoExiste` (`ON CONFLICT DO NOTHING`). Dos inserts del mismo (turno, `RECORDATORIO`) nunca coexisten; el segundo devuelve 0 filas sin excepción ni rollback. **No hace falta V12.** Los tokens no se crean al encolar sino al enviar (C-03 D8): el recordatorio encolado una vez produce un solo par de tokens por intento de envío exitoso, y los de un intento fallido se revocan.

### D4. Concurrencia de dos corridas
Dos ejecuciones simultáneas (p. ej. dos instancias o una corrida lenta solapada) seleccionan los mismos candidatos y ambas llaman a `encolar`. En PostgreSQL el segundo `INSERT` espera el commit del primero sobre el índice único y luego no inserta. El envío queda protegido por `FOR UPDATE SKIP LOCKED` + transición a `ENVIADA` del outbox. Resultado: una fila y un email. Se verifica con un IT concurrente (dos hilos + `CountDownLatch`) contra Testcontainers.

### D5. Transacciones y fallos por turno
`RecordatorioService` (único con `@Transactional`/`TransactionTemplate`, regla dura #3):
1. Lectura (`readOnly`) de los ids candidatos.
2. Por cada id, una transacción propia (`TransactionTemplate` `REQUIRES_NEW`, mismo patrón que `NotificationService.procesarPendientes`) que recarga el turno, arma los datos y llama a `encolar`. Un error en un turno (p. ej. email inválido → `ValidacionDominioException`) se loguea con el id del turno y la clase del error y no afecta al resto.
- Paciente sin email → se omite con `log.warn` (id del turno), igual que `NotificacionesTurnoListener`.
- Datos: `consultorio` = nombre, `inicio` = `turno.getInicio().toString()` (UTC; la plantilla formatea en `America/Argentina/Buenos_Aires`), `prestacion` = nombre. Enlaces: `CONFIRMAR_TURNO` y `CANCELAR_TURNO` con `venceEn = turno.getInicio()`. El límite de cancelación lo sigue aplicando `AccionTokenService`.
- Se extrae el armado de datos de `NotificacionesTurnoListener` solo si sale gratis; si no, se duplica el `Map.of` de 3 claves (no es gold plating).

### D6. Intervalo y configuración
`RecordatoriosScheduler.ejecutar()` con `@Scheduled(fixedDelayString = "${app.recordatorios.intervalo:PT5M}")` (DD-13: mismo proceso, sin colas). `fixedDelay` evita que una corrida se solape consigo misma en la misma instancia (D4 cubre el resto). Se apaga con `app.scheduling.enabled=false`. Logs: solo cantidad encolada y clase de error; nunca tokens, emails ni datos del paciente (regla dura #10).
Latencia máxima entre entrada a la ventana y envío ≈ intervalo del job + `poll-interval` del outbox (≤10 min por defecto): aceptable para un recordatorio de 24 h.

### D7. Consulta
`@Query` nativa en `TurnoRepository` que devuelve `List<Long>`:
```sql
SELECT t.id FROM turno t
WHERE t.consultorio_id = :consultorioId AND t.estado = 'RESERVADO'
  AND t.inicio > :ahora AND t.inicio <= :hasta AND t.creado_en <= t.inicio - make_interval(hours => :horas)
  AND NOT EXISTS (SELECT 1 FROM notificacion n WHERE n.turno_id = t.id AND n.tipo = 'RECORDATORIO')
ORDER BY t.inicio
```
Usa `idx_notificacion_turno`/`uq_notificacion_turno_tipo`; sin índice nuevo (volumen de un consultorio).

## Risks / Trade-offs

- [Turno cancelado/movido entre encolado y envío recibe igual el recordatorio] → los enlaces fallan de forma genérica (RN-TK-05) y el outbox descarta si el enlace venció. Mitigación completa (descartar pendiente en `alCancelar`) = etapa posterior.
- [Turno movido después de recibir el recordatorio no recibe otro] → el índice (turno_id, tipo) lo impide; aceptado para el MVP.
- [`creado_en` lo pone la base con `now()` y no el `Clock`] → en los IT se fija `creado_en` por JDBC al preparar el turno.
- [Duplicación de `REMINDER_HOURS_BEFORE`] → documentado; se ignora la propiedad.

## Migration Plan

Sin migración Flyway (próximo número libre sigue siendo V12). Despliegue = nuevo bean; rollback = quitar el bean. Las filas `RECORDATORIO` ya encoladas son inocuas.

## Open Questions

Ninguna bloqueante; D1–D6 esperan la aprobación de la persona responsable (P1).
