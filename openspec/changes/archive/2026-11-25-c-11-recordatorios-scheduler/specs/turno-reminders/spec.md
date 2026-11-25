## ADDED Requirements

### Requirement: Selección de turnos en la ventana de recordatorio
El sistema SHALL seleccionar como candidatos solo los turnos `RESERVADO` del consultorio actual con `ahora < inicio <= ahora + H`, siendo `H` el parámetro vigente `recordatorio_horas_antes` y `ahora` el `Clock` inyectado. MUST excluir los turnos creados con menos de `H` de anticipación (RN-NO-09) y los que ya tienen un `RECORDATORIO`.

#### Scenario: Turno reservado dentro de la ventana
- **WHEN** un turno `RESERVADO`, creado con más de 24 h de anticipación, empieza dentro de las próximas 24 h (con `recordatorio_horas_antes = 24`)
- **THEN** es candidato a recordatorio

#### Scenario: Límites de la ventana
- **WHEN** hay turnos que empiezan en `ahora + 24 h`, en `ahora + 24 h + 1 min` y en `ahora`
- **THEN** solo el de `ahora + 24 h` es candidato

#### Scenario: Turno creado con poca anticipación
- **WHEN** un turno `RESERVADO` se creó 10 h antes de su inicio
- **THEN** no es candidato y nunca recibe recordatorio

#### Scenario: Estados excluidos
- **WHEN** un turno en la ventana está `CONFIRMADO`, `PENDIENTE_CONFIRMACION`, `CANCELADO`, `EXPIRADO`, `AUSENTE` o `ATENDIDO`
- **THEN** no es candidato

### Requirement: Encolado del recordatorio con enlaces de confirmar y cancelar
Por cada candidato el sistema SHALL encolar, mediante el outbox de notificaciones, una notificación `RECORDATORIO` al email del paciente con los datos mínimos `consultorio`, `inicio` y `prestacion` y dos enlaces programados `CONFIRMAR_TURNO` y `CANCELAR_TURNO` que vencen en el `inicio` del turno. Los tokens MUST emitirse recién al enviar (uno por enlace) y el email MUST NOT contener datos clínicos, DNI ni teléfono. Un paciente sin email MUST omitirse sin interrumpir el resto del lote.

#### Scenario: Recordatorio encolado
- **WHEN** el job procesa un candidato con email
- **THEN** existe una notificación `PENDIENTE` de tipo `RECORDATORIO` para ese turno con enlaces `CONFIRMAR_TURNO` y `CANCELAR_TURNO`

#### Scenario: Contenido sin datos clínicos
- **WHEN** el outbox envía el recordatorio
- **THEN** el cuerpo incluye consultorio, prestación, fecha, hora y dos enlaces `/acciones/{token}`, y no incluye datos clínicos, DNI ni teléfono

#### Scenario: Paciente sin email
- **WHEN** un candidato tiene paciente sin email y otro sí tiene
- **THEN** solo se encola el recordatorio del segundo y el job no falla

#### Scenario: Enlace de cancelar funciona de punta a punta
- **WHEN** el paciente ejecuta el enlace `CANCELAR_TURNO` del recordatorio enviado dentro del límite de cancelación
- **THEN** el turno queda `CANCELADO` y el hueco se libera (comportamiento de `token-actions`)

### Requirement: Un solo recordatorio por turno garantizado por la base
El sistema SHALL garantizar a lo sumo una notificación `RECORDATORIO` por turno mediante la restricción única de la base sobre (`turno_id`, `tipo`) y un insert que ignora el conflicto, nunca solo con un chequeo en Java. Ejecuciones repetidas, concurrentes o posteriores a un reinicio MUST NOT producir un segundo recordatorio ni un segundo envío.

#### Scenario: Dos ejecuciones secuenciales
- **WHEN** el job corre dos veces seguidas con el mismo reloj
- **THEN** existe una sola notificación `RECORDATORIO` por turno candidato

#### Scenario: Dos ejecuciones concurrentes
- **WHEN** dos ejecuciones del job corren a la vez sobre los mismos candidatos
- **THEN** existe una sola notificación `RECORDATORIO` por turno y ninguna ejecución falla

#### Scenario: Un solo envío efectivo
- **WHEN** el job corre dos veces y luego el outbox procesa los pendientes dos veces
- **THEN** el canal recibe exactamente un email de recordatorio por turno

### Requirement: Job periódico configurable y tolerante a reinicios
El sistema SHALL ejecutar la búsqueda y el encolado con un job `@Scheduled` de intervalo configurable (`app.recordatorios.intervalo`, por defecto `PT5M`), apagable junto con el resto de los jobs (`app.scheduling.enabled=false`). La selección MUST ser sin estado: tras un reinicio, la siguiente ejecución encola los candidatos que falten y el outbox envía los `RECORDATORIO` que quedaron `PENDIENTE`. El job MUST NOT propagar excepciones y MUST NOT loguear tokens, emails ni datos del paciente.

#### Scenario: Retoma tras reinicio
- **WHEN** quedó un `RECORDATORIO` `PENDIENTE` sin enviar y otro candidato sin encolar antes de un reinicio
- **THEN** tras el reinicio el job encola el faltante y el outbox envía ambos, cada uno una sola vez

#### Scenario: Turno que ya empezó durante la caída
- **WHEN** el proceso estuvo caído hasta después del inicio de un turno sin recordatorio
- **THEN** ese turno ya no es candidato y no se le envía recordatorio

#### Scenario: Error en una ejecución
- **WHEN** una ejecución del job lanza una excepción inesperada
- **THEN** el job la registra solo con la clase del error y la siguiente ejecución vuelve a intentar
