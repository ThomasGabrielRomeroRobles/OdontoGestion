# api-error-handling Specification

## Purpose

Establece un formato de error único para toda la API REST (`application/problem+json`, RFC 9457) con códigos HTTP coherentes, de modo que el frontend y los demás changes manejen los errores de forma uniforme y sin filtrar detalles internos.

## Requirements

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

### Requirement: Errores de seguridad en formato ProblemDetail
Las respuestas emitidas por la capa de seguridad (sin sesión o credenciales inválidas `401`, token CSRF ausente o inválido `403`, intentos de login excedidos `429`) SHALL usar `application/problem+json` con `type`, `title`, `status`, `detail` e `instance`, y MUST NOT revelar si el email existe ni detalles del token.

#### Scenario: 401 sin sesión
- **WHEN** se envía `GET /api/v1/perfil` sin sesión
- **THEN** la respuesta es `401` con `Content-Type: application/problem+json`, `status` 401 e `instance` `/api/v1/perfil`

#### Scenario: 403 por CSRF
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `PUT /api/v1/perfil` sin token CSRF
- **THEN** la respuesta es `403` con `Content-Type: application/problem+json` y `status` 403, sin el valor esperado del token en el cuerpo

#### Scenario: 429 por intentos excedidos
- **GIVEN** un email superó el límite de intentos de login
- **WHEN** se intenta iniciar sesión con ese email
- **THEN** la respuesta es `429` con `Content-Type: application/problem+json`, `status` 429 y cabecera `Retry-After` con un número entero de segundos

#### Scenario: Detalle genérico en credenciales inválidas
- **WHEN** un login falla por email inexistente o por contraseña incorrecta
- **THEN** el `detail` del `ProblemDetail` es el mismo texto genérico en ambos casos y no incluye el email enviado

### Requirement: Token de acceso inválido como recurso desaparecido
Cuando un caso de uso informa que un token de acceso del paciente es inválido (inexistente, mal formado, vencido, usado, revocado o de otro propósito), la respuesta SHALL ser `410 Gone` en formato `ProblemDetail` con el mismo `type`, `title` y `detail` genéricos para todas las causas, sin datos del turno, aviso o entrada, y MUST NOT incluir ni registrar en logs el valor del token.

#### Scenario: Token inválido
- **WHEN** un caso de uso informa un token de acceso inválido
- **THEN** la respuesta es `410` en formato `ProblemDetail` con un `detail` genérico que no menciona la causa

#### Scenario: Cuerpos idénticos para causas distintas
- **WHEN** se compara la respuesta de un token vencido con la de un token inexistente
- **THEN** ambas tienen el mismo estado, `type`, `title` y `detail`

#### Scenario: Token ausente de la respuesta y de los logs
- **WHEN** una solicitud con un token inválido en la ruta produce el `410`
- **THEN** ni el cuerpo de la respuesta ni los logs de la aplicación contienen el valor del token

### Requirement: Errores de rutas por token sin el token
Cualquier respuesta de error (`400`, `403`, `409`, `410`, `422`, `429`, `500`) a una solicitud bajo `/api/v1/acciones/**` o `/api/v1/avisos/**` SHALL usar en `instance` un valor constante que no contenga la ruta de la solicitud, y MUST NOT incluir el valor del token en ningún campo del cuerpo.

#### Scenario: Regla de negocio en una acción por token
- **WHEN** un `POST /api/v1/acciones/{token}` produce un `422`
- **THEN** el cuerpo `ProblemDetail` no contiene el valor del token y su `instance` no es la ruta de la solicitud

#### Scenario: CSRF rechazado en una acción por token
- **WHEN** un `POST /api/v1/acciones/{token}` sin cabecera CSRF produce un `403`
- **THEN** el cuerpo no contiene el valor del token

#### Scenario: Otras rutas conservan su instance
- **WHEN** un `POST /api/v1/turnos` produce un `409`
- **THEN** su `instance` sigue siendo la ruta de la solicitud
