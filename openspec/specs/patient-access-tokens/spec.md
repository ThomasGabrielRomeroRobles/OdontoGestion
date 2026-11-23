# patient-access-tokens Specification

## Purpose

Identificar a pacientes sin cuenta mediante tokens de enlace de email aleatorios, guardados solo como hash, con vencimiento, un solo uso y un solo propósito, reutilizables por las acciones públicas (confirmar, cancelar, aceptar aviso).

## Requirements

### Requirement: Emisión de tokens aleatorios
El sistema SHALL emitir cada token de acceso con al menos 128 bits aleatorios de un generador criptográficamente seguro, codificado en un formato apto para la ruta de una URL (sin caracteres que requieran escape). Cada emisión SHALL asociar el token a un propósito, a exactamente una referencia (turno, aviso o entrada de lista de espera) y a un instante de vencimiento posterior al actual.

#### Scenario: Token con entropía suficiente y URL-safe
- **WHEN** se emite un token para el propósito `CONFIRMAR_TURNO` sobre el turno 101 con vencimiento dentro de 24 h
- **THEN** el valor devuelto representa al menos 128 bits aleatorios y solo contiene caracteres `A-Z`, `a-z`, `0-9`, `-` y `_`

#### Scenario: Tokens distintos en emisiones sucesivas
- **WHEN** se emiten 1000 tokens para la misma referencia y el mismo propósito
- **THEN** los 1000 valores son distintos entre sí

#### Scenario: Vencimiento en el pasado rechazado
- **WHEN** se intenta emitir un token con un vencimiento igual o anterior al instante actual
- **THEN** la emisión es rechazada y no se persiste ningún token

### Requirement: Propósito coherente con la referencia
Los propósitos válidos SHALL ser `CONFIRMAR_SOLICITUD`, `CONFIRMAR_TURNO`, `CANCELAR_TURNO`, `ACEPTAR_AVISO` y `CONFIRMAR_LISTA_ESPERA`. Los tres primeros MUST referenciar un turno, `ACEPTAR_AVISO` MUST referenciar un aviso y `CONFIRMAR_LISTA_ESPERA` MUST referenciar una entrada de lista de espera; la base de datos MUST rechazar filas sin exactamente una referencia o con una referencia incoherente con el propósito.

#### Scenario: Propósito y referencia coherentes
- **WHEN** se emite un token `ACEPTAR_AVISO` para el aviso 7
- **THEN** el token se persiste referenciando solo al aviso 7

#### Scenario: Ninguna o varias referencias rechazadas por la base
- **WHEN** se inserta directamente en la base un token sin referencias, o con turno y aviso a la vez
- **THEN** la inserción es rechazada por una restricción de la base

#### Scenario: Referencia incoherente rechazada por la base
- **WHEN** se inserta directamente en la base un token `ACEPTAR_AVISO` que referencia un turno
- **THEN** la inserción es rechazada por una restricción de la base

### Requirement: Solo se almacena el hash del token
El sistema SHALL persistir únicamente el hash SHA-256 del token, único en toda la tabla; el valor en claro MUST NOT persistirse en ninguna tabla ni escribirse en logs, y solo MUST existir en memoria al emitirlo para armar el enlace del email.

#### Scenario: Valor en claro ausente de la base
- **WHEN** se emite un token y se inspecciona la fila persistida
- **THEN** la fila contiene el SHA-256 del valor (32 bytes) y ninguna columna contiene el valor en claro

#### Scenario: Valor en claro ausente de los logs
- **WHEN** se emite, verifica y consume un token con el nivel de log más detallado de la aplicación
- **THEN** ninguna línea de log contiene el valor en claro del token

#### Scenario: Hash duplicado rechazado
- **WHEN** se inserta directamente en la base un segundo token con el mismo hash
- **THEN** la inserción es rechazada por la restricción de unicidad

### Requirement: Verificación sin consumo
El sistema SHALL ofrecer una verificación de solo lectura que, dado un valor y un propósito esperado, devuelva la referencia y el vencimiento si el token existe, tiene ese propósito, no fue usado y no venció. La verificación MUST NOT modificar el token, para que mostrar un enlace (`GET`) nunca lo consuma.

#### Scenario: Verificación de token vigente
- **GIVEN** un token `CANCELAR_TURNO` vigente para el turno 101
- **WHEN** se verifica con el propósito `CANCELAR_TURNO`
- **THEN** se obtiene la referencia al turno 101 y su vencimiento

#### Scenario: Verificar repetidamente no consume
- **GIVEN** un token vigente
- **WHEN** se verifica tres veces y luego se consume con el propósito correcto
- **THEN** las tres verificaciones son exitosas y el consumo también lo es

### Requirement: Consumo atómico de un solo uso
El sistema SHALL consumir un token mediante una única actualización condicional que marque `usado_en` solo si el token tiene el propósito esperado, no fue usado y no venció según el reloj de la aplicación, devolviendo la referencia. El consumo MUST participar de la transacción de la acción que lo invoca, de modo que acción y consumo se confirmen o reviertan juntos.

