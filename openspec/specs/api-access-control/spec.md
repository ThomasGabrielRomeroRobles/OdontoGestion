# api-access-control Specification

## Purpose

Define qué rutas de la API exigen la sesión del odontólogo y cuáles son públicas o por token, cómo se protegen contra CSRF las solicitudes que modifican estado y qué recursos del frontend se sirven sin sesión, con la autorización verificada siempre en el servidor.

## Requirements

### Requirement: API privada por defecto
Toda ruta bajo `/api/v1/**` SHALL exigir sesión autenticada del odontólogo, salvo `/api/v1/publico/**`, `/api/v1/acciones/**`, `/api/v1/avisos/**` y `POST /api/v1/auth/login`. Sin sesión, una ruta privada MUST responder `401` sin ejecutar el caso de uso.

#### Scenario: Ruta privada sin sesión
- **WHEN** se envía `GET /api/v1/consultorio` sin cookie de sesión
- **THEN** la respuesta es `401` en formato `ProblemDetail`

#### Scenario: Ruta privada con sesión
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `GET /api/v1/consultorio`
- **THEN** la respuesta es `200`

#### Scenario: Ruta privada inexistente sin sesión
- **WHEN** se envía `GET /api/v1/no-existe` sin cookie de sesión
- **THEN** la respuesta es `401` y no `404`, sin revelar qué rutas existen

#### Scenario: Prefijos públicos sin sesión
- **WHEN** se envía sin sesión un `GET` a una ruta bajo `/api/v1/publico/`, `/api/v1/acciones/` o `/api/v1/avisos/`
- **THEN** la respuesta no es `401` ni `403`

#### Scenario: Login accesible sin sesión
- **WHEN** se envía `POST /api/v1/auth/login` sin sesión y con token CSRF válido
- **THEN** la respuesta no es `403` por autorización (es `200`, `400`, `401` o `429` según las credenciales)

### Requirement: Protección CSRF en escrituras
Toda solicitud `POST`, `PUT`, `PATCH` o `DELETE` a `/api/v1/**`, incluidas las rutas públicas y el login, SHALL exigir que la cabecera `X-XSRF-TOKEN` coincida con el token de la cookie `XSRF-TOKEN`; si falta o no coincide MUST responder `403` sin modificar estado.

#### Scenario: Escritura sin token
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `PUT /api/v1/perfil` sin cabecera `X-XSRF-TOKEN`
- **THEN** la respuesta es `403` en formato `ProblemDetail` y el perfil no cambia

#### Scenario: Token que no coincide
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `PUT /api/v1/perfil` con una cabecera `X-XSRF-TOKEN` distinta del valor de la cookie
- **THEN** la respuesta es `403`

#### Scenario: Login sin token
- **WHEN** se envía `POST /api/v1/auth/login` con credenciales válidas y sin cabecera `X-XSRF-TOKEN`
- **THEN** la respuesta es `403` y no se crea sesión autenticada

#### Scenario: Lectura sin token
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `GET /api/v1/perfil` sin cabecera `X-XSRF-TOKEN`
- **THEN** la respuesta es `200`

### Requirement: Emisión del token CSRF al frontend
El sistema SHALL emitir la cookie `XSRF-TOKEN` legible por JavaScript (sin `HttpOnly`, `SameSite=Lax`, path `/`) en la respuesta a cualquier solicitud a la API que no la traiga, incluidas las respuestas `401`; MUST emitir un valor nuevo al iniciar sesión y eliminarla al cerrar sesión.

#### Scenario: Cookie obtenida antes del login
- **WHEN** se envía `GET /api/v1/auth/me` sin sesión ni cookie `XSRF-TOKEN`
- **THEN** la respuesta `401` incluye una cookie `XSRF-TOKEN` sin el atributo `HttpOnly`

#### Scenario: Token utilizable tal como se lee de la cookie
- **GIVEN** el frontend obtuvo la cookie `XSRF-TOKEN` con valor `T`
- **WHEN** envía `POST /api/v1/auth/login` con `X-XSRF-TOKEN: T` y credenciales válidas
- **THEN** la respuesta es `200`

#### Scenario: Token renovado al iniciar sesión
- **GIVEN** el frontend tenía la cookie `XSRF-TOKEN` con valor `T`
- **WHEN** inicia sesión con éxito
- **THEN** la respuesta emite una cookie `XSRF-TOKEN` con un valor distinto de `T`

#### Scenario: Token eliminado al cerrar sesión
- **GIVEN** el odontólogo inició sesión
- **WHEN** envía `POST /api/v1/auth/logout` con token CSRF válido
- **THEN** la respuesta expira la cookie `XSRF-TOKEN` (`Max-Age=0`) y la siguiente solicitud a la API recibe una cookie `XSRF-TOKEN` nueva

### Requirement: Sin CORS
La API SHALL atender solo solicitudes del mismo origen y MUST NOT emitir cabeceras `Access-Control-Allow-Origin` para orígenes externos.

#### Scenario: Preflight de otro origen
- **WHEN** se envía `OPTIONS /api/v1/auth/login` con `Origin: https://evil.example` y `Access-Control-Request-Method: POST`
- **THEN** la respuesta no contiene la cabecera `Access-Control-Allow-Origin`

#### Scenario: Solicitud simple de otro origen
- **WHEN** se envía `GET /api/v1/auth/me` con `Origin: https://evil.example`
- **THEN** la respuesta no contiene la cabecera `Access-Control-Allow-Origin`

### Requirement: Recursos del frontend sin sesión
Las rutas fuera de `/api/` (página `index.html`, recursos de `/assets/**` y rutas de la SPA) SHALL servirse sin exigir sesión; la protección de los datos MUST residir en la API.

#### Scenario: index.html sin sesión
- **GIVEN** el artefacto incluye el frontend
- **WHEN** se solicita `GET /` sin sesión
- **THEN** la respuesta no es `401` ni `403`

#### Scenario: Recursos estáticos sin sesión
- **GIVEN** el artefacto incluye el frontend
- **WHEN** se solicita un archivo bajo `/assets/` sin sesión
- **THEN** la respuesta no es `401` ni `403`

#### Scenario: Sin endpoint de registro
- **WHEN** se envía `POST /api/v1/auth/registro` (o cualquier ruta de alta de usuarios) sin sesión
- **THEN** la respuesta es `401` y no se crea ningún usuario

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
