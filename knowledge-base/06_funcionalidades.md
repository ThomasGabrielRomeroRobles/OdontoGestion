# Funcionalidades

Organizadas por **épica** y luego por **historia de usuario** (formato US-NNN). Las épicas 1 a 8 son el **MVP**; la épica 9 recoge las etapas posteriores. Prioridad: **M** = MVP imprescindible, **P** = posterior. El **odontólogo** es el único usuario con login; el **paciente** actúa sin cuenta, por la página pública o por enlaces de email.

## Épica 1: Acceso del odontólogo (M)

### US-001 — Inicio de sesión del odontólogo
**Como** odontólogo
**Quiero** iniciar sesión con email y contraseña
**Para** acceder a la agenda, la configuración y las historias clínicas

**Criterios de aceptación**:
- [ ] Credenciales válidas crean una sesión de servidor (cookie `HttpOnly`) y devuelven el token CSRF.
- [ ] Credenciales inválidas devuelven `401` sin revelar cuál dato falló.
- [ ] Tras varios intentos fallidos se aplica limitación de intentos.
- [ ] Sin sesión, las rutas privadas redirigen a `/login`.

**Reglas relacionadas**: RN-AU-01, RN-AU-02, RN-AU-06, RN-AU-07, RN-AU-08, RN-AU-09

### US-002 — Cierre y expiración de sesión
**Como** odontólogo
**Quiero** cerrar sesión y que la sesión expire sola por inactividad
**Para** proteger los datos clínicos del equipo compartido

**Criterios de aceptación**:
- [ ] El logout invalida la sesión en el servidor.
- [ ] La sesión expira por inactividad y la interfaz vuelve al login.
- [ ] Las peticiones de escritura sin token CSRF válido se rechazan (`403`).

**Reglas relacionadas**: RN-AU-06, RN-AU-07

### US-003 — Alta inicial del odontólogo (bootstrap)
**Como** equipo de desarrollo
**Quiero** crear el odontólogo y el consultorio por seed al arrancar
**Para** poder usar el sistema sin registro público

**Criterios de aceptación**:
- [ ] El seed crea el `Consultorio` y el usuario odontólogo con contraseña tomada de una variable de entorno.
- [ ] No existe endpoint de registro de usuarios.

**Reglas relacionadas**: RN-AU-04, RN-AU-05, RN-AG-01

## Épica 2: Configuración del consultorio (M)

### US-010 — Datos del consultorio y del odontólogo
**Como** odontólogo
**Quiero** ver y editar los datos del consultorio y mi perfil
**Para** que los emails y la página pública muestren información correcta

**Criterios de aceptación**:
- [ ] Edición de nombre, dirección, teléfono y email de contacto del consultorio.
- [ ] Edición de mis datos y cambio de contraseña.

**Reglas relacionadas**: RN-AG-01

### US-011 — Tipos de turno (prestaciones)
**Como** odontólogo
**Quiero** definir prestaciones con su duración
**Para** que la agenda calcule la duración de cada turno

**Criterios de aceptación**:
- [ ] La duración es múltiplo de 15 min.
- [ ] Puedo desactivar una prestación sin perder historial.

**Reglas relacionadas**: RN-AG-02, RN-AG-03, RN-AG-08

### US-012 — Horarios de trabajo y bloqueos
**Como** odontólogo
**Quiero** definir mis horarios semanales y bloquear franjas (vacaciones, imprevistos)
**Para** que no se reserven turnos fuera de mi disponibilidad

**Criterios de aceptación**:
- [ ] Los horarios no se superponen en un mismo día.
- [ ] Un bloqueo impide nuevas reservas en su rango.
- [ ] Si un bloqueo afecta turnos existentes, el sistema los lista para reubicarlos.

**Reglas relacionadas**: RN-AG-07

### US-013 — Parámetros del consultorio
**Como** odontólogo
**Quiero** configurar granularidad, anticipación mínima, límite de cancelación, hora de recordatorio y vencimiento de solicitudes pendientes
**Para** adaptar las reglas a mi forma de trabajo

