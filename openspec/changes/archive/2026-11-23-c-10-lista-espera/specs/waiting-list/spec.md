# Spec Delta

## Purpose

Permite que pacientes sin cuenta se anoten en una lista de espera por prestación y rango de fechas, reciban un aviso por email cuando se libera un hueco compatible y se queden con el turno si son los primeros en aceptarlo; el odontólogo puede consultar y retirar entradas.

## ADDED Requirements

### Requirement: Inscripción pública en la lista de espera
`POST /api/v1/publico/lista-espera` SHALL aceptar, sin sesión y con CSRF, prestación, `fechaDesde`, `fechaHasta` (fechas locales inclusivas, `fechaDesde` ≤ `fechaHasta`, `fechaHasta` no anterior a hoy) y los datos de contacto del paciente con los mismos formatos que la solicitud pública de turno. MUST crear la entrada `PENDIENTE_CONFIRMACION` y encolar un email `CONFIRMAR_LISTA_ESPERA` con enlace de confirmación.

#### Scenario: Inscripción válida
- **GIVEN** la prestación activa "Consulta" y un DNI sin entradas
- **WHEN** se envía `POST /api/v1/publico/lista-espera` con "Consulta", un rango de 10 días desde mañana y datos válidos
- **THEN** la respuesta es `202` con el cuerpo genérico, existe una entrada `PENDIENTE_CONFIRMACION` del paciente y una notificación `CONFIRMAR_LISTA_ESPERA` pendiente para su email

#### Scenario: Rango de fechas inválido
- **WHEN** se envía la inscripción con `fechaHasta` anterior a `fechaDesde`
- **THEN** la respuesta es `400` o `422` con el error en `fechaHasta` y no se crea ninguna entrada

#### Scenario: Prestación inexistente o desactivada
- **WHEN** se envía la inscripción para una prestación inexistente o desactivada
- **THEN** la respuesta es `404` o `422` y no se crea ninguna entrada

### Requirement: Anti-abuso de la inscripción pública
La inscripción pública SHALL aplicar el mismo límite por IP y por identidad (DNI y email) que la solicitud pública de turno (`429` con `Retry-After`) y el mismo campo señuelo: con el señuelo completo responde `202` genérico sin crear nada.

#### Scenario: Señuelo completado
- **WHEN** se envía la inscripción con el campo señuelo con contenido
- **THEN** la respuesta es `202` genérica y no se crea entrada, paciente ni notificación

#### Scenario: Límite por IP excedido
- **GIVEN** la IP ya alcanzó el límite por hora de solicitudes públicas
- **WHEN** se envía otra inscripción desde esa IP
- **THEN** la respuesta es `429` con `Retry-After`

### Requirement: Una sola entrada activa o pendiente por paciente y prestación
El sistema SHALL garantizar con una restricción de la base que un paciente tenga a lo sumo una entrada `PENDIENTE_CONFIRMACION` o `ACTIVA` por prestación. Ante un duplicado el servicio MUST fallar con conflicto (`409`); la inscripción pública MUST responder igual el `202` genérico, sin revelar que la entrada existe. Una pendiente cuyo plazo de confirmación venció no cuenta.

#### Scenario: Duplicado desde la página pública
- **GIVEN** el paciente con DNI 30111222 tiene una entrada `ACTIVA` para "Consulta"
- **WHEN** se envía otra inscripción pública del mismo DNI para "Consulta"
- **THEN** la respuesta es `202` genérica y sigue existiendo una sola entrada activa o pendiente

#### Scenario: Duplicado rechazado por la base
- **GIVEN** una entrada `PENDIENTE_CONFIRMACION` vigente de un paciente para "Consulta"
- **WHEN** el servicio intenta crear otra entrada del mismo paciente y prestación
- **THEN** la operación falla con conflicto `409` y no se inserta la segunda entrada

#### Scenario: Pendiente vencida no bloquea
- **GIVEN** una entrada `PENDIENTE_CONFIRMACION` cuyo plazo de confirmación ya pasó
- **WHEN** el mismo paciente se inscribe otra vez para la misma prestación
- **THEN** la entrada anterior queda `VENCIDA` y se crea una nueva `PENDIENTE_CONFIRMACION`

### Requirement: Paciente identificado por DNI sin modificar sus datos
La inscripción SHALL buscar al paciente por DNI y crearlo si no existe, sin modificar los datos de uno existente. Si el paciente está dado de baja MUST responder el mismo `202` genérico sin crear la entrada.

#### Scenario: Paciente existente
- **GIVEN** un paciente activo con DNI 30111222 y teléfono "1111"
- **WHEN** se inscribe con ese DNI y teléfono "2222"
- **THEN** la entrada se asocia a ese paciente y su teléfono sigue siendo "1111"

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente dado de baja con DNI 30111222
- **WHEN** se inscribe con ese DNI
- **THEN** la respuesta es `202` genérica y no se crea ninguna entrada

