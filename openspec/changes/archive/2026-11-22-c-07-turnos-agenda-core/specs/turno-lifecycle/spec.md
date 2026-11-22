# Spec Delta

## Purpose

Define el turno de la agenda del odontólogo: sus estados y transiciones, la garantía de no superposición en la base de datos, la creación, el movimiento, la cancelación y la atención, la expiración de solicitudes pendientes, la concurrencia sobre un mismo turno y los avisos que esos cambios generan.

## ADDED Requirements

### Requirement: Estados y transiciones del turno
Un turno SHALL tener uno de los estados `PENDIENTE_CONFIRMACION`, `RESERVADO`, `CONFIRMADO`, `CANCELADO`, `AUSENTE`, `ATENDIDO` o `EXPIRADO` y solo MUST cambiar según: `PENDIENTE_CONFIRMACION → RESERVADO | EXPIRADO | CANCELADO`; `RESERVADO → CONFIRMADO | CANCELADO | AUSENTE | ATENDIDO`; `CONFIRMADO → CANCELADO | AUSENTE | ATENDIDO`. `CANCELADO`, `EXPIRADO`, `AUSENTE` y `ATENDIDO` son finales.

#### Scenario: Transición válida
- **GIVEN** un turno `RESERVADO`
- **WHEN** el odontólogo lo marca como atendido
- **THEN** el turno queda `ATENDIDO`

#### Scenario: Transición desde un estado final
- **GIVEN** un turno `CANCELADO`
- **WHEN** el odontólogo intenta marcarlo como atendido
- **THEN** la respuesta es `422` en formato `ProblemDetail` y el turno sigue `CANCELADO`

#### Scenario: Transición no prevista
- **GIVEN** un turno `PENDIENTE_CONFIRMACION`
- **WHEN** el odontólogo intenta marcarlo como atendido
- **THEN** la respuesta es `422` y el estado no cambia

### Requirement: No superposición garantizada por la base de datos
La base de datos SHALL rechazar que dos turnos del mismo consultorio que ocupan agenda se superpongan en el intervalo semiabierto `[inicio, fin)`, aun bajo solicitudes simultáneas. Ocupan agenda todos los estados salvo `CANCELADO` y `EXPIRADO` (resolución propuesta de PA-06). Todo turno MUST cumplir `inicio < fin`.

#### Scenario: Turnos contiguos permitidos
- **GIVEN** un turno `RESERVADO` de 09:00 a 09:30
- **WHEN** se crea otro turno de 09:30 a 10:00 el mismo día
- **THEN** ambos turnos quedan registrados

#### Scenario: Superposición rechazada
- **GIVEN** un turno `CONFIRMADO` de 09:00 a 09:30
- **WHEN** se intenta crear un turno de 09:15 a 09:45
- **THEN** la creación es rechazada con `409` y no queda ningún turno nuevo registrado

#### Scenario: Turnos cancelados o expirados liberan el hueco
- **GIVEN** un turno `CANCELADO` y otro `EXPIRADO`, ambos de 09:00 a 09:30
- **WHEN** se crea un turno de 09:00 a 09:30
- **THEN** el turno queda registrado

#### Scenario: Turnos atendidos o ausentes siguen ocupando el rango
- **GIVEN** un turno `ATENDIDO` de 09:00 a 09:30
- **WHEN** se intenta registrar directamente en la base otro turno activo que se superpone
- **THEN** la base lo rechaza por violación de la restricción de exclusión

#### Scenario: Dos reservas simultáneas del mismo hueco
- **WHEN** dos solicitudes concurrentes intentan crear un turno en el mismo intervalo libre
- **THEN** exactamente una tiene éxito y la otra recibe `409` sin dejar estado parcial

#### Scenario: Intervalo inválido rechazado por la base
- **WHEN** se inserta directamente un turno con `fin` igual o anterior a `inicio`
- **THEN** la base rechaza la inserción

