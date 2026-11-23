# Spec Delta

## Purpose

Registrar las ausencias de los pacientes a sus turnos para que el odontólogo controle el ausentismo: marcar ausente pasada la tolerancia, corregir una marca equivocada, ver el historial de cada paciente e invitarlo a reprogramar.

## ADDED Requirements

### Requirement: Marcar un turno como ausente
El odontólogo autenticado SHALL poder marcar como `AUSENTE` un turno `RESERVADO` o `CONFIRMADO` con `POST /api/v1/turnos/{id}/ausente`. La respuesta MUST ser `200` con el turno actualizado, que informa el instante de la marca en `ausenteMarcadoEn`. El turno sigue ocupando su intervalo (RN-AS-04).

#### Scenario: Ausente pasada la tolerancia
- **GIVEN** un turno `RESERVADO` que empezó a las 10:00 y una tolerancia de 15 minutos
- **WHEN** el odontólogo lo marca ausente a las 10:20
- **THEN** la respuesta es `200`, el turno queda `AUSENTE` y `ausenteMarcadoEn` es 10:20

#### Scenario: Turno confirmado
- **GIVEN** un turno `CONFIRMADO` pasada su tolerancia
- **WHEN** el odontólogo lo marca ausente
- **THEN** el turno queda `AUSENTE`

#### Scenario: El intervalo sigue ocupado
- **GIVEN** un turno marcado `AUSENTE` de 10:00 a 10:30
- **WHEN** se intenta crear otro turno de 10:00 a 10:30
- **THEN** la creación es rechazada por superposición

### Requirement: Tolerancia antes de marcar ausente
Marcar ausente SHALL estar permitido solo cuando el instante actual es igual o posterior al inicio del turno más la tolerancia de ausente del consultorio (RN-AS-01). Antes, la respuesta MUST ser `422` en formato `ProblemDetail` y el turno, el contador del paciente y las notificaciones MUST NOT cambiar.

#### Scenario: Antes de la tolerancia
- **GIVEN** un turno `RESERVADO` que empezó a las 10:00 y una tolerancia de 15 minutos
- **WHEN** el odontólogo lo marca ausente a las 10:14
- **THEN** la respuesta es `422` y el turno sigue `RESERVADO`

#### Scenario: Justo al cumplirse la tolerancia
- **GIVEN** un turno que empezó a las 10:00 y una tolerancia de 15 minutos
- **WHEN** el odontólogo lo marca ausente a las 10:15
- **THEN** el turno queda `AUSENTE`

#### Scenario: Tolerancia configurada por el consultorio
- **GIVEN** la tolerancia del consultorio cambiada a 30 minutos y un turno que empezó a las 10:00
- **WHEN** el odontólogo lo marca ausente a las 10:20
- **THEN** la respuesta es `422`

### Requirement: Estados que admiten la marca de ausente
Solo un turno `RESERVADO` o `CONFIRMADO` SHALL poder marcarse ausente; desde cualquier otro estado la respuesta MUST ser `422` en formato `ProblemDetail` sin cambios. Un turno inexistente o de otro consultorio MUST responder `404`.

#### Scenario: Turno ya ausente
- **GIVEN** un turno `AUSENTE`
- **WHEN** el odontólogo lo marca ausente otra vez
- **THEN** la respuesta es `422` y el contador del paciente no cambia

#### Scenario: Turno pendiente, cancelado o atendido
- **WHEN** el odontólogo intenta marcar ausente un turno `PENDIENTE_CONFIRMACION`, `CANCELADO`, `EXPIRADO` o `ATENDIDO`
- **THEN** la respuesta es `422` y el estado no cambia

#### Scenario: Turno de otro consultorio
- **WHEN** el odontólogo intenta marcar ausente un turno inexistente o de otro consultorio
- **THEN** la respuesta es `404` sin datos del turno

### Requirement: Contador de ausencias del paciente
Cada marca de ausente SHALL sumar uno al contador de ausencias del paciente del turno (RN-AS-02), en la misma operación que la marca: si la marca falla o se revierte, el contador MUST NOT cambiar. Dos marcas simultáneas sobre turnos distintos del mismo paciente MUST sumar dos.

#### Scenario: Contador incrementado
- **GIVEN** un paciente con 1 ausencia
- **WHEN** el odontólogo marca ausente otro turno suyo
- **THEN** la ficha del paciente informa `cantidadAusencias` 2

#### Scenario: Marcas simultáneas del mismo paciente
- **GIVEN** un paciente con 0 ausencias y dos turnos suyos pasada la tolerancia
- **WHEN** se marcan ausentes los dos al mismo tiempo
- **THEN** el paciente queda con 2 ausencias

#### Scenario: Marca rechazada no cuenta
- **WHEN** una marca de ausente es rechazada con `422`
- **THEN** el contador del paciente no cambia

### Requirement: Invitación a reprogramar
Al marcar un turno ausente el sistema SHALL encolar, en la misma operación, una notificación `AUSENTE_REPROGRAMAR` para el email del paciente con fecha, hora, consultorio, prestación y el enlace a la página pública de reserva (RN-AS-03), sin DNI, tokens ni datos clínicos. Un fallo del envío MUST NOT revertir la marca.

#### Scenario: Notificación encolada
- **WHEN** el odontólogo marca ausente un turno de un paciente con email
- **THEN** queda encolada una notificación `AUSENTE_REPROGRAMAR` de ese turno dirigida al email del paciente