### Requirement: Generación de avisos al liberarse un hueco
Cuando se libera un hueco (cancelación o movimiento de un turno, ya confirmados), el sistema SHALL crear un aviso `ENVIADO` por cada entrada `ACTIVA` compatible y encolar a todos a la vez una notificación `AVISO_HUECO` con enlace de aceptación de un solo uso. Si no hay compatibles no crea nada y el hueco queda disponible para reserva normal.

#### Scenario: N entradas compatibles
- **GIVEN** tres entradas `ACTIVA` compatibles de tres pacientes distintos
- **WHEN** el odontólogo cancela un turno y se libera el hueco
- **THEN** existen tres avisos `ENVIADO` para ese hueco y tres notificaciones `AVISO_HUECO` pendientes, una por paciente

#### Scenario: Sin compatibles
- **GIVEN** no hay entradas `ACTIVA` compatibles
- **WHEN** se libera un hueco
- **THEN** no se crea ningún aviso ni notificación

#### Scenario: La operación que libera se revierte
- **WHEN** la cancelación de un turno falla y se revierte
- **THEN** no se crea ningún aviso

### Requirement: Compatibilidad de una entrada con un hueco
Una entrada SHALL ser compatible con un hueco si está `ACTIVA`, es de la misma prestación del turno que lo liberó, la fecha local (`America/Argentina/Buenos_Aires`) del inicio del hueco está dentro de `[fechaDesde, fechaHasta]` y su paciente está activo. Un hueco que empieza antes de ahora más la anticipación mínima de reserva MUST NOT generar avisos.

#### Scenario: Otra prestación
- **GIVEN** una entrada `ACTIVA` para "Limpieza"
- **WHEN** se libera un hueco de un turno de "Consulta"
- **THEN** esa entrada no recibe aviso

#### Scenario: Fecha fuera del rango
- **GIVEN** una entrada `ACTIVA` para "Consulta" del 10 al 15 del mes
- **WHEN** se libera un hueco de "Consulta" el día 16
- **THEN** esa entrada no recibe aviso

#### Scenario: Entrada pendiente o cancelada
- **GIVEN** una entrada `PENDIENTE_CONFIRMACION` y otra `CANCELADA`, ambas de "Consulta" y con el rango adecuado
- **WHEN** se libera un hueco de "Consulta"
- **THEN** ninguna recibe aviso

#### Scenario: Hueco sin anticipación suficiente
- **GIVEN** una anticipación mínima de reserva de 120 minutos y una entrada compatible
- **WHEN** se libera un hueco que empieza dentro de 60 minutos
- **THEN** no se crea ningún aviso

### Requirement: Vencimiento de un aviso
Cada aviso SHALL vencer a los `vencimiento_aviso_min` minutos de creado o al inicio del hueco, lo que ocurra primero; su enlace vence en el mismo instante. Un aviso vencido MUST quedar `EXPIRADO` al accederse y también por un barrido periódico.

#### Scenario: Aviso vencido al acceder
- **GIVEN** un aviso `ENVIADO` con `vencimiento_aviso_min` 60 creado hace 61 minutos según el reloj
- **WHEN** se consulta o se intenta aceptar con su enlace
- **THEN** la respuesta es `410` y el aviso queda `EXPIRADO`

#### Scenario: Vence al inicio del hueco
- **GIVEN** `vencimiento_aviso_min` 60 y un hueco que empieza dentro de 30 minutos
- **WHEN** se crea el aviso
- **THEN** su vencimiento es el inicio del hueco

#### Scenario: Barrido periódico
- **GIVEN** dos avisos `ENVIADO` vencidos y uno vigente
- **WHEN** corre el barrido de vencimiento
- **THEN** los dos vencidos quedan `EXPIRADO` y el vigente sigue `ENVIADO`

### Requirement: Resumen de un aviso sin consumir el enlace
`GET /api/v1/avisos/{token}` SHALL devolver, sin sesión, el consultorio, la prestación, inicio y fin del hueco, el vencimiento y si el aviso todavía puede aceptarse (`disponible`), sin datos del paciente. MUST NOT consumir el token ni modificar el aviso, salvo marcarlo `EXPIRADO` si venció.

#### Scenario: Aviso vigente
- **GIVEN** un aviso `ENVIADO` vigente
- **WHEN** se envía `GET /api/v1/avisos/{token}` dos veces
- **THEN** ambas respuestas son `200` con `disponible` verdadero y el token sigue sin usar

#### Scenario: Hueco ya tomado por otro paciente
- **GIVEN** un aviso `CERRADO` porque otro paciente aceptó el hueco
- **WHEN** se envía el `GET` de su enlace
- **THEN** la respuesta es `200` con `disponible` falso

