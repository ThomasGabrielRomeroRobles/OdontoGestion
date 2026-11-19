# Reglas de Negocio

Cada regla tiene un código único `RN-{DOMINIO}-{NN}` para trazabilidad. Las reglas marcadas **Suposición:** son valores por defecto aceptados que el consultorio puede ajustar.

## Dominio: Autenticación del odontólogo (RN-AU)

- **RN-AU-01**: El único usuario que inicia sesión es el **odontólogo**, con email y contraseña. La sesión es una **sesión de servidor de Spring Security** (cookie de sesión), no JWT. (Ver DD-03.)
- **RN-AU-02**: Las contraseñas se almacenan con hash adaptativo (BCrypt o Argon2); nunca en texto plano ni en logs.
- **RN-AU-03**: El email es único en el sistema y funciona como identificador de login.
- **RN-AU-04**: Los **pacientes no tienen cuenta ni login**; no existe registro de pacientes ni contraseñas de pacientes.
- **RN-AU-05**: No hay registro público de usuarios; el odontólogo se crea por seed/bootstrap con la contraseña tomada de una variable de entorno.
- **RN-AU-06**: La cookie de sesión es `HttpOnly`, `SameSite=Lax` y `Secure` cuando hay HTTPS; la sesión expira por inactividad (**Suposición:** 30 min), el logout la invalida y se rota el identificador al iniciar sesión (protección contra fijación de sesión).
- **RN-AU-07**: Las peticiones que modifican estado con sesión (`POST`, `PUT`, `PATCH`, `DELETE`) exigen **token CSRF**. El frontend lo lee de la cookie `XSRF-TOKEN` y lo envía en la cabecera `X-XSRF-TOKEN`.
- **RN-AU-08**: Todo endpoint exige sesión salvo `/api/v1/publico/**`, `/api/v1/acciones/**` y `/api/v1/avisos/**`; la autorización se verifica en el servidor, no en el frontend.
- **RN-AU-09**: El login tiene limitación de intentos fallidos por IP y por email. **Suposición:** 5 intentos y espera creciente.

## Dominio: Configuración y agenda (RN-AG)

- **RN-AG-01**: El MVP atiende a **un único consultorio con un único odontólogo**. Existe la entidad `Consultorio` (una sola instancia) para permitir multi-consultorio en el futuro; no hay aislamiento multi-tenant.
- **RN-AG-02**: La agenda trabaja con granularidad de **15 minutos**; todo inicio de turno y toda duración de prestación son múltiplos de 15 min. **Suposición:** configurable por el odontólogo.
- **RN-AG-03**: Cada **prestación** (tipo de turno) tiene una duración fija; el turno ocupa `inicio` a `inicio + duración`.
- **RN-AG-04**: Un turno ocupa la **agenda del odontólogo** durante todo su intervalo. No existen sillones ni otros recursos de agenda.
- **RN-AG-05**: **El odontólogo no puede tener dos turnos activos superpuestos** (estados `PENDIENTE_CONFIRMACION`, `RESERVADO`, `CONFIRMADO`). Es la **única regla de no superposición** y se garantiza con restricción de exclusión (`btree_gist`) en la base de datos.
- **RN-AG-06**: Ante reservas simultáneas del mismo horario **gana la primera** que llega a la base; las demás reciben `409 Conflict` (concurrencia resuelta por la restricción de exclusión, ver DD-06).
- **RN-AG-07**: Un turno solo puede crearse dentro de un `horario_trabajo` y fuera de los bloqueos de agenda.
- **RN-AG-08**: Todas las prestaciones activas del consultorio pueden agendarse (un solo odontólogo las realiza todas).
- **RN-AG-09**: Los intervalos son semiabiertos `[inicio, fin)`: un turno que termina a las 10:00 no se superpone con uno que empieza a las 10:00.
- **RN-AG-10**: Los turnos en estado `CANCELADO` o `EXPIRADO` no cuentan para la superposición y liberan el hueco.
- **RN-AG-11**: No se reserva en el pasado ni con menos anticipación que el mínimo configurado. **Suposición:** mínimo 2 horas para solicitudes públicas; el odontólogo puede crear turnos sin ese mínimo.
- **RN-AG-12**: Una solicitud pública `PENDIENTE_CONFIRMACION` **retiene el hueco** 30 min hasta su vencimiento (RN-PU-02, DD-14); al intentar reservar, el sistema primero marca como `EXPIRADO` las pendientes vencidas.
- **RN-AG-13**: Las fechas se guardan en UTC y se muestran en la zona horaria del consultorio (America/Argentina/Buenos_Aires).

