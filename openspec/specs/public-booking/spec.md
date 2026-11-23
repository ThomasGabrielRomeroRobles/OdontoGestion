# public-booking Specification

## Purpose

Permite que un paciente sin cuenta pida un turno desde la página pública entre los horarios realmente libres, con retención temporal del hueco hasta que confirme por email y con medidas anti-abuso que no revelan si un DNI o email ya existen.

## Requirements

### Requirement: Prestaciones públicas
El sistema SHALL exponer sin sesión la lista de prestaciones activas del consultorio con su identificador, nombre y duración en minutos, ordenada por nombre. Las prestaciones inactivas MUST NOT aparecer.

#### Scenario: Solo prestaciones activas
- **GIVEN** las prestaciones activas "Consulta" (30 min) y "Limpieza" (45 min) y una inactiva "Blanqueamiento"
- **WHEN** se envía `GET /api/v1/publico/prestaciones` sin sesión
- **THEN** la respuesta es `200` con "Consulta" y "Limpieza", cada una con `id`, `nombre` y `duracionMin`, y sin "Blanqueamiento"

#### Scenario: Sin prestaciones activas
- **GIVEN** que no hay prestaciones activas
- **WHEN** se consulta la lista pública de prestaciones
- **THEN** la respuesta es `200` con una lista vacía

### Requirement: Disponibilidad pública
El sistema SHALL exponer sin sesión los huecos libres de una prestación activa entre dos fechas inclusivas (zona del consultorio, rango acotado como en la consulta del odontólogo), aplicando la política pública de anticipación mínima. La respuesta MUST contener solo inicio y fin de cada hueco, sin datos de turnos ni de pacientes.

#### Scenario: Huecos libres con anticipación pública
- **GIVEN** una anticipación mínima de 120 minutos, un turno `RESERVADO` de 10:00 a 10:30 y un hueco libre que empieza dentro de 60 minutos
- **WHEN** se envía `GET /api/v1/publico/disponibilidad` con la prestación y el día
- **THEN** la respuesta es `200` con huecos `{inicio, fin}` que excluyen el de 10:00, el que empieza dentro de 60 minutos, y no contiene ningún dato de paciente ni de turno

#### Scenario: Pendientes vigentes ocupan y vencidas no
- **GIVEN** un turno `PENDIENTE_CONFIRMACION` vigente de 11:00 a 11:30 y otro vencido de 12:00 a 12:30
- **WHEN** se consulta la disponibilidad pública de ese día
- **THEN** 11:00 no aparece como libre y 12:00 sí

#### Scenario: Prestación inactiva, inexistente o rango inválido
- **WHEN** se consulta la disponibilidad pública de una prestación inactiva, de una inexistente o con un rango mayor al permitido
- **THEN** la respuesta es `422` para la inactiva y para el rango, y `404` para la inexistente

### Requirement: Solicitud pública pendiente de confirmación
Una solicitud pública válida SHALL, en una única transacción, expirar las pendientes vencidas, reutilizar el paciente con ese DNI sin modificar sus datos o crearlo, crear un turno `PENDIENTE_CONFIRMACION` de origen `PUBLICA` con `pendiente_hasta` = ahora + el vencimiento de pendientes configurado, y encolar la notificación de confirmación. Si cualquier paso falla, nada MUST persistir.

#### Scenario: Solicitud exitosa
- **GIVEN** un hueco libre a las 10:00 para la prestación "Consulta" y un vencimiento de pendientes de 30 minutos
- **WHEN** se envía `POST /api/v1/publico/solicitudes-turno` sin sesión con prestación, inicio 10:00, nombre, apellido, DNI, email y teléfono válidos
- **THEN** la respuesta es `202`, existe un turno `PENDIENTE_CONFIRMACION` de origen `PUBLICA` a las 10:00 con `pendiente_hasta` = ahora + 30 minutos y queda encolada una notificación `CONFIRMAR_SOLICITUD` para ese turno

#### Scenario: DNI existente no sobrescribe el contacto
- **GIVEN** un paciente con DNI `30123456` y email `ana@ejemplo.test`
- **WHEN** se envía una solicitud pública válida con DNI `30.123.456` y email `otra@ejemplo.test`
- **THEN** la respuesta es `202`, el turno pendiente pertenece a ese paciente, su email sigue siendo `ana@ejemplo.test` y la notificación se dirige a `ana@ejemplo.test`

#### Scenario: DNI nuevo crea al paciente
- **GIVEN** que no existe ningún paciente con DNI `30999888`
- **WHEN** se envía una solicitud pública válida con ese DNI
- **THEN** existe un paciente nuevo con los datos ingresados y un turno pendiente a su nombre

