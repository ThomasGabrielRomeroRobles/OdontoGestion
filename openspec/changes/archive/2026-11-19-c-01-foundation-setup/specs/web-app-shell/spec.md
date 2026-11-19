# Spec Delta

## Purpose

Define la estructura navegable de la SPA del odontólogo: rutas públicas y privadas con guardia de sesión, estado de sesión compartido, layout base, pantalla de inicio de sesión y cierre de sesión según el contrato del Flujo 1.

## ADDED Requirements

### Requirement: Rutas públicas y privadas
La aplicación SHALL clasificar cada ruta como pública o privada; una ruta privada solo SHALL mostrarse con sesión activa y, sin sesión, MUST redirigir a `/login?next=<ruta>`.

#### Scenario: Ruta privada sin sesión
- **GIVEN** no hay sesión activa
- **WHEN** el usuario navega a `/agenda`
- **THEN** la aplicación muestra `/login` con `next=/agenda` y no renderiza la vista privada

#### Scenario: Ruta privada con sesión
- **GIVEN** hay una sesión activa
- **WHEN** el usuario navega a `/agenda`
- **THEN** se renderiza la vista de `/agenda` dentro del layout autenticado

#### Scenario: Ruta pública sin sesión
- **GIVEN** no hay sesión activa
- **WHEN** el usuario navega a una ruta pública (por ejemplo `/publico`)
- **THEN** la vista se renderiza sin redirigir

#### Scenario: Ruta desconocida
- **WHEN** el usuario navega a `/no-existe`
- **THEN** se muestra una vista de "página no encontrada"

### Requirement: Navegación sin recarga
La aplicación SHALL cambiar de vista usando el historial del navegador sin recargar la página, y MUST responder a los botones atrás/adelante.

#### Scenario: Navegación interna
- **WHEN** el usuario activa un enlace interno de la aplicación
- **THEN** la URL cambia, se renderiza la nueva vista y no hay recarga completa de la página

#### Scenario: Botón atrás
- **GIVEN** el usuario navegó de `/agenda` a `/pacientes`
- **WHEN** usa el botón atrás del navegador
- **THEN** se renderiza nuevamente la vista de `/agenda`

#### Scenario: Carga directa de una URL
- **GIVEN** hay una sesión activa
- **WHEN** el usuario abre directamente la URL `/pacientes`
- **THEN** se renderiza la vista de `/pacientes`

### Requirement: Estado de sesión
La aplicación SHALL determinar al iniciar si hay sesión consultando `GET /api/v1/auth/me` y mantener ese estado compartido (autenticado con email del usuario, o anónimo) notificando sus cambios a las vistas.

#### Scenario: Sesión existente al iniciar
- **GIVEN** `GET /api/v1/auth/me` responde `200` con `{"email":"odontologo@consultorio.example"}`
- **WHEN** la aplicación inicia
- **THEN** el estado de sesión es autenticado con ese email

#### Scenario: Sin sesión al iniciar
- **GIVEN** `GET /api/v1/auth/me` responde `401`
- **WHEN** la aplicación inicia
- **THEN** el estado de sesión es anónimo

#### Scenario: Suscriptores notificados
- **GIVEN** una vista suscripta al estado de sesión
- **WHEN** el estado cambia de anónimo a autenticado
- **THEN** la vista recibe el nuevo estado una vez

### Requirement: Inicio de sesión
La pantalla `/login` SHALL pedir email y contraseña, enviarlos con `POST /api/v1/auth/login` y, ante éxito, establecer la sesión y navegar a la ruta indicada en `next` (o a `/agenda` por defecto). Ante `401` MUST mostrar un mensaje genérico de credenciales inválidas.

#### Scenario: Render del formulario
- **WHEN** se muestra `/login`
- **THEN** la vista contiene un campo de email, un campo de contraseña de tipo `password` y un botón para ingresar

#### Scenario: Login exitoso con destino
- **GIVEN** la URL es `/login?next=%2Fpacientes`
- **WHEN** el usuario envía credenciales y la API responde `200`
- **THEN** el estado de sesión pasa a autenticado y la aplicación navega a `/pacientes`

#### Scenario: Login exitoso sin destino
- **GIVEN** la URL es `/login`
- **WHEN** el usuario envía credenciales y la API responde `200`
- **THEN** la aplicación navega a `/agenda`

#### Scenario: Credenciales inválidas
- **WHEN** el usuario envía credenciales y la API responde `401`
- **THEN** se muestra un mensaje genérico de credenciales inválidas, el campo de contraseña queda vacío y la aplicación permanece en `/login`

#### Scenario: Destino externo rechazado
- **GIVEN** la URL es `/login?next=https%3A%2F%2Fevil.example`
- **WHEN** el login es exitoso
- **THEN** la aplicación navega a `/agenda` y no a la URL externa

#### Scenario: Campos vacíos
- **WHEN** el usuario intenta ingresar con el email vacío
- **THEN** no se envía la solicitud y se indica que el email es obligatorio

### Requirement: Cierre de sesión
El layout autenticado SHALL ofrecer una acción de cierre de sesión que envíe `POST /api/v1/auth/logout`, deje el estado anónimo y navegue a `/login`, incluso si la API responde error.

#### Scenario: Logout exitoso
- **GIVEN** hay una sesión activa
- **WHEN** el usuario activa "Cerrar sesión" y la API responde `204`
- **THEN** el estado de sesión es anónimo y se muestra `/login`

#### Scenario: Logout con error de la API
- **GIVEN** hay una sesión activa
- **WHEN** el usuario activa "Cerrar sesión" y la API responde `500`
- **THEN** el estado de sesión igualmente queda anónimo y se muestra `/login`

#### Scenario: Layout autenticado muestra al usuario
- **GIVEN** hay una sesión activa con email `odontologo@consultorio.example`
- **WHEN** se renderiza una ruta privada
- **THEN** el layout muestra ese email y la acción "Cerrar sesión"
