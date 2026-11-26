## ADDED Requirements

### Requirement: Listado de pacientes
La aplicación SHALL mostrar en `/pacientes` el listado de pacientes consultando `GET /api/v1/pacientes`, con una insignia de ausencias por fila.

#### Scenario: Carga inicial
- **WHEN** el odontólogo abre `/pacientes`
- **THEN** se consulta la API sin filtro y se dibuja una fila por paciente con su insignia de ausencias

### Requirement: Búsqueda de pacientes
La búsqueda SHALL enviar el texto como `dni` si son 7 u 8 dígitos, como `q` en cualquier otro caso y sin filtro si está vacío. La API devuelve como máximo 20 resultados.

#### Scenario: Búsqueda por DNI
- **WHEN** el usuario busca "12345678"
- **THEN** la consulta envía solo el parámetro `dni`

#### Scenario: Búsqueda por texto
- **WHEN** el usuario busca "perez"
- **THEN** la consulta envía el parámetro `q`

### Requirement: Estados del listado
El listado SHALL mostrar un estado vacío cuando no hay resultados y el mensaje de error de la API cuando la consulta falla.

#### Scenario: Sin resultados
- **WHEN** la API devuelve una lista vacía
- **THEN** se muestra el estado vacío

#### Scenario: Error de la API
- **WHEN** la consulta falla
- **THEN** se muestra el mensaje de error

### Requirement: Ficha de paciente
La aplicación SHALL mostrar en `/pacientes/:id` los datos de contacto del paciente y su historial de ausencias obtenido de `GET /api/v1/pacientes/{id}/ausencias` (US-061).

#### Scenario: Ficha con ausencias
- **WHEN** el odontólogo abre `/pacientes/:id` de un paciente con ausencias
- **THEN** se muestran sus datos de contacto y la cantidad y fechas de ausencias

#### Scenario: Sin ausencias o con error
- **WHEN** el paciente no tiene ausencias o la consulta falla
- **THEN** se muestra un mensaje de sin ausencias o el error, respectivamente

### Requirement: Marcador de historia clínica
La ficha SHALL incluir una tarjeta "Historia clínica — próximamente (C-05)" mientras C-05 no esté entregado.

#### Scenario: Placeholder visible
- **WHEN** se renderiza la ficha de un paciente
- **THEN** se muestra la tarjeta de historia clínica como próximamente