#### Scenario: Primer consumo exitoso
- **GIVEN** un token `CONFIRMAR_TURNO` vigente para el turno 101
- **WHEN** se consume con el propósito `CONFIRMAR_TURNO`
- **THEN** se obtiene la referencia al turno 101 y el token queda con `usado_en` igual al instante actual

#### Scenario: Segundo consumo rechazado
- **GIVEN** un token ya consumido
- **WHEN** se intenta consumir otra vez
- **THEN** el consumo es rechazado como token inválido y `usado_en` no cambia

#### Scenario: Dos consumos concurrentes
- **GIVEN** un token vigente
- **WHEN** dos transacciones intentan consumirlo al mismo tiempo
- **THEN** exactamente una obtiene la referencia y la otra es rechazada como token inválido

#### Scenario: La acción falla y el token no se consume
- **GIVEN** un token vigente consumido dentro de una transacción
- **WHEN** la acción de esa transacción falla y la transacción se revierte
- **THEN** el token sigue sin usar y puede consumirse después

#### Scenario: Token vencido según el reloj de la aplicación
- **GIVEN** un token emitido con vencimiento a las 12:30 UTC
- **WHEN** el reloj de la aplicación marca 12:30 UTC o más y se intenta verificarlo o consumirlo
- **THEN** la operación es rechazada como token inválido

### Requirement: Propósito incorrecto sin efecto
Un token usado con un propósito distinto del emitido SHALL tratarse como token inválido y MUST NOT consumirse, de modo que sigue siendo válido para su propósito original.

#### Scenario: Propósito equivocado no consume
- **GIVEN** un token `CONFIRMAR_TURNO` vigente
- **WHEN** se intenta consumir con el propósito `CANCELAR_TURNO`
- **THEN** el consumo es rechazado como token inválido y el token sigue consumible con `CONFIRMAR_TURNO`

### Requirement: Rechazo uniforme de tokens inválidos
Un token inexistente, mal formado, vencido, ya usado, revocado o de otro propósito SHALL producir el mismo rechazo, sin distinguir la causa ni revelar datos de la referencia, y sin cambios de estado.

#### Scenario: Causas indistinguibles
- **WHEN** se intenta consumir un valor inexistente, un valor mal formado, un token vencido, un token usado y un token de otro propósito
- **THEN** los cinco intentos producen el mismo tipo de rechazo con el mismo mensaje

### Requirement: Revocación por referencia
El sistema SHALL permitir revocar todos los tokens vigentes de una referencia (opcionalmente de un propósito), dejándolos vencidos de inmediato, para que los consumidores invaliden enlaces cuando la referencia deja de admitir la acción.

#### Scenario: Revocar los tokens de un turno
- **GIVEN** dos tokens vigentes (`CONFIRMAR_TURNO` y `CANCELAR_TURNO`) del turno 101 y uno vigente del turno 102
- **WHEN** se revocan los tokens del turno 101
- **THEN** los dos tokens del turno 101 son rechazados como inválidos y el del turno 102 sigue vigente

#### Scenario: Revocar no afecta tokens ya usados
- **GIVEN** un token del turno 101 ya consumido
- **WHEN** se revocan los tokens del turno 101
- **THEN** su `usado_en` no cambia

### Requirement: Token asociado al consultorio
Cada token SHALL registrar el consultorio actual y los instantes de creación y actualización en UTC.

#### Scenario: Token con consultorio
- **WHEN** se emite un token
- **THEN** la fila persistida tiene el `consultorio_id` del consultorio actual y `creado_en` en UTC

### Requirement: Identificación sin consumo entre propósitos admitidos
El sistema SHALL ofrecer una consulta de solo lectura que, dado un valor y un conjunto de propósitos admitidos, devuelva el propósito, la referencia y el vencimiento del token si existe, no fue usado, no venció y su propósito pertenece al conjunto. Si no, MUST rechazarlo como token inválido, con el mismo rechazo uniforme, y nunca modificar el token.

#### Scenario: Identificar un token vigente
- **GIVEN** un token `CANCELAR_TURNO` vigente para el turno 101
- **WHEN** se identifica con los propósitos admitidos `CONFIRMAR_SOLICITUD`, `CONFIRMAR_TURNO` y `CANCELAR_TURNO`
- **THEN** se obtiene el propósito `CANCELAR_TURNO`, la referencia al turno 101 y su vencimiento, y el token sigue sin usar

#### Scenario: Propósito fuera del conjunto
- **GIVEN** un token `ACEPTAR_AVISO` vigente
- **WHEN** se identifica con los propósitos admitidos `CONFIRMAR_SOLICITUD`, `CONFIRMAR_TURNO` y `CANCELAR_TURNO`
- **THEN** es rechazado como token inválido y sigue consumible con `ACEPTAR_AVISO`

#### Scenario: Token usado o vencido
- **WHEN** se identifica un token ya usado o uno vencido según el reloj de la aplicación
- **THEN** ambos son rechazados con el mismo rechazo que un valor inexistente
