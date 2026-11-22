# Spec Delta

## Purpose

Permite consultar la agenda del odontólogo: los huecos libres para una prestación, los turnos de un rango y del día, y los turnos afectados por un bloqueo, como información orientativa que no garantiza el hueco (la garantía es la restricción de la base).

## ADDED Requirements

### Requirement: Consulta de huecos libres
El odontólogo autenticado SHALL poder consultar los huecos libres para una prestación activa entre dos fechas (en la zona del consultorio). Un hueco MUST durar lo que la prestación, empezar alineado a la granularidad, caber en un horario de trabajo y no solaparse con bloqueos ni con turnos que ocupan agenda.

#### Scenario: Huecos de un día sin turnos
- **GIVEN** un horario de 09:00 a 10:00, granularidad de 15 minutos y una prestación de 30 minutos
- **WHEN** el odontólogo consulta la disponibilidad de ese día
- **THEN** la respuesta es `200` con los huecos de 09:00, 09:15 y 09:30, cada uno de 30 minutos, ordenados por inicio

#### Scenario: Turnos y bloqueos descuentan huecos
- **GIVEN** un horario de 09:00 a 11:00, un turno `RESERVADO` de 09:00 a 09:30 y un bloqueo de 10:00 a 11:00
- **WHEN** el odontólogo consulta la disponibilidad para una prestación de 30 minutos
- **THEN** los huecos son 09:30 y nada desde las 10:00

#### Scenario: Turnos cancelados y pendientes vencidas no ocupan
- **GIVEN** un turno `CANCELADO` y una pendiente vencida en el rango
- **WHEN** el odontólogo consulta la disponibilidad
- **THEN** esos intervalos aparecen como libres

#### Scenario: Día sin horario de trabajo
- **WHEN** el odontólogo consulta la disponibilidad de un día sin horario configurado
- **THEN** la respuesta es `200` con una lista vacía

#### Scenario: Huecos pasados excluidos
- **WHEN** el odontólogo consulta la disponibilidad del día actual
- **THEN** no se incluye ningún hueco cuyo inicio sea anterior al momento actual

#### Scenario: Prestación inactiva o inexistente
- **WHEN** el odontólogo consulta la disponibilidad de una prestación inactiva o inexistente
- **THEN** la respuesta es `422` para la inactiva y `404` para la inexistente

### Requirement: Anticipación mínima según quién reserva
El cálculo de huecos SHALL admitir dos políticas: para el odontólogo, huecos desde el momento actual; para solicitudes públicas, solo huecos que empiecen al menos la anticipación mínima configurada después del momento actual.

#### Scenario: Política pública
- **GIVEN** una anticipación mínima de 120 minutos y un hueco libre dentro de 60 minutos
- **WHEN** se calculan los huecos con la política pública
- **THEN** ese hueco no se incluye

#### Scenario: Política del odontólogo
- **GIVEN** el mismo hueco libre dentro de 60 minutos
- **WHEN** el odontólogo consulta la disponibilidad
- **THEN** ese hueco se incluye

### Requirement: Rango de consulta acotado
Las consultas de disponibilidad y de agenda SHALL exigir `desde` y `hasta` válidos con `desde <= hasta` y un rango de como máximo 31 días; si no MUST responder `400` o `422` sin ejecutar la consulta.

#### Scenario: Rango invertido
- **WHEN** se consulta la agenda con `desde` posterior a `hasta`
- **THEN** la respuesta es `422`

#### Scenario: Rango demasiado largo
- **WHEN** se consulta la disponibilidad para 60 días
- **THEN** la respuesta es `422`

#### Scenario: Parámetro faltante
- **WHEN** se consulta la agenda sin `desde`
- **THEN** la respuesta es `400`

### Requirement: Agenda por rango
El odontólogo autenticado SHALL poder listar los turnos del consultorio que empiezan entre dos fechas, en todos sus estados, ordenados por inicio, con id, inicio, fin, estado, origen, paciente (id, nombre, apellido), prestación (id, nombre) y versión.

#### Scenario: Agenda semanal
- **GIVEN** turnos `RESERVADO`, `PENDIENTE_CONFIRMACION` y `CANCELADO` en la semana
- **WHEN** el odontólogo consulta la agenda de esa semana
- **THEN** la respuesta es `200` con los tres turnos ordenados por inicio y su estado

#### Scenario: Agenda sin sesión
- **WHEN** se consulta la agenda sin sesión
- **THEN** la respuesta es `401`

### Requirement: Turnos del día
El odontólogo autenticado SHALL poder listar los turnos del día actual en la zona del consultorio, excluyendo `CANCELADO` y `EXPIRADO`, ordenados por inicio, con paciente (id, nombre, apellido), prestación y hora.

#### Scenario: Lista del día
- **GIVEN** hoy un turno `CONFIRMADO` a las 09:00, uno `ATENDIDO` a las 08:00 y uno `CANCELADO` a las 10:00
- **WHEN** el odontólogo consulta los turnos de hoy
- **THEN** la respuesta contiene el de 08:00 y el de 09:00, en ese orden, con el id del paciente

#### Scenario: Límite de día en la zona del consultorio
- **GIVEN** un turno a las 22:00 de Buenos Aires (01:00 UTC del día siguiente)
- **WHEN** el odontólogo consulta los turnos de hoy en Buenos Aires
- **THEN** el turno se incluye

### Requirement: Turnos afectados por un bloqueo
El odontólogo autenticado SHALL poder listar, para un bloqueo de agenda, los turnos que ocupan agenda y se solapan con su rango, para reubicarlos. Crear un bloqueo MUST NOT cancelar ni modificar turnos.

#### Scenario: Bloqueo que pisa turnos
- **GIVEN** un bloqueo de 10:00 a 12:00 y turnos `RESERVADO` a las 10:30 y `CANCELADO` a las 11:00
- **WHEN** el odontólogo consulta los turnos afectados por el bloqueo
- **THEN** la respuesta es `200` con solo el turno de 10:30

#### Scenario: Bloqueo inexistente
- **WHEN** el odontólogo consulta los turnos afectados de un bloqueo inexistente
- **THEN** la respuesta es `404`
