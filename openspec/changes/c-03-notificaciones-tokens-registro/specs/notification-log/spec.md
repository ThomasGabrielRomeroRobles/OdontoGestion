# Spec Delta

## Purpose

Dar al odontólogo visibilidad sobre los envíos a pacientes (qué se envió, a quién, con qué resultado) y una forma acotada de reintentar los que fallaron, desde la API autenticada.

## ADDED Requirements

### Requirement: Consulta del registro de envíos
`GET /api/v1/notificaciones` SHALL devolver, paginadas y ordenadas de la más reciente a la más antigua, las notificaciones del consultorio con id, tipo, canal, destinatario, estado, intentos, error, referencia, programada para, enviada en y creada en. La respuesta MUST NOT incluir el cuerpo del mensaje, valores de tokens ni datos clínicos.

#### Scenario: Listado paginado
- **GIVEN** 25 notificaciones registradas
- **WHEN** el odontólogo autenticado pide `GET /api/v1/notificaciones?page=0&size=20`
- **THEN** recibe `200` con 20 elementos ordenados por creación descendente, el total 25 y la indicación de que hay más páginas

#### Scenario: Sin cuerpo ni tokens en la respuesta
- **WHEN** el odontólogo consulta el registro que incluye un `RECORDATORIO` enviado con enlaces
- **THEN** ningún campo de la respuesta contiene el cuerpo del email ni un token

#### Scenario: Tamaño de página acotado
- **WHEN** se pide `size=500`
- **THEN** la respuesta es `400` en formato `ProblemDetail`

### Requirement: Filtros por estado y tipo
El listado SHALL admitir los filtros opcionales `estado` (`PENDIENTE | ENVIADA | FALLIDA`) y `tipo` (cualquiera de los tipos de notificación), combinables. Un valor de filtro desconocido MUST responder `400`.

#### Scenario: Filtrar fallidas de recordatorio
- **GIVEN** notificaciones de varios tipos y estados
- **WHEN** se pide `GET /api/v1/notificaciones?estado=FALLIDA&tipo=RECORDATORIO`
- **THEN** solo se devuelven recordatorios en estado `FALLIDA`

#### Scenario: Filtro inválido
- **WHEN** se pide `GET /api/v1/notificaciones?estado=PERDIDA`
- **THEN** la respuesta es `400` en formato `ProblemDetail`

### Requirement: Reintento manual acotado
`POST /api/v1/notificaciones/{id}/reintentar` SHALL volver a dejar `PENDIENTE`, con intentos reiniciados y programada para el instante actual, una notificación `FALLIDA`. Cada notificación MUST admitir como máximo 3 reintentos manuales. Una notificación inexistente MUST responder `404`, una que no está `FALLIDA` MUST responder `409`, y superar el máximo o tener un enlace ya vencido MUST responder `422`.

#### Scenario: Reintento de una fallida
- **GIVEN** una notificación `FALLIDA` sin reintentos manuales previos
- **WHEN** el odontólogo pide reintentarla
- **THEN** recibe `200` con la notificación `PENDIENTE`, 0 intentos y 1 reintento manual

#### Scenario: Reintento de una enviada
- **WHEN** se pide reintentar una notificación `ENVIADA`
- **THEN** la respuesta es `409` y la notificación no cambia

#### Scenario: Máximo de reintentos manuales
- **GIVEN** una notificación `FALLIDA` que ya tuvo 3 reintentos manuales
- **WHEN** se pide reintentarla
- **THEN** la respuesta es `422` y la notificación no cambia

#### Scenario: Enlace vencido
- **GIVEN** una notificación `FALLIDA` cuyo enlace ya venció
- **WHEN** se pide reintentarla
- **THEN** la respuesta es `422` y la notificación no cambia

#### Scenario: Notificación inexistente
- **WHEN** se pide reintentar la notificación 999999
- **THEN** la respuesta es `404` en formato `ProblemDetail`

### Requirement: Registro solo con sesión del odontólogo
Las operaciones del registro de envíos SHALL requerir la sesión autenticada del odontólogo, y el reintento MUST exigir además el token CSRF.

#### Scenario: Consulta sin sesión
- **WHEN** se pide `GET /api/v1/notificaciones` sin sesión
- **THEN** la respuesta es `401` en formato `ProblemDetail`

#### Scenario: Reintento sin CSRF
- **WHEN** el odontólogo autenticado pide reintentar sin el encabezado CSRF
- **THEN** la respuesta es `403` y la notificación no cambia