**Criterios de aceptación**:
- [ ] Los parámetros tienen valores por defecto (15 min, 24 h, 30 min de TTL pendiente, etc.).
- [ ] Los cambios aplican a nuevas operaciones, no a turnos ya creados.

**Reglas relacionadas**: RN-AG-02, RN-AG-11, RN-TU-03, RN-NO-02, RN-PU-02

## Épica 3: Agenda y turnos (M)

### US-020 — Ver agenda
**Como** odontólogo
**Quiero** ver mi agenda por día y semana
**Para** tener visibilidad de la ocupación

**Criterios de aceptación**:
- [ ] Vista por día y semana.
- [ ] Colores por estado del turno (incluye pendientes de confirmación).

**Reglas relacionadas**: RN-AG-04, RN-TU-01

### US-021 — Crear turno
**Como** odontólogo
**Quiero** crear un turno para un paciente eligiendo prestación y horario
**Para** registrar turnos pedidos por teléfono o en persona

**Criterios de aceptación**:
- [ ] El sistema propone horarios libres respetando la duración.
- [ ] Si el horario ya fue tomado, se informa conflicto (`409`) y se sugieren alternativas.
- [ ] Puedo buscar un paciente por DNI o crearlo en el momento.

**Reglas relacionadas**: RN-AG-05, RN-AG-06, RN-AG-07, RN-TU-08

### US-022 — Mover o reprogramar turno
**Como** odontólogo
**Quiero** mover un turno a otro horario
**Para** acomodar cambios sin cancelar y recrear

**Criterios de aceptación**:
- [ ] Se valida superposición del nuevo intervalo.
- [ ] El hueco original se libera y dispara la lista de espera.
- [ ] El paciente recibe notificación del cambio.

**Reglas relacionadas**: RN-TU-04, RN-LE-01, RN-NO-07

### US-023 — Cancelar turno
**Como** odontólogo
**Quiero** cancelar un turno con motivo
**Para** liberar el hueco y avisar al paciente

**Criterios de aceptación**:
- [ ] El turno pasa a `CANCELADO` y registra quién y por qué.
- [ ] Se dispara el aviso a la lista de espera.

**Reglas relacionadas**: RN-TU-05, RN-AG-10, RN-LE-01

### US-024 — Atender turno
**Como** odontólogo
**Quiero** ver mis turnos del día y marcarlos como atendidos
**Para** llevar el registro de mi jornada

**Criterios de aceptación**:
- [ ] Veo la lista del día con paciente, prestación y hora.
- [ ] Desde un turno accedo a la historia clínica del paciente.

**Reglas relacionadas**: RN-TU-07

## Épica 4: Página pública y acciones del paciente (M)

### US-030 — Solicitar turno desde la página pública
**Como** paciente (sin cuenta)
**Quiero** elegir una prestación y un horario libre e ingresar mis datos (nombre, DNI, email, teléfono)
**Para** pedir mi turno sin llamar

**Criterios de aceptación**:
- [ ] Solo se muestran horarios realmente libres.
- [ ] La solicitud queda `PENDIENTE_CONFIRMACION`, retiene el hueco y envía un email con enlace de confirmación.
- [ ] Si otro toma el horario antes, se informa y se ofrecen alternativas (`409`).
- [ ] Se aplican limitación de tasa y campo señuelo (anti-abuso decidido, DD-14).
- [ ] La respuesta no revela si el DNI o email ya existían.

**Reglas relacionadas**: RN-PU-01, RN-PU-02, RN-PU-03, RN-PU-06, RN-PU-07, RN-AG-12

### US-031 — Confirmar la solicitud desde el email
**Como** paciente
**Quiero** confirmar mi solicitud con el enlace que recibí por email
**Para** que mi turno quede reservado

**Criterios de aceptación**:
- [ ] El enlace abre una página con el resumen y un botón de confirmar (`POST`).
- [ ] Confirmar pasa el turno a `RESERVADO` y envía la confirmación.
- [ ] Un enlace vencido informa que la solicitud expiró y que el hueco fue liberado.

