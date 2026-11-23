# Spec Delta

## MODIFIED Requirements

### Requirement: Estados y transiciones del turno
Un turno SHALL tener uno de los estados `PENDIENTE_CONFIRMACION`, `RESERVADO`, `CONFIRMADO`, `CANCELADO`, `AUSENTE`, `ATENDIDO` o `EXPIRADO` y solo MUST cambiar según: `PENDIENTE_CONFIRMACION → RESERVADO | EXPIRADO | CANCELADO`; `RESERVADO → CONFIRMADO | CANCELADO | AUSENTE | ATENDIDO`; `CONFIRMADO → CANCELADO | AUSENTE | ATENDIDO`. `CANCELADO`, `EXPIRADO` y `ATENDIDO` son finales; `AUSENTE` solo sale por la reversión de la ausencia.

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

#### Scenario: Ausente solo sale por reversión
- **GIVEN** un turno `AUSENTE`
- **WHEN** el odontólogo intenta marcarlo como atendido, cancelarlo o moverlo
- **THEN** la respuesta es `422` y el turno sigue `AUSENTE`

#### Scenario: Reversión al estado previo
- **GIVEN** un turno que estaba `CONFIRMADO` y fue marcado `AUSENTE` hace una hora
- **WHEN** el odontólogo revierte la ausencia
- **THEN** el turno queda `CONFIRMADO`

### Requirement: Eventos de cambios de agenda
Por cada cambio de agenda el sistema SHALL emitir un evento de turno creado, turno movido (con intervalo anterior y nuevo), turno cancelado o turno marcado ausente, y un evento de hueco liberado con el intervalo libre al cancelar o mover. Marcar ausente MUST NOT emitir hueco liberado, porque el turno sigue ocupando su intervalo (RN-AS-04). Si el cambio se revierte, ningún efecto de esos eventos MUST persistir.

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

#### Scenario: Ausente sin hueco liberado
- **WHEN** el odontólogo marca ausente un turno de 10:00 a 10:30
- **THEN** se emite un evento de turno marcado ausente y ningún evento de hueco liberado

### Requirement: Notificación al paciente de cambios en su turno
Al crear un turno que nace `RESERVADO` o al moverlo, el sistema SHALL encolar una notificación `CONFIRMACION_TURNO`; al cancelarlo, una `CANCELACION`; al marcarlo ausente, una `AUSENTE_REPROGRAMAR` con el enlace a la página pública. Su contenido MUST limitarse a fecha, hora, consultorio, nombre de la prestación y, si corresponde, ese enlace, sin DNI, tokens ni datos de la historia clínica; un fallo de envío no revierte el cambio.

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

#### Scenario: Invitación a reprogramar encolada
- **WHEN** el odontólogo marca ausente un turno de un paciente con email
- **THEN** queda encolada una notificación `AUSENTE_REPROGRAMAR` para ese turno

## ADDED Requirements

### Requirement: Reversión de la ausencia
La reversión de la ausencia SHALL devolver un turno `AUSENTE` exactamente al estado que tenía antes de la marca (`RESERVADO` o `CONFIRMADO`) y solo MUST estar disponible dentro de las 24 horas desde la marca (RN-AS-06). Ninguna otra operación MUST sacar un turno de `AUSENTE`.

#### Scenario: Vuelve a reservado
- **GIVEN** un turno que estaba `RESERVADO` y fue marcado `AUSENTE` hace una hora
- **WHEN** el odontólogo revierte la ausencia
- **THEN** el turno queda `RESERVADO`

#### Scenario: Pasadas las 24 horas
- **GIVEN** un turno marcado `AUSENTE` hace 24 horas
- **WHEN** el odontólogo intenta revertir la ausencia
- **THEN** la respuesta es `422` y el turno sigue `AUSENTE`

### Requirement: Datos de ausencia en el turno
Toda respuesta que devuelve un turno (agenda, turnos de hoy y acciones sobre el turno) SHALL incluir `ausenteMarcadoEn`, con el instante de la marca de ausente o `null` si el turno no está `AUSENTE`, y la cantidad de ausencias del paciente en `paciente.cantidadAusencias`. MUST NOT incluir el DNI, el email ni el teléfono del paciente.

#### Scenario: Turno ausente
- **GIVEN** un turno marcado `AUSENTE` a las 10:20 de un paciente con 2 ausencias
- **WHEN** el odontólogo consulta la agenda de ese día
- **THEN** el turno trae `ausenteMarcadoEn` 10:20 y `paciente.cantidadAusencias` 2

#### Scenario: Turno no ausente
- **WHEN** el odontólogo consulta un turno `RESERVADO`
- **THEN** el turno trae `ausenteMarcadoEn` `null` y no incluye DNI, email ni teléfono del paciente
