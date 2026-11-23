# Spec Delta

## Purpose

Páginas del frontend, sin sesión, para los enlaces de email `/acciones/{token}`: muestran el resumen de la acción y la ejecutan solo cuando el paciente pulsa el botón, con mensajes claros para cada resultado.

## ADDED Requirements

### Requirement: Resumen antes de actuar
Al abrir `/acciones/{token}` la vista SHALL consultar el resumen del token y mostrar la acción, el consultorio, la prestación, la fecha y hora en `America/Argentina/Buenos_Aires` y el estado del turno, con un único botón para la acción. Abrir la página MUST NOT ejecutar la acción.

#### Scenario: Resumen de cancelación
- **GIVEN** el resumen devuelve `accion` `CANCELAR_TURNO` y `disponible` verdadero
- **WHEN** el paciente abre el enlace
- **THEN** la vista muestra los datos del turno y un botón "Cancelar turno", y no envió ningún `POST`

#### Scenario: Acción no disponible
- **GIVEN** el resumen devuelve `disponible` falso con `motivo` `FUERA_DE_LIMITE`
- **WHEN** el paciente abre el enlace
- **THEN** la vista explica que ya no puede cancelar por este medio y que debe contactar al consultorio, sin botón de acción

### Requirement: Ejecución con confirmación explícita
Al pulsar el botón la vista SHALL enviar el `POST` con la acción del resumen, deshabilitar el botón mientras espera y mostrar el resultado: solicitud confirmada (turno reservado), asistencia confirmada o turno cancelado.

#### Scenario: Confirmar la solicitud
- **GIVEN** el resumen devuelve `accion` `CONFIRMAR_SOLICITUD`
- **WHEN** el paciente pulsa "Confirmar solicitud" y la API responde `200`
- **THEN** la vista muestra que el turno quedó reservado y el botón no vuelve a estar disponible

#### Scenario: Doble clic
- **WHEN** el paciente pulsa dos veces seguidas el botón
- **THEN** se envía un solo `POST`

### Requirement: Páginas informativas de enlace no disponible
Ante un `410` (en el resumen o en la ejecución) la vista SHALL mostrar un mensaje genérico: el enlace venció o ya fue usado, si era una solicitud el horario se liberó y puede pedir otro turno desde la página pública (con enlace a `/publico`). Ante `422` o `409` MUST mostrar que el turno ya no admite la acción y volver a consultar el resumen.

#### Scenario: Enlace vencido
- **WHEN** el resumen responde `410`
- **THEN** la vista muestra el mensaje genérico de enlace no disponible con un enlace a `/publico` y sin datos del turno

#### Scenario: Estado cambiado al ejecutar
- **WHEN** el `POST` responde `422`
- **THEN** la vista informa que el turno ya no admite la acción y muestra el resumen actualizado

### Requirement: Páginas por token sin sesión
Las vistas `/acciones/{token}` SHALL funcionar sin sesión y sin el layout del odontólogo, y el documento MUST declarar `referrer` `no-referrer`.

#### Scenario: Acceso sin sesión
- **GIVEN** no hay sesión activa
- **WHEN** el paciente abre `/acciones/{token}`
- **THEN** la vista se renderiza sin redirigir a `/login`

#### Scenario: Política de referer en el documento
- **WHEN** se carga `index.html`
- **THEN** contiene `<meta name="referrer" content="no-referrer">`