**Reglas relacionadas**: RN-PU-02, RN-TK-01, RN-TK-03, RN-TK-04

### US-032 — Confirmar o cancelar desde el recordatorio
**Como** paciente
**Quiero** confirmar o cancelar con un enlace del recordatorio
**Para** hacerlo sin iniciar sesión

**Criterios de aceptación**:
- [ ] El token es único por turno, de un solo uso, vence y solo permite esa acción.
- [ ] Confirmar actualiza el turno a `CONFIRMADO`; cancelar libera el hueco y dispara la lista de espera.
- [ ] La cancelación respeta el límite de horas (RN-TU-03).

**Reglas relacionadas**: RN-TK-01, RN-TK-03, RN-TK-04, RN-NO-04, RN-NO-08, RN-TU-03

## Épica 5: Lista de espera (M)

### US-040 — Anotarse en la lista de espera (página pública)
**Como** paciente (sin cuenta)
**Quiero** anotarme para una prestación indicando rango de fechas y franja
**Para** que me avisen si se libera un turno antes

**Criterios de aceptación**:
- [ ] Ingreso nombre, DNI, email y teléfono.
- [ ] La entrada queda pendiente hasta confirmar el enlace del email; luego pasa a `ACTIVA`.
- [ ] Puedo retirarme desde un enlace de los emails.

**Reglas relacionadas**: RN-PU-01, RN-PU-05, RN-LE-02, RN-LE-09, RN-LE-11

### US-041 — Aviso de hueco liberado y aceptación
**Como** paciente en lista de espera
**Quiero** recibir un email cuando se libera un turno compatible
**Para** aceptarlo rápidamente con un enlace

**Criterios de aceptación**:
- [ ] Todos los pacientes compatibles reciben el aviso a la vez, cada uno con un token único de un solo uso y con vencimiento.
- [ ] El primero en aceptar obtiene el turno; los demás ven que ya no está disponible.
- [ ] El aviso vence según la configuración.
- [ ] No hace falta login.

**Reglas relacionadas**: RN-LE-01, RN-LE-03, RN-LE-04, RN-LE-05, RN-LE-06, RN-LE-08, RN-TK-01

### US-042 — Gestionar lista de espera
**Como** odontólogo
**Quiero** ver, agregar y quitar pacientes de la lista de espera
**Para** administrar pedidos recibidos por teléfono o por la página pública

**Criterios de aceptación**:
- [ ] Listado filtrable por prestación y estado.
- [ ] Puedo anotar a un paciente en su nombre (nace `ACTIVA`).

**Reglas relacionadas**: RN-LE-02, RN-LE-09, RN-LE-11, RN-PU-05

## Épica 6: Recordatorios y notificaciones (M)

### US-050 — Recordatorio automático por email
**Como** consultorio
**Quiero** enviar un recordatorio 24 horas antes de cada turno
**Para** reducir el ausentismo

**Criterios de aceptación**:
- [ ] Un recordatorio por turno, sin duplicados aunque el proceso se reinicie (`@Scheduled` idempotente).
- [ ] Incluye enlaces para confirmar y cancelar.
- [ ] No contiene datos clínicos.

**Reglas relacionadas**: RN-NO-02, RN-NO-03, RN-NO-04, RN-NO-05, RN-NO-09

### US-051 — Registro de envíos
**Como** odontólogo
**Quiero** ver qué notificaciones se enviaron o fallaron
**Para** detectar problemas de entrega

**Criterios de aceptación**:
- [ ] Listado con estado, destinatario y error.
- [ ] Los fallos se reintentan con límite.

**Reglas relacionadas**: RN-NO-06

## Épica 7: Ausentismo (M)

### US-060 — Marcar turno como ausente
**Como** odontólogo
**Quiero** marcar un turno como ausente
**Para** llevar el control de no presentaciones

