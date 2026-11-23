# Spec Delta

## ADDED Requirements

### Requirement: Confirmación de la inscripción en la lista de espera
Cuando el resumen de `/acciones/{token}` trae `accion` `CONFIRMAR_LISTA_ESPERA`, la vista SHALL mostrar el consultorio, la prestación y el rango de fechas de la entrada con un botón "Confirmar inscripción"; al confirmarse con `200` MUST informar que la inscripción quedó activa y que recibirá un aviso cuando se libere un horario compatible.

#### Scenario: Resumen de la inscripción
- **GIVEN** el resumen devuelve `accion` `CONFIRMAR_LISTA_ESPERA` con una `entrada` y `turno` nulo
- **WHEN** el paciente abre el enlace
- **THEN** la vista muestra la prestación y el rango de fechas y el botón "Confirmar inscripción", sin enviar ningún `POST`

#### Scenario: Inscripción confirmada
- **GIVEN** la vista muestra la inscripción pendiente
- **WHEN** el paciente pulsa "Confirmar inscripción" y la API responde `200`
- **THEN** la vista informa que la inscripción quedó activa y el botón no vuelve a estar disponible