### Requirement: Aceptar un aviso, el primero gana
`POST /api/v1/avisos/{token}/aceptar` SHALL, en una única transacción, consumir el token, crear el turno `RESERVADO` con origen `LISTA_ESPERA` para el paciente de la entrada, pasar el aviso a `ACEPTADO`, la entrada a `CUBIERTA` (con el turno resultante), los demás avisos del hueco a `CERRADO` y encolar `CONFIRMACION_TURNO` al ganador. La no superposición MUST decidirla la restricción de exclusión de la base.

#### Scenario: Aceptación exitosa
- **GIVEN** dos avisos `ENVIADO` vigentes del mismo hueco, de los pacientes A y B
- **WHEN** A envía `POST /api/v1/avisos/{token}/aceptar`
- **THEN** la respuesta es `200` con el turno `RESERVADO` de origen `LISTA_ESPERA`, el aviso de A queda `ACEPTADO`, su entrada `CUBIERTA`, el aviso de B `CERRADO` y hay una notificación `CONFIRMACION_TURNO` para A

#### Scenario: Dos aceptaciones simultáneas
- **GIVEN** dos avisos `ENVIADO` vigentes del mismo hueco
- **WHEN** ambos pacientes aceptan al mismo tiempo
- **THEN** exactamente una respuesta es `200`, la otra es `409`, existe un solo turno en ese rango y una sola entrada `CUBIERTA`

#### Scenario: Aceptar después de que otro ganó
- **GIVEN** un aviso `CERRADO` porque otro paciente ya aceptó
- **WHEN** se envía el `POST` de aceptar con su enlace
- **THEN** la respuesta es `409` y no se crea ningún turno

#### Scenario: Hueco tomado por una reserva normal
- **GIVEN** un aviso `ENVIADO` vigente y el odontólogo reservó un turno que se superpone con ese hueco
- **WHEN** el paciente acepta el aviso
- **THEN** la respuesta es `409`, no se crea el turno, la entrada sigue `ACTIVA` y los avisos de ese hueco quedan `CERRADO`

### Requirement: Rechazo uniforme de enlaces de aviso inválidos
Un token de aviso inexistente, usado, vencido, revocado o de otro propósito SHALL responder `410 Gone` con el mismo cuerpo genérico en el `GET` y en el `POST`, sin distinguir la causa ni registrar el valor del token.

#### Scenario: Enlace ya usado
- **GIVEN** un aviso que su paciente ya aceptó
- **WHEN** se vuelve a enviar el `POST` de aceptar con el mismo enlace
- **THEN** la respuesta es `410`

#### Scenario: Token de otro propósito
- **GIVEN** un token vigente `CONFIRMAR_TURNO`
- **WHEN** se envía `GET /api/v1/avisos/{token}`
- **THEN** la respuesta es `410` y el token no se consume

### Requirement: Listado de la lista de espera para el odontólogo
`GET /api/v1/lista-espera` SHALL devolver, con sesión, las entradas del consultorio con paciente (nombre y apellido), prestación, rango de fechas, estado y fecha de alta, de la más reciente a la más antigua, sin filtros. Sin sesión MUST responder `401`.

#### Scenario: Listado con sesión
- **GIVEN** una entrada `ACTIVA` y una `CUBIERTA`
- **WHEN** el odontólogo envía `GET /api/v1/lista-espera`
- **THEN** la respuesta es `200` con ambas entradas y sus estados

#### Scenario: Sin sesión
- **WHEN** se envía `GET /api/v1/lista-espera` sin sesión
- **THEN** la respuesta es `401`

### Requirement: Retiro de una entrada por el odontólogo
`DELETE /api/v1/lista-espera/{id}` SHALL pasar a `CANCELADA` una entrada `PENDIENTE_CONFIRMACION` o `ACTIVA` y revocar su enlace de confirmación vigente, respondiendo `204`. Una entrada inexistente o de otro consultorio MUST responder `404`; una en estado final, `422`.

#### Scenario: Retirar una entrada activa
- **GIVEN** una entrada `ACTIVA`
- **WHEN** el odontólogo envía `DELETE /api/v1/lista-espera/{id}`
- **THEN** la respuesta es `204`, la entrada queda `CANCELADA` y deja de recibir avisos

#### Scenario: Entrada ya cubierta
- **GIVEN** una entrada `CUBIERTA`
- **WHEN** el odontólogo envía el `DELETE`
- **THEN** la respuesta es `422` y la entrada no cambia

### Requirement: Sin datos personales ni tokens en logs
Los logs de inscripción, avisos y aceptación SHALL contener solo ids internos y estados; MUST NOT incluir el valor del token, la URL del enlace, DNI, email, teléfono ni nombre del paciente.

#### Scenario: Log de una aceptación
- **WHEN** se acepta un aviso y se registra el resultado en el log
- **THEN** el log no contiene el token, el email ni el DNI del paciente