## Dominio: Turnos (RN-TU)

- **RN-TU-01**: Estados del turno: `PENDIENTE_CONFIRMACION`, `RESERVADO`, `CONFIRMADO`, `CANCELADO`, `AUSENTE`, `ATENDIDO`, `EXPIRADO`.
- **RN-TU-02**: Transiciones válidas: `PENDIENTE_CONFIRMACION → RESERVADO | EXPIRADO | CANCELADO`; `RESERVADO → CONFIRMADO | CANCELADO | AUSENTE | ATENDIDO`; `CONFIRMADO → CANCELADO | AUSENTE | ATENDIDO`. `CANCELADO`, `EXPIRADO`, `AUSENTE` y `ATENDIDO` son estados finales. Los turnos creados por el odontólogo o por aceptación de un aviso nacen `RESERVADO`.
- **RN-TU-03**: El paciente puede cancelar su turno (con el enlace de email) hasta **N horas antes**. **Suposición:** N = 2 h; pasado ese límite solo el odontólogo puede cancelar.
- **RN-TU-04**: El odontólogo puede mover un turno (cambiar fecha u hora) siempre que el nuevo intervalo cumpla RN-AG-05 y RN-AG-07. Mover libera el hueco original y puede disparar RN-LE-01.
- **RN-TU-05**: Todo turno cancelado conserva `cancelado_por` y `motivo` (opcional) para auditoría.
- **RN-TU-06**: Un paciente no puede tener dos turnos activos superpuestos entre sí (en la práctica lo impide RN-AG-05). **Suposición.**
- **RN-TU-07**: Solo el odontólogo (con sesión) marca un turno como `ATENDIDO`.
- **RN-TU-08**: Tras un conflicto de superposición detectado por la base, la API responde `409 Conflict` con un mensaje claro y no deja estado parcial.

## Dominio: Página pública (RN-PU)

- **RN-PU-01**: Existe una **página pública sin login** donde el paciente (a) pide un turno entre los horarios libres o (b) se anota en la lista de espera, ingresando **nombre, apellido, DNI, email y teléfono**. Solo ve horarios libres, nunca datos de otros pacientes.
- **RN-PU-02**: *(anti-abuso, decidido: DD-14)* una solicitud pública crea el turno en `PENDIENTE_CONFIRMACION`, retiene el hueco 30 min y solo pasa a `RESERVADO` cuando el paciente abre el enlace de token de un solo uso enviado a su email y lo confirma. Si no confirma, el turno pasa a `EXPIRADO` y el hueco se libera.
- **RN-PU-03**: *(anti-abuso, decidido: DD-14)* limitación de tasa en los endpoints públicos: 5 solicitudes por hora por IP y como máximo 2 solicitudes pendientes por DNI o email. Excedido el límite: `429 Too Many Requests`.
- **RN-PU-04**: Si el DNI ya existe en el consultorio se reutiliza el paciente y **no se sobrescriben** sus datos de contacto desde la página pública; los emails se envían a la dirección ya registrada (**Suposición:** ver [10](10_preguntas_abiertas.md)). Si no existe, se crea un paciente sin cuenta con los datos ingresados.
- **RN-PU-05**: Una entrada de lista de espera pedida desde la página pública nace `PENDIENTE_CONFIRMACION` y solo pasa a `ACTIVA` al confirmar el enlace de email; hasta entonces no recibe avisos. Las cargadas por el odontólogo nacen `ACTIVA`.
- **RN-PU-06**: *(anti-abuso, decidido: DD-14)* el formulario incluye un campo señuelo (*honeypot*) y validación estricta de formato de DNI, email y teléfono; un CAPTCHA queda como mejora posterior (US-113).
- **RN-PU-07**: Las respuestas de la página pública no revelan si un DNI o email ya existen (anti-enumeración): el mensaje es siempre "si los datos son correctos, recibirás un email".

