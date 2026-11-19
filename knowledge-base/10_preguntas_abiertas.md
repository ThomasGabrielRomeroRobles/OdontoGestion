# Preguntas Abiertas

## Inconsistencias detectadas

### IN-01 — Plazo de entrega vs. alcance del MVP (RESUELTA 2026-11-19, ver DD-16)
**Alcance dice**: MVP con 8 épicas (acceso, configuración, agenda, página pública, lista de espera, recordatorios, ausentismo, HC con odontograma).
**Calendario dice**: entrega el 2026-11-25, siete días después de la fecha de esta documentación (2026-11-18).
**Impacto**: riesgo alto de no completar el MVP completo.
**Resolución (2026-11-19, DD-16)**: corte por valor. Obligatorio para el 2026-11-25: épicas 1-7 (11 changes core, C-01 a C-12 sin C-05). La historia clínica con odontograma (épica 8, C-05) es "si llega" (stretch); si no se entrega se documenta como "etapa posterior". Las correcciones de alcance (un solo odontólogo, sin sillones, sin cuentas de paciente) ya reducen el trabajo.

### IN-02 — Historia clínica del MVP vs. exigencias de la Ley 26.529
**El MVP**: HC editable con autor y fecha.
**El marco legal ([11](11_contexto_de_mercado.md))**: exige inalterabilidad y control de modificaciones.
**Impacto**: el MVP no sería apto para uso real con pacientes.
**Resolución propuesta**: mantener datos ficticios (RN-GL-01) y declarar la brecha; planificar HC *append-only* como etapa posterior.

### IN-03 — Identidad del paciente sin login vs. datos personales sensibles
**La página pública**: pide DNI, email y teléfono a un visitante anónimo, sin verificación fuerte de identidad.
**El marco legal ([11](11_contexto_de_mercado.md))**: la Ley 25.326 exige consentimiento y medidas de seguridad sobre datos personales.
**Impacto**: cualquiera puede ingresar el DNI o email de un tercero; el consentimiento no está modelado.
**Resolución propuesta**: mitigar con confirmación por email y no sobrescribir datos de pacientes existentes (RN-PU-04); usar solo datos ficticios en el MVP (RN-GL-01) y tratar consentimiento y verificación fuerte como requisitos futuros.

## Preguntas abiertas (priorizadas)

| ID | Prioridad | Pregunta | Bloquea | Decisor |
|----|-----------|----------|---------|---------|
| PA-03 | Media | Si un DNI ya existe con otro email, ¿a qué email se envían los enlaces y cómo se actualiza el contacto? (Suposición: se usa el email registrado y no se sobrescribe; ver RN-PU-04) | Página pública, RN-PU-04 | Equipo |
| PA-04 | Media | ¿Cómo recupera su contraseña el odontólogo (único usuario, sin administrador)? Opciones: enlace por email, o reinicio manual por seed/variable de entorno. | Épica 1 (US-116) | Equipo |
| PA-05 | Media | Valores por defecto de reglas: tolerancia de ausente (15 min), límite de cancelación (2 h), vencimiento de aviso (60 min), anticipación mínima (2 h), retención de solicitud pública (30 min). ¿Se confirman? | Reglas de turnos, página pública y lista de espera | Product Owner (docente/equipo) |
| PA-06 | Media | ¿Los turnos `ATENDIDO` y `AUSENTE` deben seguir ocupando el rango en la restricción de exclusión? | Modelo de turno | Equipo técnico |
| PA-07 | Media | ¿Todas las máquinas del equipo pueden ejecutar Docker para Testcontainers y tienen `btree_gist` disponible en su PostgreSQL local? (SU-07, SU-11) | Scaffolding, TDD | Equipo técnico |
| PA-08 | Media | ¿Existe alguna política por reincidencia de ausencias (aviso, seña, bloqueo), por ejemplo restringir solicitudes públicas de un DNI con muchas ausencias? | Etapa posterior (US-112) | Equipo |
| PA-09 | Baja | Estados de pieza dental y alcance del odontograma (por pieza vs. por cara). ¿Se valida con un odontólogo? | Épica 8 | Equipo / odontólogo asesor |
| PA-10 | Baja | ¿Hay línea base de ausentismo para medir el impacto? (no existe tasa sólida en Argentina) | Métricas | Equipo |
| PA-11 | Baja | ¿Qué costo y plan de WhatsApp Business API se usaría luego (validar rate card oficial de Meta)? | Etapa posterior | Equipo |
| PA-12 | Baja | Validación legal de requisitos futuros (Leyes 25.326, 26.529, 27.706), incluido el consentimiento por la recolección de datos en la página pública. | Uso real | Docente / asesor legal |
| PA-13 | Baja | ¿Cuánto tiempo se conservan las solicitudes y entradas de lista de espera nunca confirmadas y los pacientes creados por ellas (depuración)? | Datos personales, modelo | Equipo |

## Cuestiones resueltas (referencia)

Resueltas por las correcciones del equipo y ya incorporadas a la KB: hosting y despliegue (demo local, DD-11), proveedor SMTP (cuenta real por variables de entorno, SU-10), scheduler en hosting gratuito (`@Scheduled` local, DD-13), cómo se sirve el frontend (DD-12), acceso del administrador a datos clínicos y odontólogo-administrador (no hay rol administrador, DD-01), vinculación ficha-cuenta (los pacientes no tienen cuenta, DD-08) y recuperación de contraseña de pacientes (no hay login de pacientes).

Resueltas el 2026-11-19 por decisión del equipo:

| ID | Decisión | Referencia |
|----|----------|------------|
| IN-01 / PA-01 | **Corte por valor**: obligatorio para el 2026-11-25 los 11 changes core (C-01, C-02, C-03, C-04, C-06, C-07, C-08, C-09, C-10, C-11, C-12: épicas 1-7); "si llega" (stretch) C-05 historia clínica (épica 8). Lo no entregado se documenta como etapa posterior. | DD-16, SU-08 |
| PA-02 | **Anti-abuso confirmado**: la solicitud pública crea turno/entrada de espera `PENDIENTE_CONFIRMACION`, retiene el hueco 30 min y pasa a `RESERVADO`/`ACTIVA` solo tras confirmar por enlace de token de un solo uso enviado por email; límite de 5 solicitudes/hora por IP y máximo 2 pendientes por DNI o email (429 al exceder); campo señuelo (honeypot) y validación estricta de formato; respuestas sin enumeración. CAPTCHA/OTP queda como mejora posterior (US-113). | DD-14, SU-09, RN-PU-02/03/06 |