### Requirement: Creación de turno por el odontólogo
El odontólogo autenticado SHALL poder crear un turno indicando paciente, prestación activa e inicio. El turno MUST nacer `RESERVADO` con origen `ODONTOLOGO`, ocupar `[inicio, inicio + duración de la prestación)` y responder `201` con el turno creado.

#### Scenario: Creación exitosa
- **GIVEN** un horario de trabajo de 09:00 a 13:00 y una prestación activa de 30 minutos
- **WHEN** el odontólogo crea un turno para un paciente existente con inicio a las 10:00
- **THEN** la respuesta es `201` con el turno `RESERVADO` de 10:00 a 10:30 y origen `ODONTOLOGO`

#### Scenario: Sin sesión
- **WHEN** se intenta crear un turno sin sesión
- **THEN** la respuesta es `401` y no se crea ningún turno

#### Scenario: Paciente o prestación inexistente
- **WHEN** se crea un turno con un paciente o una prestación que no existe en el consultorio
- **THEN** la respuesta es `404`

#### Scenario: Prestación inactiva
- **WHEN** se crea un turno con una prestación desactivada
- **THEN** la respuesta es `422`

#### Scenario: Paciente dado de baja
- **WHEN** se crea un turno para un paciente dado de baja
- **THEN** la respuesta es `422` y no se crea ningún turno

### Requirement: Validación del horario de un turno
Un turno nuevo o movido SHALL quedar íntegramente dentro de un horario de trabajo, fuera de todo bloqueo de agenda, con inicio alineado a la granularidad vigente y no en el pasado; si no cumple MUST responderse `422` sin cambios. El odontólogo no está sujeto a la anticipación mínima de reserva.

#### Scenario: Fuera del horario de trabajo
- **GIVEN** un horario de 09:00 a 13:00
- **WHEN** se crea un turno de 30 minutos con inicio a las 12:45
- **THEN** la respuesta es `422`

#### Scenario: Dentro de un bloqueo
- **GIVEN** un bloqueo de 10:00 a 11:00
- **WHEN** se crea un turno con inicio a las 10:30
- **THEN** la respuesta es `422`

#### Scenario: Inicio no alineado a la granularidad
- **GIVEN** una granularidad de 15 minutos
- **WHEN** se crea un turno con inicio a las 10:10
- **THEN** la respuesta es `422`

#### Scenario: Inicio en el pasado
- **WHEN** el odontólogo crea un turno con inicio anterior al momento actual
- **THEN** la respuesta es `422`

#### Scenario: Odontólogo sin anticipación mínima
- **GIVEN** una anticipación mínima de reserva de 120 minutos
- **WHEN** el odontólogo crea un turno que empieza dentro de 30 minutos en un hueco libre
- **THEN** la respuesta es `201`

### Requirement: Conflicto de agenda con alternativas
Cuando un turno no puede crearse o moverse porque el intervalo ya está ocupado, la respuesta SHALL ser `409` en formato `ProblemDetail` con un mensaje claro y una propiedad `alternativas` con hasta 3 huecos libres para la misma prestación, a partir del inicio pedido.

#### Scenario: Conflicto con alternativas
- **GIVEN** un turno `RESERVADO` de 10:00 a 10:30 y huecos libres a las 10:30, 10:45 y 11:00
- **WHEN** el odontólogo intenta crear un turno de 30 minutos a las 10:00
- **THEN** la respuesta es `409` con `alternativas` que contiene los huecos de 10:30, 10:45 y 11:00

#### Scenario: Conflicto sin alternativas
- **GIVEN** que no quedan huecos libres para la prestación en el período de búsqueda
- **WHEN** el odontólogo intenta crear un turno en un intervalo ocupado
- **THEN** la respuesta es `409` con `alternativas` vacía

### Requirement: Mover un turno
El odontólogo SHALL poder mover un turno `RESERVADO` o `CONFIRMADO` a un nuevo inicio que cumpla la validación de horario y la no superposición, conservando paciente, prestación, duración y estado. El intervalo original MUST quedar libre en el mismo cambio.