## Dominio: Tokens de acceso del paciente (RN-TK)

- **RN-TK-01**: Un paciente se identifica **solo** por un enlace de email con **token único por turno o por aviso** (y por entrada pendiente de lista de espera), de 128+ bits aleatorios.
- **RN-TK-02**: El token se guarda **hasheado** (`token_hash`); el valor en claro solo existe en el email.
- **RN-TK-03**: Cada token tiene **vencimiento** y es de **un solo uso**; además es de **un solo propósito** (confirmar solicitud, confirmar turno, cancelar turno, aceptar aviso, confirmar lista de espera).
- **RN-TK-04**: El `GET` del enlace solo muestra la información y un botón; la acción se ejecuta con `POST` (evita que escáneres de email o previsualizadores consuman el token).
- **RN-TK-05**: Token vencido, usado o inexistente: respuesta genérica sin cambios de estado (`410 Gone` o `404`), sin revelar datos del turno.

## Dominio: Lista de espera (RN-LE)

- **RN-LE-01**: Cuando se libera un hueco (cancelación o movimiento de un turno), el sistema identifica a todas las entradas `ACTIVA` **compatibles** y envía un **aviso por email a todos a la vez**, cada uno con un enlace de token único, de un solo uso y con vencimiento.
- **RN-LE-02**: Una entrada es **compatible** con un hueco si: misma prestación (o duración del hueco ≥ duración de la prestación) y fecha y franja horaria dentro de lo solicitado.
- **RN-LE-03**: **El primero que acepta gana.** La aceptación del hueco se resuelve de forma atómica; los demás reciben un mensaje de "hueco ya no disponible". Se implementa con la restricción de exclusión en base de datos y manejo de conflicto (ver DD-06).
- **RN-LE-04**: Aceptar un aviso **no requiere login**: el token identifica al paciente y al aviso. Al aceptarse, el turno nace `RESERVADO` (el email del token ya verifica al paciente).
- **RN-LE-05**: Al aceptarse el hueco, los avisos restantes pasan a `CERRADO` y la entrada ganadora a `CUBIERTA`.
- **RN-LE-06**: Un aviso vence a los **M minutos** o al inicio del turno, lo que ocurra primero. **Suposición:** M = 60 min.
- **RN-LE-07**: Si no hay pacientes compatibles, el hueco queda disponible en la agenda para reserva normal.
- **RN-LE-08**: Un paciente no recibe más de un aviso por hueco aunque tenga varias entradas compatibles.
- **RN-LE-09**: Una entrada de lista de espera se vence al superar `fecha_hasta`. El odontólogo puede retirarla en cualquier momento y el paciente puede cancelarla desde un enlace de sus emails.
- **RN-LE-10**: Los huecos que se liberan con menos anticipación que la mínima de reserva no se ofrecen (ver RN-AG-11). **Suposición.**
- **RN-LE-11**: Las entradas provienen de la página pública (tras confirmar el email) o son cargadas por el odontólogo.

## Dominio: Notificaciones (RN-NO)

- **RN-NO-01**: El MVP notifica por **email**; el diseño admite WhatsApp mediante un puerto común (ver [12](12_notificaciones.md)).
- **RN-NO-02**: Se envía un recordatorio **24 horas antes** del turno, con enlaces para confirmar y cancelar. **Suposición:** configurable.
- **RN-NO-03**: El envío es **idempotente**: no se envía más de un recordatorio por turno y tipo, ni más de un aviso por entrada y hueco, aunque el scheduler (`@Scheduled`) se ejecute varias veces o se reinicie.
- **RN-NO-04**: Los enlaces usan los tokens de RN-TK-01 a RN-TK-05.
- **RN-NO-05**: Los emails contienen **datos mínimos** (fecha, hora, consultorio), **nunca** diagnósticos ni datos clínicos.
- **RN-NO-06**: Un fallo de envío no impide ni revierte la operación de negocio; se registra y se reintenta con límite.
- **RN-NO-07**: Se notifica al paciente al crear, mover o cancelar un turno (confirmación de cambios).
- **RN-NO-08**: Si un turno se confirma o cancela desde el enlace, la agenda se actualiza inmediatamente.
- **RN-NO-09**: Un turno creado con menos de 24 h de anticipación no recibe recordatorio de 24 h (recibe solo la confirmación).