**Criterios de aceptación**:
- [ ] Solo se puede marcar tras la hora de inicio más la tolerancia.
- [ ] Se incrementa el contador de ausencias del paciente.
- [ ] Se envía un email invitando a reprogramar.

**Reglas relacionadas**: RN-AS-01, RN-AS-02, RN-AS-03, RN-AS-06

### US-061 — Historial de ausencias
**Como** odontólogo
**Quiero** ver las ausencias de un paciente
**Para** tener contexto al agendarlo

**Criterios de aceptación**:
- [ ] La ficha muestra cantidad y fechas de ausencias.

**Reglas relacionadas**: RN-AS-02, RN-AS-05

## Épica 8: Historia clínica y odontograma (M) — Si llega (stretch)

> **Si llega (stretch)** para la entrega del 2026-11-25 (DD-16): US-070, US-071 y US-072 no están comprometidas; si no se entregan quedan como etapa posterior.

### US-070 — Ficha y anamnesis (si llega, stretch)
**Como** odontólogo
**Quiero** registrar la anamnesis básica del paciente
**Para** conocer alergias, medicación y antecedentes

**Criterios de aceptación**:
- [ ] Se puede crear y editar la anamnesis.
- [ ] Solo accede el odontólogo con sesión.

**Reglas relacionadas**: RN-HC-01, RN-HC-02

### US-071 — Odontograma FDI (si llega, stretch)
**Como** odontólogo
**Quiero** registrar el estado de cada pieza dental en un odontograma
**Para** reflejar la condición bucal del paciente

**Criterios de aceptación**:
- [ ] Se muestran las 32 piezas permanentes (numeración FDI).
- [ ] Puedo asignar un estado y una observación por pieza.
- [ ] Cada cambio registra autor y fecha.

**Reglas relacionadas**: RN-HC-03

### US-072 — Notas de evolución (si llega, stretch)
**Como** odontólogo
**Quiero** registrar una nota de evolución por consulta
**Para** dejar constancia del tratamiento realizado

**Criterios de aceptación**:
- [ ] La nota queda asociada al paciente, al autor y opcionalmente al turno.
- [ ] El historial se lista en orden cronológico.

**Reglas relacionadas**: RN-HC-04, RN-HC-06

## Épica 9: Etapas posteriores (P)

| ID | Historia | Origen |
|----|----------|--------|
| US-100 | Seña con Mercado Pago que bloquea el horario | Informe de mercado, MVP sugerido |
| US-101 | Facturación ARCA al registrar el cobro | RG 5866/2026 |
| US-102 | Ficha de obra social y validación/liquidación | Informe de mercado |
| US-103 | Notificaciones por WhatsApp Business API (ver [12](12_notificaciones.md)) | Estándar de mercado |
| US-104 | Campañas de recall con enlace de reserva | Informe de mercado |
| US-105 | Recepcionista con IA en WhatsApp | Commodity declarado |
| US-106 | Plan de tratamiento y presupuestos | Estándar de mercado |
| US-107 | Adjuntos de radiografías y consentimientos | Estándar de mercado |
| US-108 | HC *append-only*, auditoría de accesos, exportación al paciente en 48 h | Ley 26.529 art. 13, 14 |
| US-109 | Periodontograma | Diferenciador |
| US-110 | Interoperabilidad FHIR (Ley 27.706) | Ley 27.706 |
| US-111 | Receta electrónica integrando plataforma registrada en ReNaPDiS | Ley 27.553 |
| US-112 | Políticas por reincidencia de ausencias (seña, bloqueo) | Ausentismo |
| US-113 | Anti-abuso reforzado en la página pública (CAPTCHA u OTP) | Referencia de UX (Dentally) |
| US-114 | Varios odontólogos y multi-consultorio (agregar `usuario_id` a turno y roles) | Crecimiento del modelo `Consultorio` |
| US-115 | Despliegue público (hosting, scheduler persistente, HTTPS) | Etapa posterior (DD-11) |
| US-116 | Recuperación de contraseña del odontólogo por email | Seguridad |