#### Scenario: Movimiento exitoso
- **GIVEN** un turno `CONFIRMADO` de 10:00 a 10:30
- **WHEN** el odontólogo lo mueve a las 11:00
- **THEN** la respuesta es `200` con el turno de 11:00 a 11:30 y estado `CONFIRMADO`

#### Scenario: Movimiento parcialmente solapado consigo mismo
- **GIVEN** un turno `RESERVADO` de 10:00 a 10:30 sin otros turnos cerca
- **WHEN** el odontólogo lo mueve a las 10:15
- **THEN** la respuesta es `200` con el turno de 10:15 a 10:45

#### Scenario: Movimiento a un hueco ocupado
- **GIVEN** otro turno ocupa de 11:00 a 11:30
- **WHEN** el odontólogo mueve un turno a las 11:00
- **THEN** la respuesta es `409` con alternativas y el turno conserva su horario original

#### Scenario: Mover un turno en estado no movible
- **WHEN** el odontólogo intenta mover un turno `PENDIENTE_CONFIRMACION`, `CANCELADO`, `EXPIRADO`, `AUSENTE` o `ATENDIDO`
- **THEN** la respuesta es `422`

### Requirement: Cancelar un turno
El odontólogo SHALL poder cancelar un turno `PENDIENTE_CONFIRMACION`, `RESERVADO` o `CONFIRMADO` con un motivo opcional (máximo 200 caracteres). El turno MUST quedar `CANCELADO` con `cancelado_por = ODONTOLOGO` y el motivo registrado, y su intervalo libre.

#### Scenario: Cancelación con motivo
- **GIVEN** un turno `RESERVADO`
- **WHEN** el odontólogo lo cancela con motivo "Imprevisto del consultorio"
- **THEN** la respuesta es `200` con el turno `CANCELADO`, `canceladoPor` `ODONTOLOGO` y el motivo registrado

#### Scenario: Cancelación sin motivo
- **WHEN** el odontólogo cancela un turno `CONFIRMADO` sin indicar motivo
- **THEN** el turno queda `CANCELADO` con motivo vacío

#### Scenario: Hueco reutilizable tras cancelar
- **GIVEN** un turno cancelado de 10:00 a 10:30
- **WHEN** se crea otro turno de 10:00 a 10:30
- **THEN** la respuesta es `201`

#### Scenario: Cancelar un turno ya finalizado
- **WHEN** el odontólogo intenta cancelar un turno `ATENDIDO`
- **THEN** la respuesta es `422`

### Requirement: Marcar un turno como atendido
Solo el odontólogo autenticado SHALL poder marcar como `ATENDIDO` un turno `RESERVADO` o `CONFIRMADO`; la respuesta MUST ser `200` con el turno actualizado.

#### Scenario: Turno atendido
- **GIVEN** un turno `CONFIRMADO` de hoy
- **WHEN** el odontólogo lo marca como atendido
- **THEN** la respuesta es `200` con el turno `ATENDIDO`

#### Scenario: Atender sin sesión
- **WHEN** se intenta marcar un turno como atendido sin sesión
- **THEN** la respuesta es `401` y el estado no cambia

### Requirement: Turno inexistente o de otro consultorio
Toda operación sobre un turno que no existe o que no pertenece al consultorio actual SHALL responder `404` sin revelar datos del turno.

#### Scenario: Turno inexistente
- **WHEN** el odontólogo intenta cancelar un turno con un id inexistente
- **THEN** la respuesta es `404` en formato `ProblemDetail`

### Requirement: Modificación concurrente de un mismo turno
Dos cambios simultáneos sobre el mismo turno SHALL resolverse de modo que solo uno se aplique; el otro MUST recibir `409` sin modificar el turno.

#### Scenario: Cancelar y mover a la vez
- **WHEN** dos solicitudes concurrentes cancelan y mueven el mismo turno
- **THEN** exactamente una se aplica y la otra recibe `409`

