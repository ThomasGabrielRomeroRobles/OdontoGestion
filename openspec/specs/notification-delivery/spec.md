# notification-delivery Specification

## Purpose

Enviar las notificaciones a pacientes de forma confiable mediante un outbox persistido y un canal intercambiable (email en el MVP), con idempotencia, reintentos acotados y contenido mínimo sin datos clínicos, sin que un fallo de envío afecte la operación de negocio.

## Requirements

### Requirement: Tipos de notificación
El sistema SHALL soportar los tipos `CONFIRMAR_SOLICITUD`, `CONFIRMAR_LISTA_ESPERA`, `CONFIRMACION_TURNO`, `RECORDATORIO`, `AVISO_HUECO`, `CANCELACION` y `AUSENTE_REPROGRAMAR`, cada uno con una plantilla propia de asunto y cuerpo, y el canal `EMAIL` en el MVP.

#### Scenario: Cada tipo tiene plantilla
- **WHEN** se renderiza cada uno de los siete tipos con datos ficticios válidos
- **THEN** cada uno produce un asunto y un cuerpo no vacíos con los datos provistos

### Requirement: Encolado dentro de la operación de origen
Encolar una notificación SHALL persistirla como `PENDIENTE` con cero intentos dentro de la transacción de la operación que la origina, sin enviarla en ese momento; si la operación de origen se revierte, la notificación MUST NOT quedar persistida.

#### Scenario: Notificación encolada pendiente
- **WHEN** una operación encola una `CONFIRMACION_TURNO` para el turno 101 y confirma su transacción
- **THEN** existe una notificación `PENDIENTE`, con 0 intentos, destinatario y turno 101, y el canal no recibió ningún envío

#### Scenario: Origen revertido descarta la notificación
- **WHEN** una operación encola una notificación y luego su transacción se revierte
- **THEN** no queda ninguna notificación persistida

### Requirement: Encolado idempotente
El encolado SHALL ser idempotente: a lo sumo una notificación por turno para cada tipo `CONFIRMAR_SOLICITUD`, `RECORDATORIO`, `CANCELACION` y `AUSENTE_REPROGRAMAR`; a lo sumo una por aviso; y a lo sumo una `CONFIRMAR_LISTA_ESPERA` por entrada. Un encolado duplicado MUST ignorarse sin error y sin afectar la transacción de origen. `CONFIRMACION_TURNO` MAY repetirse para el mismo turno (cada movimiento se confirma).

#### Scenario: Recordatorio duplicado ignorado
- **GIVEN** un `RECORDATORIO` ya encolado para el turno 101
- **WHEN** se encola otro `RECORDATORIO` para el turno 101 en otra transacción que luego también escribe otros datos
- **THEN** sigue existiendo un único `RECORDATORIO` del turno 101 y la otra transacción confirma sus datos normalmente

#### Scenario: Aviso duplicado ignorado
- **WHEN** se encola dos veces un `AVISO_HUECO` para el aviso 7
- **THEN** existe una única notificación del aviso 7

#### Scenario: Confirmación repetida por movimiento
- **WHEN** se encolan dos `CONFIRMACION_TURNO` para el turno 101 (alta y movimiento)
- **THEN** existen dos notificaciones `CONFIRMACION_TURNO` del turno 101

#### Scenario: Unicidad garantizada por la base
- **WHEN** se insertan directamente en la base dos `RECORDATORIO` del mismo turno
- **THEN** la segunda inserción es rechazada por un índice único

### Requirement: Contenido mínimo sin datos clínicos
Las notificaciones SHALL contener solo datos mínimos (nombre del consultorio, fecha y hora en la zona horaria de presentación, prestación, enlaces). El encolado MUST rechazar variables no declaradas por la plantilla del tipo, y ninguna notificación MUST incluir diagnósticos, anamnesis, odontograma ni evoluciones.

#### Scenario: Variable no declarada rechazada
- **WHEN** se intenta encolar un `RECORDATORIO` con una variable `diagnostico`
- **THEN** el encolado es rechazado y no se persiste ninguna notificación

#### Scenario: Fecha y hora en la zona de presentación
- **WHEN** se envía una `CONFIRMACION_TURNO` de un turno que inicia a las 13:00 UTC
- **THEN** el cuerpo muestra la hora 10:00 (America/Argentina/Buenos_Aires)

### Requirement: Enlaces con tokens emitidos al enviar
Una notificación con enlaces SHALL persistir solo el propósito y el vencimiento de cada enlace; el token de cada enlace MUST emitirse en el momento del envío para la referencia de la notificación, y su valor en claro MUST NOT persistirse en el outbox ni en el registro de errores. Los enlaces SHALL tener la forma `APP_BASE_URL/acciones/{token}` o, para `ACEPTAR_AVISO`, `APP_BASE_URL/avisos/{token}`.

#### Scenario: Enlace armado al enviar
- **GIVEN** un `RECORDATORIO` pendiente del turno 101 con enlaces `CONFIRMAR_TURNO` y `CANCELAR_TURNO`
- **WHEN** se procesa con éxito
- **THEN** el email contiene dos enlaces `APP_BASE_URL/acciones/{token}` cuyos tokens son válidos para esos propósitos sobre el turno 101

