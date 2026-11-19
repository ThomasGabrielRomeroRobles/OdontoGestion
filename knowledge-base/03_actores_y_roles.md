# Actores y Roles

## Actores del sistema

| Actor | Descripción | Cómo interactúa |
|-------|-------------|-----------------|
| Odontólogo | Único profesional del consultorio y **único usuario con login** | Inicia sesión; configura el consultorio (prestaciones, horarios, bloqueos, parámetros); gestiona la agenda y la lista de espera; marca ausentes; registra anamnesis, odontograma y evolución; atiende y finaliza turnos |
| Paciente | Persona atendida; **sin cuenta ni login** | Usa la página pública para pedir turno o anotarse en la lista de espera (nombre, DNI, email, teléfono); confirma, cancela o acepta un hueco con enlaces de email (token único) |
| Sistema (scheduler) | Proceso interno (`@Scheduled`) | Envía recordatorios, vence solicitudes pendientes, avisos y entradas, procesa notificaciones pendientes |
| Servicio de email (SMTP) | Sistema externo | Entrega los correos |

No existen los roles Recepcionista ni Administrador: el odontólogo realiza todas esas tareas.

## Modelo de acceso (sin RBAC multi-rol)

El sistema distingue tres niveles de acceso, no cuatro roles:

| Nivel | Cómo se identifica | Qué puede hacer |
|-------|--------------------|-----------------|
| Odontólogo autenticado | Email + contraseña; sesión de servidor (cookie) con CSRF | Todo lo autenticado de la tabla siguiente |
| Paciente por token | Enlace de email con token único (por turno o por aviso), con vencimiento y de un solo uso | Solo la acción para la que se emitió el token (confirmar solicitud, confirmar turno, cancelar turno, aceptar hueco) |
| Visitante anónimo (página pública) | Sin identificación; limitación de tasa | Ver horarios libres, solicitar turno, anotarse en la lista de espera |

## Matriz de permisos

Leyenda: C = crear, L = leer, A = actualizar, B = baja/cancelar, `—` = sin acceso.

| Recurso | Odontólogo (sesión) | Paciente por token | Visitante público |
|---------|---------------------|--------------------|-------------------|
| Cuenta propia | L, A (contraseña) | — (no existe) | — |
| Turnos | C, L, A (mover), B, marcar ausente / atendido | Confirmar y cancelar **solo el turno del token** | C (solicitud, queda pendiente de confirmación) |
| Disponibilidad (huecos libres) | L | — | L (solo horarios libres, sin datos de otros pacientes) |
| Lista de espera | C, L, B | Aceptar el hueco del aviso del token | C (queda pendiente de confirmación por email) |
| Pacientes (ficha administrativa) | C, L, A | — | — (se crea/vincula por DNI al solicitar; ver RN-PU-04) |
| Historia clínica / anamnesis | C, L, A | — | — |
| Odontograma y evolución | C, L, A | — | — |
| Prestaciones (tipos de turno) | C, L, A, B | — | L (nombre y duración, para elegir) |
| Horarios y bloqueos de agenda | C, L, A, B | — | — |
| Configuración del consultorio | L, A | — | — |
| Historial de ausencias del paciente | L | — | — |
| Registro de notificaciones | L | — | — |

Notas:

- **Mínimo privilegio sobre datos de salud:** la historia clínica solo es accesible con la sesión del odontólogo; ningún token ni la página pública acceden a datos clínicos (RN-HC-02).
- El odontólogo se crea por **seed / bootstrap** (no hay registro público de usuarios; RN-AU-05).

## Rutas públicas

Accesibles sin autenticación:

| Ruta | Propósito |
|------|-----------|
| `/` (landing / página pública) | Información del consultorio, horarios libres, solicitud de turno y alta en lista de espera (nombre, DNI, email, teléfono) |
| `/login` | Inicio de sesión del odontólogo |
| `/acciones/{token}` | Confirmar la solicitud, confirmar o cancelar un turno desde el email (token de un solo propósito y un solo uso; el `GET` solo muestra, el `POST` ejecuta) |
| `/avisos/{token}` | Ver y aceptar un hueco ofrecido desde la lista de espera (sin login; el token identifica al paciente y al aviso) |

**Importante:** los pacientes nunca inician sesión. Las rutas por token solo permiten la acción específica para la que se emitieron. Todo lo demás (`/agenda`, `/pacientes`, `/historia`, `/config`, `/api/v1/**` salvo `publico`, `acciones` y `avisos`) exige sesión del odontólogo.