### Requirement: Retención y expiración de solicitudes pendientes
Un turno `PENDIENTE_CONFIRMACION` SHALL retener su hueco hasta `pendiente_hasta`. Antes de cada reserva o movimiento, y periódicamente, las pendientes vencidas MUST pasar a `EXPIRADO`, liberando su hueco.

#### Scenario: Pendiente vigente retiene el hueco
- **GIVEN** un turno `PENDIENTE_CONFIRMACION` de 10:00 a 10:30 con `pendiente_hasta` dentro de 10 minutos
- **WHEN** el odontólogo intenta crear un turno de 10:00 a 10:30
- **THEN** la respuesta es `409`

#### Scenario: Pendiente vencida se expira antes de reservar
- **GIVEN** un turno `PENDIENTE_CONFIRMACION` de 10:00 a 10:30 cuyo `pendiente_hasta` ya pasó
- **WHEN** el odontólogo crea un turno de 10:00 a 10:30
- **THEN** la respuesta es `201` y el turno pendiente queda `EXPIRADO`

#### Scenario: Expiración periódica
- **GIVEN** dos turnos pendientes, uno vencido y otro vigente
- **WHEN** corre la expiración periódica
- **THEN** solo el vencido queda `EXPIRADO` y el vigente sigue `PENDIENTE_CONFIRMACION`

#### Scenario: Expiración idempotente
- **WHEN** la expiración periódica corre dos veces seguidas
- **THEN** la segunda ejecución no modifica ningún turno

### Requirement: Eventos de cambios de agenda
Por cada cambio de agenda el sistema SHALL emitir un evento de turno creado, turno movido (con intervalo anterior y nuevo) o turno cancelado, y un evento de hueco liberado con el intervalo libre al cancelar o mover. Si el cambio se revierte, ningún efecto de esos eventos MUST persistir.

#### Scenario: Mover libera hueco
- **GIVEN** un turno `RESERVADO` de 10:00 a 10:30
- **WHEN** el odontólogo lo mueve a las 11:00
- **THEN** se emite un evento de turno movido y un evento de hueco liberado para 10:00 a 10:30

#### Scenario: Cancelar libera hueco
- **WHEN** el odontólogo cancela un turno de 10:00 a 10:30
- **THEN** se emite un evento de turno cancelado y un evento de hueco liberado para 10:00 a 10:30

#### Scenario: Sin evento ante conflicto
- **WHEN** la creación de un turno falla con `409`
- **THEN** no se emite ningún evento

### Requirement: Notificación al paciente de cambios en su turno
Al crear un turno que nace `RESERVADO` o al moverlo, el sistema SHALL encolar una notificación `CONFIRMACION_TURNO`; al cancelarlo, una `CANCELACION`. Su contenido MUST limitarse a fecha, hora, consultorio y nombre de la prestación, sin DNI, tokens ni datos de la historia clínica; un fallo de envío no revierte el cambio.

#### Scenario: Confirmación encolada al crear
- **WHEN** el odontólogo crea un turno para un paciente con email
- **THEN** queda encolada una notificación `CONFIRMACION_TURNO` para ese turno dirigida al email del paciente

#### Scenario: Cancelación encolada
- **WHEN** el odontólogo cancela un turno
- **THEN** queda encolada una notificación `CANCELACION` para ese turno

#### Scenario: Pendiente pública sin confirmación de turno
- **WHEN** se crea un turno `PENDIENTE_CONFIRMACION`
- **THEN** no se encola `CONFIRMACION_TURNO`

#### Scenario: Fallo del canal no revierte
- **GIVEN** un canal de envío que falla
- **WHEN** el odontólogo cancela un turno
- **THEN** el turno queda `CANCELADO` y la notificación queda registrada para reintento

#### Scenario: Mover re-notifica
- **GIVEN** un turno `RESERVADO` con su `CONFIRMACION_TURNO` ya encolada
- **WHEN** el odontólogo lo mueve
- **THEN** queda encolada una segunda `CONFIRMACION_TURNO` para ese turno con el horario nuevo
