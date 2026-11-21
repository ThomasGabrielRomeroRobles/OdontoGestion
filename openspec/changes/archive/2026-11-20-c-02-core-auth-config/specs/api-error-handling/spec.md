# Spec Delta

## ADDED Requirements

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