## Dominio: Ausentismo (RN-AS)

- **RN-AS-01**: El odontólogo puede marcar un turno como `AUSENTE` una vez superada la hora de inicio más una tolerancia. **Suposición:** tolerancia 15 min.
- **RN-AS-02**: Cada ausencia se registra en el **historial del paciente** y se suma a su contador de ausencias.
- **RN-AS-03**: Al marcar ausente, se envía un email al paciente **invitándolo a reprogramar**, con enlace a la página pública de reserva.
- **RN-AS-04**: Un turno ausente no libera retroactivamente el hueco (ya pasó), pero queda registrado para estadísticas de ausentismo.
- **RN-AS-05**: En el MVP las ausencias **no bloquean** futuras reservas; solo informan. **Suposición:** una política de bloqueo o seña por reincidencia queda para etapas posteriores (ver [10](10_preguntas_abiertas.md)).
- **RN-AS-06**: Una ausencia marcada por error puede revertirse por el odontólogo dentro de las 24 h, restaurando el contador. **Suposición.**

## Dominio: Pacientes (RN-PA)

- **RN-PA-01**: El DNI es único por consultorio.
- **RN-PA-02**: Un paciente puede crearse desde la página pública (RN-PU-04) o ser cargado por el odontólogo; en ningún caso tiene cuenta.
- **RN-PA-03**: Los datos de contacto (email, teléfono) son obligatorios para poder notificar.
- **RN-PA-04**: Un paciente no se elimina físicamente si tiene turnos o historia clínica; se desactiva (baja lógica).
- **RN-PA-05**: Un paciente creado desde la página pública no tiene historia clínica con contenido hasta que el odontólogo la inicia (**Suposición:** la HC se crea al primer uso clínico).

## Dominio: Historia clínica (RN-HC)

- **RN-HC-01**: Existe **una única historia clínica por paciente** (art. 17 Ley 26.529).
- **RN-HC-02**: Solo el odontólogo, con su sesión, puede crear, leer o modificar datos clínicos (anamnesis, odontograma, evolución). Los pacientes (por token o página pública) nunca acceden a datos clínicos.
- **RN-HC-03**: El odontograma usa numeración **FDI** y registra un estado por pieza (ver [04](04_modelo_de_datos.md)); toda modificación registra autor y fecha.
- **RN-HC-04**: Cada nota de evolución registra autor, fecha y, si corresponde, el turno asociado.
- **RN-HC-05**: **Etapa posterior:** la historia pasa a ser *append-only* (las enmiendas se agregan como nuevos asientos, nunca sobrescriben), con auditoría de accesos. En el MVP las modificaciones conservan al menos autor y fecha. (Requisito derivado del art. 13 Ley 26.529; ver [11](11_contexto_de_mercado.md).)
- **RN-HC-06**: Los datos clínicos nunca se incluyen en notificaciones, URLs ni logs.
- **RN-HC-07**: **Etapa posterior:** custodia mínima de 10 años desde la última actuación y copia al paciente en 48 horas (arts. 14 y 18 Ley 26.529).

## Dominio: Excepciones globales (RN-GL)

- **RN-GL-01**: Todos los datos del proyecto académico son **ficticios**; no se cargan datos reales de pacientes.
- **RN-GL-02**: Las Leyes 25.326 (datos personales), 26.529 (derechos del paciente / HC) y 27.706 (digitalización de HC) se documentan como **requisitos futuros de cumplimiento**; el MVP aplica buenas prácticas (acceso con clave, mínimo privilegio, tokens hasheados) sin pretender cumplimiento pleno.
- **RN-GL-03**: Toda operación que cambie la agenda es atómica (transacción): o se completa íntegramente o no se aplica.
- **RN-GL-04**: Los errores de validación devuelven códigos HTTP coherentes (`400`, `401`, `403`, `404`, `409`, `410`, `422`, `429`) con cuerpo de error uniforme.
- **RN-GL-05**: Las contraseñas, tokens y secretos nunca se registran en logs ni se versionan; las credenciales de SMTP y base se toman solo de variables de entorno.
