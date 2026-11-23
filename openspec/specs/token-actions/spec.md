# token-actions Specification

## Purpose

Permite que el paciente, sin cuenta, confirme su solicitud, confirme su turno o lo cancele desde un enlace de email con token de un solo uso y un solo propósito, viendo primero un resumen que nunca consume el token.

## Requirements

### Requirement: Resumen de la acción sin consumir el token
`GET /api/v1/acciones/{token}` SHALL devolver, para un token vigente de propósito `CONFIRMAR_SOLICITUD`, `CONFIRMAR_TURNO` o `CANCELAR_TURNO`, la acción, su vencimiento y un resumen del turno (consultorio, prestación, inicio, fin, estado), sin datos del paciente. MUST NOT consumir ni modificar el token ni el turno.

#### Scenario: Resumen de un token vigente
- **GIVEN** un token `CANCELAR_TURNO` vigente para un turno `RESERVADO` de "Consulta"
- **WHEN** se envía `GET /api/v1/acciones/{token}` sin sesión
- **THEN** la respuesta es `200` con `accion` `CANCELAR_TURNO`, `venceEn`, y un `turno` con consultorio, prestación "Consulta", `inicio`, `fin` y `estado` `RESERVADO`, sin nombre, DNI, email ni teléfono del paciente

#### Scenario: Mostrar varias veces no consume
- **GIVEN** un token `CONFIRMAR_TURNO` vigente
- **WHEN** se envía el `GET` tres veces y luego el `POST` de confirmación
- **THEN** los tres `GET` responden `200` y el `POST` ejecuta la acción

#### Scenario: Token de otro tipo de ruta
- **GIVEN** un token vigente de propósito `ACEPTAR_AVISO`
- **WHEN** se envía `GET /api/v1/acciones/{token}`
- **THEN** la respuesta es `410` y el token no se consume

### Requirement: Disponibilidad de la acción en el resumen
El resumen SHALL indicar si la acción puede ejecutarse ahora (`disponible`) y, si no, el motivo: `ESTADO_NO_PERMITE` cuando el estado actual del turno no admite la acción, o `FUERA_DE_LIMITE` cuando una cancelación está fuera del límite de horas. Para `CANCELAR_TURNO` MUST incluir el instante límite de cancelación.

#### Scenario: Cancelación fuera del límite
- **GIVEN** un límite de cancelación de 2 horas y un turno `RESERVADO` que empieza dentro de 1 hora con un token `CANCELAR_TURNO` vigente
- **WHEN** se envía el `GET` del token
- **THEN** la respuesta es `200` con `disponible` falso, `motivo` `FUERA_DE_LIMITE` y `limiteCancelacion` igual al inicio menos 2 horas

#### Scenario: Turno ya cancelado por el odontólogo
- **GIVEN** un token `CONFIRMAR_TURNO` vigente para un turno que el odontólogo canceló
- **WHEN** se envía el `GET` del token
- **THEN** la respuesta es `200` con `estado` `CANCELADO`, `disponible` falso y `motivo` `ESTADO_NO_PERMITE`

### Requirement: Ejecución de la acción por POST
`POST /api/v1/acciones/{token}` SHALL recibir la acción esperada, consumir el token con ese propósito y ejecutar la acción en una única transacción, respondiendo `200` con la acción ejecutada y el resumen actualizado. Si la acción falla, el token MUST seguir sin usar.

#### Scenario: Acción y consumo juntos
- **GIVEN** un token `CONFIRMAR_TURNO` vigente para un turno `RESERVADO`
- **WHEN** se envía el `POST` con la acción `CONFIRMAR_TURNO`
- **THEN** la respuesta es `200` con el turno `CONFIRMADO` y el token queda usado

#### Scenario: La acción falla y el token sigue vigente
- **GIVEN** un token `CONFIRMAR_TURNO` vigente para un turno `CANCELADO`
- **WHEN** se envía el `POST` con la acción `CONFIRMAR_TURNO`
- **THEN** la respuesta es `422`, el turno sigue `CANCELADO` y un `GET` posterior del token responde `200`