#### Scenario: El outbox no guarda tokens
- **WHEN** se inspecciona la fila de una notificación enviada con enlaces
- **THEN** ninguna columna contiene el valor de un token

#### Scenario: Enlace vencido antes del envío
- **GIVEN** una `CONFIRMAR_SOLICITUD` pendiente cuyo enlace venció
- **WHEN** se procesa
- **THEN** no se envía, queda `FALLIDA` con un error que indica el vencimiento y no se emite ningún token

### Requirement: Procesamiento con reintentos acotados
El procesamiento SHALL enviar las notificaciones `PENDIENTE` cuyo momento programado ya llegó. Un envío exitoso MUST dejarla `ENVIADA` con la hora de envío; un fallo MUST incrementar los intentos, registrar el error y reprogramarla con espera creciente; al tercer intento fallido MUST quedar `FALLIDA`.

#### Scenario: Envío exitoso
- **GIVEN** una notificación pendiente programada para ahora
- **WHEN** se procesan los pendientes y el canal acepta el envío
- **THEN** queda `ENVIADA`, con 1 intento y `enviada_en` igual al instante actual

#### Scenario: Fallo y reintento con espera creciente
- **GIVEN** un canal que falla siempre
- **WHEN** se procesa la notificación a las 12:00, de nuevo al vencer la primera espera y de nuevo al vencer la segunda, más larga
- **THEN** tras el primer y segundo fallo sigue `PENDIENTE` con 1 y 2 intentos y la segunda espera es mayor que la primera; tras el tercero queda `FALLIDA` con 3 intentos y el último error

#### Scenario: No se reprocesa antes de tiempo
- **GIVEN** una notificación reprogramada para dentro de 1 minuto
- **WHEN** se procesan los pendientes ahora
- **THEN** el canal no recibe ningún envío y los intentos no cambian

#### Scenario: Un fallo no frena al resto
- **GIVEN** dos notificaciones pendientes, la primera con un destinatario que el canal rechaza
- **WHEN** se procesan los pendientes
- **THEN** la primera registra el fallo y la segunda queda `ENVIADA`

### Requirement: Fallo de envío sin revertir la operación de negocio
Un fallo del canal SHALL registrarse en la notificación y MUST NOT revertir ni impedir la operación de negocio que la originó, ni propagarse como error al usuario de esa operación.

#### Scenario: Turno conservado con canal caído
- **GIVEN** el servidor de correo no responde
- **WHEN** una operación crea datos de negocio, encola una notificación y luego se procesan los pendientes
- **THEN** los datos de negocio persisten y la notificación registra el error del intento

### Requirement: Procesamiento periódico
El sistema SHALL procesar las notificaciones pendientes de forma periódica dentro del mismo proceso, con un intervalo configurable (por defecto 5 minutos), y retomar los pendientes después de un reinicio. Dos ejecuciones superpuestas MUST NOT enviar dos veces la misma notificación.

#### Scenario: Pendientes retomados al reiniciar
- **GIVEN** notificaciones `PENDIENTE` persistidas antes de detener la aplicación
- **WHEN** la aplicación vuelve a arrancar y corre el procesamiento periódico
- **THEN** esas notificaciones se envían

#### Scenario: Ejecuciones superpuestas sin doble envío
- **GIVEN** una notificación pendiente
- **WHEN** dos procesamientos corren al mismo tiempo
- **THEN** el canal recibe exactamente un envío de esa notificación

### Requirement: Canal intercambiable con contrato común
Todo canal SHALL cumplir un mismo contrato verificable: recibe una notificación neutra (tipo, destinatario, asunto, cuerpo) y devuelve éxito o fallo con motivo, sin lanzar errores al procesamiento. El canal de email MUST usar el remitente configurado (`MAIL_FROM`) y credenciales solo desde variables de entorno.

#### Scenario: Email entregado al servidor SMTP
- **GIVEN** un servidor SMTP de pruebas
- **WHEN** el canal de email envía una notificación a `paciente@ejemplo.test`
- **THEN** el servidor recibe un mensaje para ese destinatario, desde `MAIL_FROM`, con el asunto y el cuerpo renderizados

#### Scenario: Servidor SMTP inaccesible
- **WHEN** el canal de email intenta enviar sin servidor disponible
- **THEN** devuelve un resultado de fallo con motivo, sin lanzar una excepción

### Requirement: Notificación asociada al consultorio y a su referencia
Cada notificación SHALL registrar el consultorio actual, el destinatario, exactamente una referencia coherente con su tipo (`AVISO_HUECO` → aviso, `CONFIRMAR_LISTA_ESPERA` → entrada de lista de espera, el resto → turno) y los instantes de creación, programación y envío en UTC.

#### Scenario: Notificación con consultorio y referencia
- **WHEN** se encola una `AVISO_HUECO` para el aviso 7
- **THEN** la fila tiene el `consultorio_id` actual, `aviso_id` 7, sin `turno_id` ni entrada, y `programada_para` en UTC

#### Scenario: Referencia incoherente rechazada
- **WHEN** se intenta encolar un `RECORDATORIO` que referencia un aviso, o se inserta directamente en la base una notificación sin referencia
- **THEN** la operación es rechazada
