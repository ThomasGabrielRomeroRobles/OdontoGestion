# web-http-client Specification

## Purpose

Define el contrato del cliente HTTP del frontend con la API: envío de credenciales de sesión y del token CSRF en las escrituras, reacción ante sesión ausente o vencida y entrega de errores tipados a partir de respuestas `ProblemDetail`.

## Requirements

### Requirement: Token CSRF en solicitudes de escritura
El cliente SHALL leer la cookie `XSRF-TOKEN` y enviar su valor en la cabecera `X-XSRF-TOKEN` en toda solicitud `POST`, `PUT`, `PATCH` y `DELETE`, y MUST NOT enviarla en `GET`.

#### Scenario: POST con cookie presente
- **GIVEN** el navegador tiene la cookie `XSRF-TOKEN=abc123`
- **WHEN** el cliente envía un `POST` a `/api/v1/auth/login`
- **THEN** la solicitud incluye la cabecera `X-XSRF-TOKEN: abc123`

#### Scenario: GET sin cabecera CSRF
- **GIVEN** el navegador tiene la cookie `XSRF-TOKEN=abc123`
- **WHEN** el cliente envía un `GET` a `/api/v1/auth/me`
- **THEN** la solicitud no incluye la cabecera `X-XSRF-TOKEN`

#### Scenario: Escritura sin cookie disponible
- **GIVEN** el navegador no tiene la cookie `XSRF-TOKEN`
- **WHEN** el cliente envía un `DELETE`
- **THEN** la solicitud se envía sin la cabecera `X-XSRF-TOKEN`

#### Scenario: Valor de cookie codificado
- **GIVEN** la cookie `XSRF-TOKEN` contiene caracteres codificados en URL (`a%2Bb`)
- **WHEN** el cliente envía un `PUT`
- **THEN** la cabecera `X-XSRF-TOKEN` lleva el valor decodificado `a+b`

### Requirement: Credenciales de sesión del mismo origen
El cliente SHALL dirigir todas las solicitudes a rutas relativas bajo `/api/v1` del mismo origen e incluir las cookies de sesión, enviando y aceptando JSON.

#### Scenario: Ruta relativa al origen
- **WHEN** el cliente solicita el recurso `auth/me`
- **THEN** la URL de la solicitud es `/api/v1/auth/me`

#### Scenario: Cookies incluidas
- **WHEN** el cliente envía cualquier solicitud
- **THEN** la solicitud se configura para incluir credenciales del mismo origen

#### Scenario: Cuerpo JSON
- **WHEN** el cliente envía un `POST` con un objeto como cuerpo
- **THEN** el cuerpo se serializa como JSON y la cabecera `Content-Type` es `application/json`

### Requirement: Sesión ausente o vencida
Ante una respuesta `401` de una solicitud que no sea la de inicio de sesión, el cliente SHALL limpiar el estado de sesión y redirigir a `/login`, conservando la ruta original para volver tras autenticarse.

#### Scenario: 401 en ruta privada
- **GIVEN** el usuario está en `/agenda`
- **WHEN** una solicitud a la API responde `401`
- **THEN** el estado de sesión queda vacío y la aplicación navega a `/login?next=%2Fagenda`

#### Scenario: 401 en el propio login
- **WHEN** la solicitud `POST /api/v1/auth/login` responde `401`
- **THEN** el cliente no redirige y entrega un error de credenciales inválidas a quien llamó

#### Scenario: Respuesta exitosa sin redirección
- **WHEN** una solicitud responde `200`
- **THEN** el cliente devuelve el cuerpo JSON decodificado y no navega

### Requirement: Errores tipados desde ProblemDetail
Ante una respuesta de error, el cliente SHALL rechazar con un error tipado que expone `status`, `title`, `detail` y los errores por campo cuando el cuerpo es `application/problem+json`, y un error genérico con el `status` cuando no lo es.

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