#### Scenario: Pendiente vencida liberada antes de solicitar
- **GIVEN** un turno `PENDIENTE_CONFIRMACION` de 10:00 a 10:30 cuyo `pendiente_hasta` ya pasó
- **WHEN** se envía una solicitud pública válida para las 10:00
- **THEN** la respuesta es `202` y el turno anterior queda `EXPIRADO`

#### Scenario: Sin efectos ante un error
- **WHEN** una solicitud pública con DNI nuevo falla por conflicto de agenda
- **THEN** no se crea el paciente, ni el turno, ni la notificación

### Requirement: Respuesta sin enumeración
Toda solicitud pública aceptada SHALL responder `202` con el mismo cuerpo genérico ("si los datos son correctos, recibirás un email"), sea el DNI o el email nuevo o existente. La respuesta MUST NOT incluir identificadores de paciente ni de turno, ni datos del paciente.

#### Scenario: Cuerpo idéntico para DNI nuevo y existente
- **WHEN** se comparan las respuestas a una solicitud válida con DNI nuevo y a otra con DNI existente
- **THEN** ambas son `202` con exactamente el mismo cuerpo, sin `id` de turno ni de paciente

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente dado de baja con DNI `30111222`
- **WHEN** se envía una solicitud pública válida con ese DNI
- **THEN** la respuesta es `202` con el cuerpo genérico, no se crea turno ni notificación y el paciente sigue dado de baja

### Requirement: Validación estricta de la solicitud
La solicitud pública SHALL exigir prestación, inicio, nombre, apellido, DNI de 7 u 8 dígitos (con o sin puntos), email válido y teléfono, con los mismos formatos que el alta de pacientes. Un dato faltante o inválido MUST responder `400` indicando el campo, sin efectos.

#### Scenario: DNI inválido
- **WHEN** se envía una solicitud pública con DNI `12AB`
- **THEN** la respuesta es `400` con un error para el campo `dni` y no se crea turno ni paciente

#### Scenario: Falta el email
- **WHEN** se envía una solicitud pública sin `email`
- **THEN** la respuesta es `400` con un error para el campo `email`

### Requirement: Horario válido para una solicitud pública
El inicio pedido SHALL caer dentro de un horario de trabajo, fuera de bloqueos, alineado a la granularidad y al menos la anticipación mínima pública después del momento actual; si no, la respuesta MUST ser `422` con error en el campo `inicio`. Una prestación inactiva MUST responder `422` y una inexistente `404`.

#### Scenario: Fuera del horario de trabajo
- **WHEN** se envía una solicitud pública para un inicio fuera del horario de trabajo
- **THEN** la respuesta es `422` con error en `inicio` y no se crea turno

#### Scenario: Dentro de un bloqueo
- **GIVEN** un bloqueo de agenda de 10:00 a 12:00
- **WHEN** se envía una solicitud pública para las 10:30
- **THEN** la respuesta es `422` con error en `inicio`

#### Scenario: Antes de la anticipación mínima
- **GIVEN** una anticipación mínima de 120 minutos
- **WHEN** se envía una solicitud pública para un hueco libre que empieza dentro de 60 minutos
- **THEN** la respuesta es `422` con error en `inicio`

### Requirement: Conflicto en la solicitud pública con alternativas
Si el intervalo pedido ya está ocupado, la respuesta SHALL ser `409` en formato `ProblemDetail` con `alternativas` de hasta 3 huecos libres de la misma prestación desde el inicio pedido, calculadas con la política pública. La garantía de no superposición MUST ser la restricción de la base.

#### Scenario: Hueco tomado entre la consulta y la solicitud
- **GIVEN** un turno `RESERVADO` de 10:00 a 10:30 y huecos libres públicos a las 10:30, 10:45 y 11:00
- **WHEN** se envía una solicitud pública para las 10:00
- **THEN** la respuesta es `409` con `alternativas` 10:30, 10:45 y 11:00 y no se crea turno ni paciente

#### Scenario: Solicitudes simultáneas del mismo horario
- **GIVEN** un hueco libre a las 10:00
- **WHEN** dos solicitudes públicas válidas con DNI distintos piden las 10:00 al mismo tiempo
- **THEN** exactamente una recibe `202` y deja un turno pendiente, y la otra recibe `409`

### Requirement: Limitación de tasa de solicitudes públicas
El sistema SHALL limitar las solicitudes públicas a `PUBLIC_RATE_LIMIT_PER_HOUR` por hora por IP de origen y, por separado, por DNI y por email. Excedido un límite, MUST responder `429` en formato `ProblemDetail` con cabecera `Retry-After`, sin efectos y con el mismo cuerpo para cualquier límite.

