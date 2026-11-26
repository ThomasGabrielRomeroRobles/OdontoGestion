# web-landing Specification

## Purpose
Define la página pública de presentación en `/inicio`, con accesos a pedir turno y lista de espera, sin cuentas de paciente.

## Requirements

### Requirement: Landing pública
La aplicación SHALL ofrecer en `/inicio` una página pública de presentación del consultorio, accesible sin sesión, con los pasos del proceso y tarjetas informativas.

#### Scenario: Acceso sin sesión
- **WHEN** un visitante anónimo abre `/inicio`
- **THEN** se renderiza la landing sin redirigir a `/login`

#### Scenario: Contenido de presentación
- **WHEN** se renderiza la landing
- **THEN** se muestran los tres pasos y las tres tarjetas informativas

### Requirement: Llamadas a la acción
La landing SHALL ofrecer "Pedir turno" (enlace a `/publico`) y "Anotarme en lista de espera". Los pacientes MUST NOT tener cuenta, por lo que la acción "Ingresar" SHALL presentarse solo como acceso del odontólogo.

#### Scenario: CTAs para pacientes
- **WHEN** se renderiza la landing
- **THEN** existe un enlace "Pedir turno" hacia `/publico` y una acción "Anotarme en lista de espera"

#### Scenario: Ingreso solo para el odontólogo
- **WHEN** se renderiza la landing
- **THEN** "Ingresar" se indica como acceso del odontólogo y no se ofrece registro de pacientes

### Requirement: Movimiento reducido
Las animaciones de la landing SHALL desactivarse cuando el usuario tiene `prefers-reduced-motion: reduce`.

#### Scenario: Preferencia de movimiento reducido
- **GIVEN** el navegador declara `prefers-reduced-motion: reduce`
- **WHEN** se renderiza la landing
- **THEN** no se aplican animaciones ni transiciones