#### Scenario: Canal caído
- **GIVEN** un canal de envío que falla
- **WHEN** el odontólogo marca ausente un turno
- **THEN** el turno queda `AUSENTE` y la notificación queda registrada para reintento

### Requirement: Revertir una ausencia dentro de 24 horas
El odontólogo SHALL poder revertir la marca con `POST /api/v1/turnos/{id}/revertir-ausencia` mientras no hayan pasado 24 horas desde `ausenteMarcadoEn` (RN-AS-06). El turno MUST volver al estado que tenía antes de la marca, `ausenteMarcadoEn` MUST quedar vacío, el contador del paciente MUST bajar en uno y la respuesta MUST ser `200` con el turno.

#### Scenario: Reversión de un turno reservado
- **GIVEN** un turno que estaba `RESERVADO`, marcado `AUSENTE` a las 10:20 y un paciente con 3 ausencias
- **WHEN** el odontólogo revierte la marca a las 11:00 del mismo día
- **THEN** el turno vuelve a `RESERVADO` sin `ausenteMarcadoEn` y el paciente queda con 2 ausencias

#### Scenario: Reversión de un turno confirmado
- **GIVEN** un turno que estaba `CONFIRMADO` y fue marcado `AUSENTE`
- **WHEN** el odontólogo revierte la marca dentro de las 24 horas
- **THEN** el turno vuelve a `CONFIRMADO`

#### Scenario: Marcar de nuevo después de revertir
- **GIVEN** una ausencia revertida
- **WHEN** el odontólogo vuelve a marcar ausente el turno
- **THEN** el turno queda `AUSENTE` y el contador vuelve a subir en uno

### Requirement: Límites de la reversión
Revertir SHALL ser posible solo para un turno `AUSENTE` y antes de que se cumplan 24 horas desde la marca; fuera de plazo o desde otro estado la respuesta MUST ser `422` en formato `ProblemDetail` sin cambios en el turno ni en el contador. Un turno inexistente o de otro consultorio MUST responder `404`.

#### Scenario: Fuera de plazo
- **GIVEN** un turno marcado `AUSENTE` a las 10:20 del lunes
- **WHEN** el odontólogo intenta revertirlo a las 10:20 del martes
- **THEN** la respuesta es `422` y el turno sigue `AUSENTE`

#### Scenario: Turno que no está ausente
- **WHEN** el odontólogo intenta revertir la ausencia de un turno `RESERVADO` o `ATENDIDO`
- **THEN** la respuesta es `422` y el turno no cambia

#### Scenario: Turno inexistente
- **WHEN** el odontólogo intenta revertir la ausencia de un turno inexistente o de otro consultorio
- **THEN** la respuesta es `404`

### Requirement: Aviso pendiente descartado al revertir
Al revertir una ausencia, una notificación `AUSENTE_REPROGRAMAR` de ese turno que todavía no se envió SHALL dejar de enviarse y quedar registrada como no enviada con el motivo de la reversión. Una ya enviada MUST conservarse sin cambios.

#### Scenario: Aviso todavía pendiente
- **GIVEN** un turno recién marcado `AUSENTE` con su `AUSENTE_REPROGRAMAR` pendiente
- **WHEN** el odontólogo revierte la marca y luego se procesan los envíos
- **THEN** el paciente no recibe el email y la notificación queda `FALLIDA` indicando que la ausencia se revirtió

#### Scenario: Aviso ya enviado
- **GIVEN** un turno `AUSENTE` cuyo `AUSENTE_REPROGRAMAR` ya fue `ENVIADA`
- **WHEN** el odontólogo revierte la marca
- **THEN** la notificación sigue `ENVIADA`

### Requirement: Historial de ausencias del paciente
`GET /api/v1/pacientes/{id}/ausencias` SHALL devolver `{cantidad, ausencias: [{turnoId, inicio, prestacionNombre}]}` con los turnos `AUSENTE` del paciente, del más reciente al más antiguo, donde `cantidad` es el contador del paciente (US-061). Un paciente inexistente o de otro consultorio MUST responder `404`; sin sesión, `401`.

#### Scenario: Paciente con ausencias
- **GIVEN** un paciente con turnos `AUSENTE` el 3 y el 10 de noviembre y uno `ATENDIDO` el 17
- **WHEN** el odontólogo consulta su historial
- **THEN** recibe `cantidad` 2 y las dos ausencias, primero la del 10 y luego la del 3, con su prestación

#### Scenario: Paciente sin ausencias
- **WHEN** el odontólogo consulta el historial de un paciente sin ausencias
- **THEN** recibe `cantidad` 0 y una lista vacía

#### Scenario: Ausencia revertida fuera del historial
- **GIVEN** una ausencia que el odontólogo revirtió
- **WHEN** consulta el historial del paciente
- **THEN** ese turno no aparece

#### Scenario: Paciente de otro consultorio
- **WHEN** el odontólogo consulta el historial de un paciente inexistente o de otro consultorio
- **THEN** la respuesta es `404`

### Requirement: Ausencias sin datos sensibles en logs
Las operaciones de ausentismo MUST NOT escribir en los logs el DNI, el email ni el teléfono del paciente; los logs identifican solo ids de turno y de paciente.

#### Scenario: Log de una marca de ausente
- **WHEN** el odontólogo marca ausente un turno y luego revierte la marca
- **THEN** los logs de esas operaciones no contienen el DNI, el email ni el teléfono del paciente