#### Scenario: Acción que no coincide con el propósito
- **GIVEN** un token `CONFIRMAR_TURNO` vigente
- **WHEN** se envía el `POST` con la acción `CANCELAR_TURNO`
- **THEN** la respuesta es `410`, el turno no cambia y el token sigue consumible con `CONFIRMAR_TURNO`

### Requirement: Confirmar la solicitud pública
La acción `CONFIRMAR_SOLICITUD` SHALL pasar el turno de `PENDIENTE_CONFIRMACION` a `RESERVADO` y encolar en la misma transacción una notificación `CONFIRMACION_TURNO`. Si `pendiente_hasta` ya pasó según el reloj de la aplicación, MUST responder `410` sin cambios, aunque la expiración periódica no haya corrido.

#### Scenario: Confirmación a tiempo
- **GIVEN** un turno `PENDIENTE_CONFIRMACION` con `pendiente_hasta` dentro de 10 minutos y su token `CONFIRMAR_SOLICITUD`
- **WHEN** se envía el `POST` con la acción `CONFIRMAR_SOLICITUD`
- **THEN** la respuesta es `200`, el turno queda `RESERVADO` y queda encolada una `CONFIRMACION_TURNO` para ese turno

#### Scenario: Solicitud vencida
- **GIVEN** un turno pendiente cuyo `pendiente_hasta` ya pasó y que todavía no fue marcado `EXPIRADO`
- **WHEN** se envía el `POST` con su token `CONFIRMAR_SOLICITUD`
- **THEN** la respuesta es `410` y el turno no pasa a `RESERVADO`

### Requirement: Confirmar asistencia al turno
La acción `CONFIRMAR_TURNO` SHALL pasar el turno de `RESERVADO` a `CONFIRMADO`. Desde cualquier otro estado MUST responder `422` sin cambios.

#### Scenario: Confirmación de asistencia
- **GIVEN** un turno `RESERVADO` y su token `CONFIRMAR_TURNO`
- **WHEN** se envía el `POST` con la acción `CONFIRMAR_TURNO`
- **THEN** la respuesta es `200` y el turno queda `CONFIRMADO`

#### Scenario: Turno ya atendido
- **GIVEN** un turno `ATENDIDO` con un token `CONFIRMAR_TURNO` vigente
- **WHEN** se envía el `POST` con la acción `CONFIRMAR_TURNO`
- **THEN** la respuesta es `422` y el turno sigue `ATENDIDO`

### Requirement: Cancelación por el paciente con límite de horas
La acción `CANCELAR_TURNO` SHALL cancelar un turno `RESERVADO` o `CONFIRMADO` con `cancelado_por = PACIENTE` solo si el momento actual es anterior o igual al inicio menos `limite_cancelacion_horas`. Fuera del límite MUST responder `422` con el código `limite-cancelacion`, sin cambios ni consumo del token.

#### Scenario: Cancelación dentro del límite
- **GIVEN** un límite de 2 horas y un turno `CONFIRMADO` que empieza dentro de 3 horas
- **WHEN** se envía el `POST` con la acción `CANCELAR_TURNO`
- **THEN** la respuesta es `200`, el turno queda `CANCELADO` con `canceladoPor` `PACIENTE` y su intervalo queda libre

#### Scenario: Cancelación justo en el límite
- **GIVEN** un límite de 2 horas y un turno `RESERVADO` que empieza exactamente dentro de 2 horas según el reloj de la aplicación
- **WHEN** se envía el `POST` con la acción `CANCELAR_TURNO`
- **THEN** la respuesta es `200` y el turno queda `CANCELADO`

#### Scenario: Cancelación fuera del límite
- **GIVEN** un límite de 2 horas y un turno `RESERVADO` que empieza dentro de 1 hora y 59 minutos
- **WHEN** se envía el `POST` con la acción `CANCELAR_TURNO`
- **THEN** la respuesta es `422` con `type` terminado en `limite-cancelacion`, el turno sigue `RESERVADO` y el token sigue vigente

### Requirement: Efectos de la cancelación por el paciente
Una cancelación por el paciente SHALL emitir los eventos de turno cancelado y de hueco liberado con el intervalo del turno, encolar la notificación `CANCELACION` e invalidar los demás tokens vigentes de ese turno, todo en la misma transacción.

