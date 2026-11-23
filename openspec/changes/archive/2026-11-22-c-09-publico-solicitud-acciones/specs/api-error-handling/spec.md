# Spec Delta

## ADDED Requirements

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
