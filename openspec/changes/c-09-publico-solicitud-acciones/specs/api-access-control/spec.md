# Spec Delta

## ADDED Requirements

### Requirement: Sin envío de Referer
Toda respuesta de la aplicación (API, `index.html` y rutas de la SPA, incluidas `/acciones/{token}` y `/avisos/{token}`) SHALL incluir la cabecera `Referrer-Policy: no-referrer`, para que la URL con un token nunca se filtre a terceros por la cabecera `Referer`.

#### Scenario: Cabecera en la API por token
- **WHEN** se envía `GET /api/v1/acciones/{token}` sin sesión
- **THEN** la respuesta incluye `Referrer-Policy: no-referrer`, sea `200` o `410`

#### Scenario: Cabecera en la página del enlace
- **GIVEN** el artefacto incluye el frontend
- **WHEN** se solicita `GET /` sin sesión
- **THEN** la respuesta incluye `Referrer-Policy: no-referrer`

#### Scenario: Cabecera en rutas privadas
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `GET /api/v1/consultorio`
- **THEN** la respuesta incluye `Referrer-Policy: no-referrer`