#### Scenario: Hueco liberado y tokens invalidados
- **GIVEN** un turno `RESERVADO` de 10:00 a 10:30 con tokens vigentes `CONFIRMAR_TURNO` y `CANCELAR_TURNO`
- **WHEN** el paciente lo cancela con su token `CANCELAR_TURNO`
- **THEN** se emite un evento de hueco liberado para 10:00 a 10:30, queda encolada una `CANCELACION` y el `GET` del token `CONFIRMAR_TURNO` responde `410`

#### Scenario: Confirmar no invalida la cancelación
- **GIVEN** un turno `RESERVADO` con tokens vigentes `CONFIRMAR_TURNO` y `CANCELAR_TURNO`
- **WHEN** el paciente confirma con `CONFIRMAR_TURNO`
- **THEN** el token `CANCELAR_TURNO` sigue vigente y permite cancelar dentro del límite

### Requirement: Rechazo uniforme de enlaces inválidos
Un token inexistente, mal formado, vencido, usado, revocado o de propósito incorrecto SHALL producir, tanto en `GET` como en `POST`, el mismo `410` genérico, sin datos del turno y sin cambios de estado.

#### Scenario: Token usado
- **GIVEN** un token `CONFIRMAR_TURNO` ya usado
- **WHEN** se envía su `GET` o su `POST`
- **THEN** ambas respuestas son `410` con el mismo cuerpo que un token inexistente

#### Scenario: Token vencido
- **GIVEN** un token cuyo vencimiento ya pasó según el reloj de la aplicación
- **WHEN** se envía su `GET`
- **THEN** la respuesta es `410` genérica

### Requirement: Doble ejecución concurrente
Dos `POST` simultáneos con el mismo token SHALL resolverse con exactamente una ejecución exitosa; la otra MUST recibir `410` sin efectos. Dos acciones simultáneas sobre el mismo turno con tokens distintos MUST dejar el turno en un estado coherente con una sola de ellas.

#### Scenario: Mismo token dos veces a la vez
- **GIVEN** un token `CANCELAR_TURNO` vigente dentro del límite
- **WHEN** se envían dos `POST` de cancelación al mismo tiempo
- **THEN** exactamente uno responde `200`, el otro `410`, y existe un único evento de hueco liberado y una única `CANCELACION`

#### Scenario: Confirmar y cancelar a la vez
- **GIVEN** un turno `RESERVADO` con tokens vigentes `CONFIRMAR_TURNO` y `CANCELAR_TURNO`
- **WHEN** se envían ambos `POST` al mismo tiempo
- **THEN** si la cancelación respondió `200` el turno termina `CANCELADO`; si respondió `409` por modificación concurrente el turno termina `CONFIRMADO` y el token `CANCELAR_TURNO` sigue vigente; nunca se pierde una de las dos actualizaciones

### Requirement: Rutas por token sin sesión y con CSRF
Las rutas `/api/v1/acciones/**` SHALL funcionar sin sesión. El `POST` MUST exigir el token CSRF como el resto de las escrituras de la API; el `GET` MUST emitir la cookie CSRF si la solicitud no la trae.

#### Scenario: POST sin cabecera CSRF
- **GIVEN** un token `CONFIRMAR_TURNO` vigente
- **WHEN** se envía su `POST` sin sesión y sin cabecera `X-XSRF-TOKEN`
- **THEN** la respuesta es `403`, el turno no cambia y el token sigue vigente

#### Scenario: GET entrega la cookie CSRF
- **WHEN** se envía `GET /api/v1/acciones/{token}` sin cookies
- **THEN** la respuesta incluye la cookie `XSRF-TOKEN` y un `POST` posterior con ese valor en `X-XSRF-TOKEN` no es rechazado por CSRF

### Requirement: Token fuera de los logs
Ni las rutas por token ni la ejecución de sus acciones MUST registrar en logs la URL con el token, el valor del token ni datos personales del paciente; solo identificadores internos.

#### Scenario: Acción registrada sin token
- **WHEN** se ejecutan un `GET` y un `POST` exitosos y uno con token inválido
- **THEN** los logs de la aplicación no contienen el valor de ningún token
