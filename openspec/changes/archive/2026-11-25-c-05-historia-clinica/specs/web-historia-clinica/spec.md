# Spec Delta

## Purpose

Define la pantalla con la que el odontólogo consulta y edita desde la SPA la historia clínica de un paciente: anamnesis, odontograma FDI visual de 32 piezas y notas de evolución.

> Convención de los escenarios: la zona del consultorio es `America/Argentina/Buenos_Aires` (UTC−03:00). Todos los pacientes son ficticios.

## ADDED Requirements

### Requirement: Ruta privada de la historia clínica
La aplicación SHALL ofrecer la ruta privada `/pacientes/:id/historia`, que carga la ficha con `GET /api/v1/pacientes/{id}/historia` y muestra nombre y apellido del paciente. Sin sesión MUST redirigir a login.

#### Scenario: Carga de la ficha
- **GIVEN** hay una sesión activa y la API devuelve la historia del paciente 7 "Ana Pérez"
- **WHEN** el usuario navega a `/pacientes/7/historia`
- **THEN** se muestran el encabezado "Historia clínica — Pérez, Ana", la anamnesis, el odontograma y las evoluciones

#### Scenario: Sin sesión
- **GIVEN** no hay sesión activa
- **WHEN** el usuario navega a `/pacientes/7/historia`
- **THEN** la aplicación redirige a `/login?next=` con la ruta original codificada

#### Scenario: Paciente inexistente o id inválido
- **WHEN** el usuario navega a `/pacientes/abc/historia` o la API responde `404`
- **THEN** se muestra "Paciente no encontrado" sin pedir más datos

### Requirement: Edición de la anamnesis
La pantalla SHALL mostrar la anamnesis en un formulario editable y guardarla con `PUT /api/v1/pacientes/{id}/historia/anamnesis`, mostrando autor y fecha del último guardado en hora del consultorio.

#### Scenario: Guardado exitoso
- **GIVEN** la anamnesis está vacía
- **WHEN** el usuario completa "Penicilina" en alergias y guarda, y la API responde `200` con autor "Laura Gómez" y fecha `2026-11-26T13:00:00Z`
- **THEN** se muestra "Actualizada por Laura Gómez el 26/11/2026 10:00"

#### Scenario: Error de validación
- **WHEN** la API responde `400` con error en el campo `observaciones`
- **THEN** el mensaje se muestra junto a ese campo y lo cargado en el formulario se conserva

### Requirement: Odontograma visual de 32 piezas
La pantalla SHALL dibujar las 32 piezas permanentes en 4 cuadrantes FDI (18→11 y 21→28 arriba; 48→41 y 31→38 abajo), cada una con su número y su estado (o "sin registrar"), distinguibles por clase CSS y por texto.

#### Scenario: Orden y estados
- **GIVEN** la API devuelve la pieza 16 `CARIES` y las demás sin estado
- **WHEN** se renderiza el odontograma
- **THEN** hay 32 piezas, la primera fila empieza por 18 y termina en 28, y solo la pieza 16 indica "Caries"

### Requirement: Edición de una pieza
Al elegir una pieza, la pantalla SHALL abrir un editor con el estado (lista de los 9 estados del MVP) y la observación, y guardarlo con `PUT /api/v1/pacientes/{id}/historia/odontograma/piezas/{fdi}`, actualizando solo esa pieza en pantalla.

#### Scenario: Guardar el estado de una pieza
- **WHEN** el usuario elige la pieza 16, selecciona "Obturada", escribe "Resina" y guarda, y la API responde `200`
- **THEN** la pieza 16 muestra "Obturada" y el editor indica autor y fecha del cambio

#### Scenario: Error al guardar una pieza
- **WHEN** la API responde `422` al guardar la pieza
- **THEN** el editor muestra el título del `ProblemDetail` y la pieza mantiene su estado anterior

### Requirement: Listado y alta de evoluciones
La pantalla SHALL listar las evoluciones en el orden que entrega la API (cronológico), con fecha, autor y nota, y permitir el alta con nota y fecha (por defecto hoy en el consultorio) mediante `POST /api/v1/pacientes/{id}/historia/evoluciones`.

#### Scenario: Alta de una evolución
- **GIVEN** el paciente tiene una evolución
- **WHEN** el usuario escribe "Control post obturación" y guarda, y la API responde `201`
- **THEN** la nueva evolución aparece al final del listado y el campo de nota queda vacío

#### Scenario: Sin evoluciones
- **GIVEN** la API devuelve ninguna evolución
- **WHEN** se renderiza la sección
- **THEN** se muestra "Sin evoluciones registradas"

#### Scenario: Nota vacía
- **WHEN** el usuario intenta guardar una nota en blanco
- **THEN** no se llama a la API y se indica que la nota es obligatoria

### Requirement: Edición de una evolución habilitada por la API
La pantalla SHALL ofrecer "Editar" solo en las evoluciones que la API marca como editables y guardar la edición con `PUT /api/v1/pacientes/{id}/historia/evoluciones/{evolucionId}`.

#### Scenario: Evolución no editable
- **GIVEN** la API devuelve una evolución con `editable` en `false`
- **WHEN** se renderiza el listado
- **THEN** esa evolución no ofrece "Editar"

#### Scenario: Ventana vencida al guardar
- **GIVEN** el usuario está editando una evolución
- **WHEN** guarda y la API responde `422`
- **THEN** se muestra el título del `ProblemDetail`, se recarga el listado y la evolución deja de ofrecer "Editar"

### Requirement: Datos clínicos solo en pantalla
La pantalla MUST NOT escribir datos clínicos en la URL, en `localStorage`/`sessionStorage` ni en la consola.

#### Scenario: Sin persistencia local
- **WHEN** el usuario guarda anamnesis, una pieza y una evolución
- **THEN** la URL sigue siendo `/pacientes/7/historia` y no se escribió nada en `localStorage` ni en `sessionStorage`
