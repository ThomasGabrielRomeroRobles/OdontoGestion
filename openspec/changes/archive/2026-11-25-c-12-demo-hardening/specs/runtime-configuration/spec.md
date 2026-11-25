# Spec Delta

## ADDED Requirements

### Requirement: Rutas del frontend servidas desde el mismo origen
Cuando el artefacto incluye el frontend, la aplicación SHALL responder `200` con el contenido de `static/index.html` a toda solicitud `GET` cuya ruta no esté bajo `/api` y no corresponda a un archivo (su último segmento no tiene extensión), para que el router del frontend resuelva la pantalla. Las rutas bajo `/api` y los archivos estáticos inexistentes MUST conservar su respuesta `404`.

#### Scenario: Entrada directa a una ruta privada del frontend
- **GIVEN** la aplicación corre con el perfil `demo` y el frontend incluido
- **WHEN** se pide `GET /agenda` sin sesión
- **THEN** la respuesta es `200` con el contenido de `index.html`, y es el frontend el que redirige al inicio de sesión

#### Scenario: Enlace de un email
- **GIVEN** la aplicación corre con el perfil `demo` y el frontend incluido
- **WHEN** se pide `GET /acciones/tok-ficticio` o `GET /avisos/tok-ficticio`
- **THEN** la respuesta es `200` con el contenido de `index.html` y el token no se consume ni se valida en esa solicitud

#### Scenario: Ruta de la API inexistente
- **GIVEN** la aplicación corre con el perfil `demo` y el frontend incluido
- **WHEN** se pide `GET /api/v1/no-existe` con sesión
- **THEN** la respuesta es `404` en formato `ProblemDetail`, nunca `index.html`

#### Scenario: Archivo estático inexistente
- **GIVEN** la aplicación corre con el perfil `demo` y el frontend incluido
- **WHEN** se pide `GET /assets/no-existe.js`
- **THEN** la respuesta es `404` y no es `index.html`

#### Scenario: Recursos del build servidos tal cual
- **GIVEN** la aplicación corre con el perfil `demo` y el frontend incluido
- **WHEN** se pide un archivo real de `static/assets/`
- **THEN** la respuesta es `200` con ese archivo

#### Scenario: Solicitud que no es GET
- **WHEN** se envía `POST /agenda`
- **THEN** la respuesta no es `index.html`
