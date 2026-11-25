# Spec Delta

## MODIFIED Requirements

### Requirement: Formulario público de inscripción
La ruta pública `/publico/lista-espera` SHALL mostrar, sin sesión, la prestación (de las prestaciones públicas), fecha desde, fecha hasta y los datos de contacto, con el campo señuelo oculto. MUST validar en el cliente obligatorios, formatos, `fechaDesde` ≤ `fechaHasta` y `fechaHasta` no anterior a hoy en el calendario del consultorio antes de enviar, y tras un `202` mostrar un mensaje genérico de "revisá tu email". Los rechazos de la prestación MUST mostrarse junto al selector de prestación.

#### Scenario: Envío válido
- **GIVEN** el formulario completo con datos válidos
- **WHEN** el paciente pulsa "Anotarme"
- **THEN** se envía un solo `POST /api/v1/publico/lista-espera` y la vista muestra el mensaje genérico de confirmación por email

#### Scenario: Rango inválido
- **GIVEN** fecha hasta anterior a fecha desde
- **WHEN** el paciente pulsa "Anotarme"
- **THEN** la vista muestra el error junto a fecha hasta y no envía ninguna petición

#### Scenario: Fecha hasta anterior a hoy
- **GIVEN** hoy es 25/11/2026 en el consultorio y el paciente eligió fecha hasta 24/11/2026
- **WHEN** el paciente pulsa "Anotarme"
- **THEN** la vista muestra "No puede ser anterior a hoy." junto a fecha hasta y no envía ninguna petición

#### Scenario: Prestación desactivada
- **GIVEN** el formulario completo con datos válidos
- **WHEN** la API responde `422` con el error "La prestación está desactivada." en `prestacionId`
- **THEN** la vista muestra ese mensaje junto al selector de prestación y lo marca como inválido

#### Scenario: Prestación inexistente
- **GIVEN** el formulario completo con datos válidos
- **WHEN** la API responde `404`
- **THEN** la vista informa que la prestación elegida ya no está disponible, vuelve a pedir la lista de prestaciones y deja el selector sin elección

#### Scenario: Demasiadas solicitudes
- **WHEN** la API responde `429`
- **THEN** la vista informa que hubo demasiados intentos y que pruebe más tarde
