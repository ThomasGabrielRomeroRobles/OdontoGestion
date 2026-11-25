# Spec Delta

## MODIFIED Requirements

### Requirement: Errores tipados desde ProblemDetail
Ante una respuesta de error, el cliente SHALL rechazar con un error tipado que expone `status`, `title`, `detail` y los errores por campo cuando el cuerpo es `application/problem+json`, y un error genérico con el `status` y sin extensiones si no lo es. El error MUST exponer además el código (último segmento de `type`), los miembros no estándar del cuerpo como extensiones `unknown` y, en un `429`, los segundos de `Retry-After`.

#### Scenario: Error 422 con ProblemDetail
- **WHEN** la API responde `422` con `{"title":"Regla incumplida","detail":"Duración inválida","status":422}`
- **THEN** el error entregado tiene `status` 422, `title` "Regla incumplida" y `detail` "Duración inválida"

#### Scenario: Error 400 con errores por campo
- **WHEN** la API responde `400` con `errors: [{"field":"email","message":"formato inválido"}]`
- **THEN** el error entregado expone el error del campo `email`

#### Scenario: Error sin cuerpo ProblemDetail
- **WHEN** la API responde `502` con un cuerpo HTML
- **THEN** el error entregado tiene `status` 502 y un `title` genérico

#### Scenario: Falla de red
- **WHEN** la solicitud no puede completarse por un problema de red
- **THEN** el cliente rechaza con un error de red distinguible de los errores HTTP

#### Scenario: Código y propiedades adicionales
- **WHEN** la API responde `409` con `type` `https://odontogestion.example/problemas/hueco-no-disponible` y `alternativas: [{"inicio":"2026-11-23T13:30:00Z","fin":"2026-11-23T14:00:00Z"}]`
- **THEN** el error entregado tiene código `hueco-no-disponible` y expone `alternativas` entre sus propiedades adicionales sin validar su forma

#### Scenario: Retry-After en un 429
- **WHEN** la API responde `429` con la cabecera `Retry-After: 120`
- **THEN** el error entregado expone 120 segundos de espera

#### Scenario: Retry-After ausente o inválido
- **WHEN** la API responde `429` sin cabecera `Retry-After` o con un valor no numérico
- **THEN** el error entregado no informa segundos de espera

#### Scenario: Miembros de extensión conservados
- **WHEN** la API responde `409` con `{"title":"Conflicto de agenda","status":409,"alternativas":[{"inicio":"2026-11-24T13:00:00Z","fin":"2026-11-24T13:45:00Z"}]}`
- **THEN** el error entregado expone en sus extensiones el miembro `alternativas` con ese contenido, y no repite `title` ni `status` entre las extensiones

#### Scenario: Sin miembros de extensión
- **WHEN** la API responde `422` con un `ProblemDetail` que solo tiene `title`, `status` y `detail`
- **THEN** el error entregado tiene extensiones vacías