#### Scenario: Exceso por IP
- **GIVEN** un límite de 5 por hora
- **WHEN** la misma IP envía 6 solicitudes públicas válidas con DNI y emails distintos dentro de una hora
- **THEN** la sexta recibe `429` con `Retry-After` y no crea turno

#### Scenario: Exceso por DNI desde IP distintas
- **GIVEN** un límite de 5 por hora
- **WHEN** se envían 6 solicitudes con el mismo DNI desde IP distintas dentro de una hora
- **THEN** la sexta recibe `429`

#### Scenario: La ventana se renueva
- **GIVEN** una IP que alcanzó el límite
- **WHEN** pasa una hora según el reloj de la aplicación
- **THEN** la siguiente solicitud válida de esa IP recibe `202`

### Requirement: Máximo de solicitudes pendientes por DNI o email
Un DNI o un email SHALL tener como máximo 2 turnos `PENDIENTE_CONFIRMACION` vigentes. Una solicitud que lo supere MUST responder `429` con el mismo cuerpo que el límite de tasa, sin efectos.

#### Scenario: Tercera pendiente con el mismo DNI
- **GIVEN** dos turnos `PENDIENTE_CONFIRMACION` vigentes del paciente con DNI `30123456`
- **WHEN** se envía otra solicitud pública válida con ese DNI
- **THEN** la respuesta es `429` y no se crea un tercer turno

#### Scenario: Tercera pendiente con el mismo email y otro DNI
- **GIVEN** dos turnos pendientes vigentes de pacientes cuyo email es `x@ejemplo.test`
- **WHEN** se envía una solicitud pública con DNI nuevo y email `x@ejemplo.test`
- **THEN** la respuesta es `429`

#### Scenario: Pendientes vencidas o confirmadas no cuentan
- **GIVEN** un paciente con una pendiente vencida y un turno `RESERVADO`
- **WHEN** envía una solicitud pública válida
- **THEN** la respuesta es `202`

### Requirement: Campo señuelo
La solicitud pública SHALL incluir un campo señuelo que una persona no completa. Si llega con contenido, el sistema MUST responder `202` con el mismo cuerpo genérico sin crear paciente, turno ni notificación; la solicitud cuenta para el límite por IP.

#### Scenario: Señuelo completado
- **WHEN** se envía una solicitud pública válida con el campo señuelo con texto
- **THEN** la respuesta es `202` con el cuerpo genérico y no se crea paciente, turno ni notificación

#### Scenario: Señuelo vacío
- **WHEN** se envía una solicitud pública válida con el campo señuelo vacío o ausente
- **THEN** la solicitud se procesa normalmente

### Requirement: Notificación de confirmación de la solicitud
Al crearse un turno pendiente de origen público, el sistema SHALL encolar, en la misma transacción, una notificación `CONFIRMAR_SOLICITUD` al email registrado del paciente, con fecha, hora, consultorio, prestación y un enlace `CONFIRMAR_SOLICITUD` que vence en `pendiente_hasta`. MUST NOT incluir DNI, datos clínicos ni el token en la fila encolada.

#### Scenario: Notificación encolada con enlace
- **WHEN** una solicitud pública crea un turno pendiente con `pendiente_hasta` a las 10:30 UTC
- **THEN** queda una notificación `CONFIRMAR_SOLICITUD` para ese turno con un enlace de propósito `CONFIRMAR_SOLICITUD` que vence a las 10:30 UTC y sin el valor de ningún token

#### Scenario: Turno del odontólogo sin confirmación de solicitud
- **WHEN** el odontólogo crea un turno `RESERVADO`
- **THEN** no se encola `CONFIRMAR_SOLICITUD`

### Requirement: Expiración de la solicitud no confirmada
Una solicitud pública no confirmada antes de `pendiente_hasta` SHALL pasar a `EXPIRADO` por la expiración periódica o antes de la siguiente reserva, y su hueco MUST volver a aparecer en la disponibilidad pública.

#### Scenario: Hueco liberado tras el vencimiento
- **GIVEN** una solicitud pública pendiente a las 10:00 con `pendiente_hasta` dentro de 30 minutos
- **WHEN** el reloj de la aplicación avanza 30 minutos y corre la expiración periódica
- **THEN** el turno queda `EXPIRADO` y las 10:00 aparece en la disponibilidad pública

### Requirement: Datos personales fuera de los logs públicos
Los endpoints públicos MUST NOT registrar en logs DNI, email, teléfono, nombre, IP completa ni el contenido del campo señuelo.

#### Scenario: Solicitud registrada sin datos personales
- **WHEN** se procesa una solicitud pública exitosa, una rechazada por límite y una con señuelo
- **THEN** los logs de la aplicación no contienen el DNI, el email, el teléfono ni el nombre enviados
