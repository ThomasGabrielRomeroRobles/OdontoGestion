# Spec Delta

## Purpose

Pantalla pública del frontend, sin sesión, para que un paciente elija prestación y horario libre, envíe sus datos y entienda el resultado de su solicitud sin que la interfaz revele si sus datos ya existían.

## ADDED Requirements

### Requirement: Elección de prestación y horario libre
La vista `/publico` SHALL cargar las prestaciones públicas, permitir elegir una y un día (o semana) y mostrar solo los huecos libres que devuelve la disponibilidad pública, con fecha y hora en `America/Argentina/Buenos_Aires`. Sin huecos MUST mostrar un mensaje de "sin horarios disponibles".

#### Scenario: Huecos mostrados en hora local
- **GIVEN** la disponibilidad pública devuelve un hueco con `inicio` `2026-11-23T13:00:00Z`
- **WHEN** el paciente elige la prestación y ese día
- **THEN** la vista muestra el horario 10:00 del 23/11/2026 como seleccionable

#### Scenario: Sin horarios
- **GIVEN** la disponibilidad pública devuelve una lista vacía
- **WHEN** el paciente elige la prestación y el día
- **THEN** la vista muestra "sin horarios disponibles" y no muestra el formulario de datos

### Requirement: Formulario con validación y campo señuelo
Elegido un horario, la vista SHALL pedir nombre, apellido, DNI, email y teléfono, validarlos en el cliente con los mismos formatos que la API antes de enviar y marcar cada campo inválido. MUST incluir un campo señuelo invisible para las personas, fuera del orden de tabulación, oculto a tecnologías de asistencia y sin autocompletado, que se envía tal como esté.

#### Scenario: DNI inválido en el cliente
- **WHEN** el paciente ingresa el DNI `12AB` y envía
- **THEN** la vista marca el campo DNI y no envía la solicitud

#### Scenario: Señuelo no visible
- **WHEN** se renderiza el formulario
- **THEN** el campo señuelo existe, no es visible, tiene `tabindex="-1"`, `aria-hidden="true"` y `autocomplete="off"`, y se envía vacío si nadie lo completa

### Requirement: Resultado genérico de la solicitud
Ante un `202` la vista SHALL mostrar siempre el mismo mensaje ("si los datos son correctos, recibirás un email para confirmar el turno") y limpiar el formulario, sin indicar si el paciente ya existía.

#### Scenario: Solicitud aceptada
- **WHEN** la API responde `202`
- **THEN** la vista muestra el mensaje genérico de confirmación por email y no muestra datos del paciente

### Requirement: Manejo de errores de la solicitud
La vista SHALL traducir cada error a un mensaje accionable: `409` → "ese horario ya no está disponible" con las alternativas como horarios seleccionables; `422` → error en el campo indicado (p. ej. horario fuera de agenda); `400` → errores por campo; `429` → "demasiadas solicitudes, probá de nuevo en X minutos" usando `Retry-After`; red u otro → mensaje genérico con reintento.

#### Scenario: Conflicto con alternativas
- **WHEN** la API responde `409` con dos `alternativas`
- **THEN** la vista informa que el horario ya no está disponible, ofrece las dos alternativas en hora local y conserva los datos ingresados

#### Scenario: Límite excedido
- **WHEN** la API responde `429` con `Retry-After: 1800`
- **THEN** la vista muestra que se superó el límite y que puede reintentar en 30 minutos, y deshabilita el envío

#### Scenario: Horario rechazado por la agenda
- **WHEN** la API responde `422` con un error en `inicio`
- **THEN** la vista informa que el horario no es válido y recarga los huecos libres

### Requirement: Página pública sin sesión
La vista `/publico` SHALL funcionar sin sesión, sin redirigir a `/login` ni mostrar el layout del odontólogo, y MUST NOT llamar a endpoints privados.

#### Scenario: Acceso sin sesión
- **GIVEN** no hay sesión activa
- **WHEN** el paciente navega a `/publico`
- **THEN** la vista se renderiza y todas sus llamadas son a `/api/v1/publico/**`
