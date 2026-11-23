# Spec Delta

## MODIFIED Requirements

### Requirement: Rutas públicas y privadas
La aplicación SHALL clasificar cada ruta como pública o privada; una ruta privada solo SHALL mostrarse con sesión activa y, sin sesión, MUST redirigir a `/login?next=<ruta>`. Una ruta SHALL poder declarar segmentos variables (por ejemplo `/acciones/{token}`), que se entregan a la vista sin pasar por la verificación de sesión cuando la ruta es pública.

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

#### Scenario: Ruta pública con segmento variable
- **GIVEN** no hay sesión activa
- **WHEN** el usuario navega a `/acciones/abc123`
- **THEN** se renderiza la vista de acciones por token con el parámetro `token` igual a `abc123`, sin redirigir a `/login` y sin el layout autenticado

#### Scenario: Segmento variable vacío
- **WHEN** el usuario navega a `/acciones/`
- **THEN** se muestra la vista de "página no encontrada"
