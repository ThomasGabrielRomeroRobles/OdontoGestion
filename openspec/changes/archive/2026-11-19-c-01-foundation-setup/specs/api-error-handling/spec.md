# Spec Delta

## Purpose

Establece un formato de error único para toda la API REST (`application/problem+json`, RFC 9457) con códigos HTTP coherentes, de modo que el frontend y los demás changes manejen los errores de forma uniforme y sin filtrar detalles internos.

## ADDED Requirements

### Requirement: Formato uniforme de errores
Toda respuesta de error de la API bajo `/api/v1` SHALL usar el tipo de contenido `application/problem+json` con los campos `type`, `title`, `status` y `detail`, y el campo `instance` con el path solicitado.

#### Scenario: Error con cuerpo ProblemDetail
- **WHEN** una solicitud a la API termina en error
- **THEN** la respuesta tiene `Content-Type: application/problem+json` y el campo `status` coincide con el código HTTP

#### Scenario: Instance indica el recurso solicitado
- **WHEN** una solicitud a `/api/v1/recurso-de-prueba/99` termina en error
- **THEN** el campo `instance` del cuerpo es `/api/v1/recurso-de-prueba/99`

#### Scenario: Título legible
- **WHEN** una solicitud a la API termina en error
- **THEN** el campo `title` contiene una descripción breve y no vacía del tipo de error

### Requirement: Errores de validación de entrada
Una solicitud con cuerpo o parámetros inválidos SHALL responder `400` con un `ProblemDetail` que incluya la lista de errores por campo (`errors`, cada uno con `field` y `message`).

#### Scenario: Campo obligatorio ausente
- **GIVEN** un endpoint que exige el campo `nombre`
- **WHEN** se envía un cuerpo sin `nombre`
- **THEN** la respuesta es `400` y `errors` contiene una entrada con `field` igual a `nombre`

#### Scenario: Varios campos inválidos
- **GIVEN** un endpoint que exige `nombre` no vacío y `email` con formato válido
- **WHEN** se envía `nombre` vacío y `email` igual a `no-es-email`
- **THEN** la respuesta es `400` y `errors` contiene una entrada para `nombre` y otra para `email`

#### Scenario: Cuerpo JSON mal formado
- **WHEN** se envía un cuerpo que no es JSON válido a un endpoint que espera JSON
- **THEN** la respuesta es `400` en formato `ProblemDetail` sin incluir el texto de la excepción del parser

### Requirement: Recurso inexistente y conflictos de dominio
Las excepciones de dominio SHALL traducirse a códigos coherentes: recurso inexistente → `404`, conflicto de estado o de agenda → `409`, regla de negocio incumplida → `422`.

#### Scenario: Recurso no encontrado
- **WHEN** un caso de uso informa que el recurso solicitado no existe
- **THEN** la respuesta es `404` en formato `ProblemDetail`

#### Scenario: Conflicto
- **WHEN** un caso de uso informa un conflicto (por ejemplo, un hueco ya ocupado)
- **THEN** la respuesta es `409` en formato `ProblemDetail`

#### Scenario: Regla de negocio incumplida
- **WHEN** un caso de uso informa que una regla de negocio no se cumple
- **THEN** la respuesta es `422` en formato `ProblemDetail` con el mensaje de la regla en `detail`

### Requirement: Errores inesperados sin filtrar detalles internos
Un error no previsto SHALL responder `500` con un `ProblemDetail` genérico y MUST NOT incluir trazas, mensajes de excepción, SQL ni datos de la solicitud en la respuesta.

#### Scenario: Excepción no controlada
- **WHEN** un endpoint lanza una excepción no prevista con el mensaje `secreto-interno`
- **THEN** la respuesta es `500` en formato `ProblemDetail` y el cuerpo no contiene `secreto-interno` ni una traza de pila

#### Scenario: Detalle genérico
- **WHEN** ocurre un error no previsto
- **THEN** el campo `detail` contiene un mensaje genérico fijo

#### Scenario: Error registrado del lado del servidor
- **WHEN** ocurre un error no previsto
- **THEN** el servidor registra el error en su log sin incluir el cuerpo de la solicitud
