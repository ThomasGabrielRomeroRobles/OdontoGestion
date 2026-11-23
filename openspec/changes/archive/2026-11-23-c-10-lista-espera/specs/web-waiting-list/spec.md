# Spec Delta

## Purpose

Vistas del frontend de la lista de espera: el formulario público para anotarse, la página por token para aceptar un aviso de hueco y la pantalla del odontólogo para consultar y retirar entradas.

## ADDED Requirements

### Requirement: Formulario público de inscripción
La ruta pública `/publico/lista-espera` SHALL mostrar, sin sesión, la prestación (de las prestaciones públicas), fecha desde, fecha hasta y los datos de contacto, con el campo señuelo oculto. MUST validar en el cliente obligatorios, formatos y `fechaDesde` ≤ `fechaHasta` antes de enviar, y tras un `202` mostrar un mensaje genérico de "revisá tu email".

#### Scenario: Envío válido
- **GIVEN** el formulario completo con datos válidos
- **WHEN** el paciente pulsa "Anotarme"
- **THEN** se envía un solo `POST /api/v1/publico/lista-espera` y la vista muestra el mensaje genérico de confirmación por email

#### Scenario: Rango inválido
- **GIVEN** fecha hasta anterior a fecha desde
- **WHEN** el paciente pulsa "Anotarme"
- **THEN** la vista muestra el error junto a fecha hasta y no envía ninguna petición

#### Scenario: Demasiadas solicitudes
- **WHEN** la API responde `429`
- **THEN** la vista informa que hubo demasiados intentos y que pruebe más tarde

### Requirement: Página de aceptación de un aviso
La ruta pública `/avisos/:token` SHALL consultar el resumen del aviso y mostrar prestación, fecha y hora en `America/Argentina/Buenos_Aires` con un botón "Aceptar turno" si está disponible. Abrir la página MUST NOT aceptar el aviso. Al aceptar MUST deshabilitar el botón y mostrar: turno reservado (`200`), "el horario ya no está disponible" (`409` o `disponible` falso) o "el enlace venció o ya fue usado" (`410`).

#### Scenario: Aceptación exitosa
- **GIVEN** el resumen devuelve `disponible` verdadero
- **WHEN** el paciente pulsa "Aceptar turno" y la API responde `200`
- **THEN** la vista muestra que el turno quedó reservado con su fecha y hora

#### Scenario: Otro paciente ganó
- **WHEN** el paciente pulsa "Aceptar turno" y la API responde `409`
- **THEN** la vista muestra que el horario ya no está disponible, sin botón

#### Scenario: Enlace vencido
- **WHEN** el `GET` del resumen responde `410`
- **THEN** la vista muestra que el enlace venció o ya fue usado, sin botón

### Requirement: Pantalla de lista de espera del odontólogo
La ruta privada `/lista-espera` SHALL listar las entradas (paciente, prestación, rango de fechas, estado, alta) y ofrecer "Retirar" solo para entradas `PENDIENTE_CONFIRMACION` o `ACTIVA`; al retirar con `204` MUST actualizar el estado mostrado a `CANCELADA`.

#### Scenario: Listado
- **GIVEN** la API devuelve una entrada `ACTIVA` y una `CUBIERTA`
- **WHEN** el odontólogo abre `/lista-espera`
- **THEN** se ven ambas filas y solo la `ACTIVA` tiene el botón "Retirar"

#### Scenario: Retirar
- **WHEN** el odontólogo pulsa "Retirar" en la entrada `ACTIVA` y la API responde `204`
- **THEN** la fila pasa a mostrar `CANCELADA` sin botón
