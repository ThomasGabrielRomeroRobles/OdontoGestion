# Proposal

## Why

El ausentismo es uno de los dos problemas que ataca OdontoGestión, y hoy el odontólogo no puede registrarlo: la máquina de estados de C-07 ya admite `AUSENTE`, pero no hay caso de uso, ni contador, ni historial, ni aviso al paciente (US-060, US-061, RN-AS-01…06, Flujo 6). Además, el frontend de C-08 (P5) ya está en `main` contra un contrato provisional y su integración (tarea 9 de `c-08-agenda-frontend-ausentismo`) está bloqueada esperando este backend. Es la mitad backend de C-08 y está asignada a P3.

## What Changes

- **Marcar ausente** — `POST /api/v1/turnos/{id}/ausente`: un turno `RESERVADO` o `CONFIRMADO` pasa a `AUSENTE` solo cuando `ahora >= inicio + tolerancia_ausente_min` del consultorio (`422` antes; `422` por transición inválida; `404` si no existe o es de otro consultorio). Registra el instante de la marca y el estado previo, suma uno al contador de ausencias del paciente, emite un evento `TurnoAusente` y encola una notificación `AUSENTE_REPROGRAMAR` (plantilla ya existente de C-03, con enlace a la página pública). El turno sigue ocupando su rango (RN-AS-04, PA-06).
- **Revertir ausencia** — `POST /api/v1/turnos/{id}/revertir-ausencia`: dentro de las 24 h desde la marca (RN-AS-06), el turno vuelve exactamente a su estado previo (`RESERVADO` o `CONFIRMADO`), el contador del paciente baja en uno y la notificación `AUSENTE_REPROGRAMAR` que todavía no se envió se descarta. `422` fuera de plazo o si el turno no está `AUSENTE`; `404` si no existe.
- **Historial de ausencias** — `GET /api/v1/pacientes/{id}/ausencias` → `{cantidad, ausencias: [{turnoId, inicio, prestacionNombre}]}`, la más reciente primero (US-061). `404` si el paciente no existe o es de otro consultorio.
- **DTO del turno** (`TurnoDto`, usado por la agenda y las acciones): se agregan `ausenteMarcadoEn` (instante o `null`) y `paciente.cantidadAusencias`. Cambio aditivo, no rompe a los consumidores actuales. **No** se agrega el DNI que el contrato v0 del frontend incluía: `TurnoDto` lo excluye a propósito (regla dura #10); P5 lo ajusta en su tarea 9.1.
- **Migración V11** (`turno`): columnas `ausente_marcado_en timestamptz` y `estado_previo_ausencia text`, con `CHECK` de coherencia con el estado. Es la próxima versión libre (V1–V10 aplicadas).
- **Máquina de estados**: `AUSENTE` deja de ser final en sentido estricto: admite una única salida, la reversión, hacia el estado previo guardado y dentro de la ventana de 24 h.

Fuera de alcance: políticas por reincidencia (PA-08, US-112: las ausencias no bloquean reservas, RN-AS-05), estadísticas de ausentismo, enviar un aviso al paciente cuando se revierte una marca, y cualquier cambio de frontend (lo integra P5).

## Capabilities

### New Capabilities
- `patient-absenteeism`: registro de ausencias de pacientes a sus turnos: marcar ausente con tolerancia, revertir dentro de 24 h, contador e historial de ausencias del paciente y aviso para reprogramar.

### Modified Capabilities
- `turno-lifecycle`: la tabla de estados admite la reversión `AUSENTE → RESERVADO | CONFIRMADO` (solo hacia el estado previo y dentro de 24 h); los eventos de cambios de agenda suman `TurnoAusente`; las notificaciones por cambios del turno suman `AUSENTE_REPROGRAMAR`; el turno expone `ausenteMarcadoEn` y la cantidad de ausencias del paciente.

## Impact

- **Base de datos**: `V11__ausencia_turno.sql` (dos columnas nuevas en `turno` y un `CHECK`). Sin tablas nuevas: el historial se obtiene de los turnos `AUSENTE` del paciente.
- **Backend**: `Turno` (marcar con instante y estado previo, revertir), `EstadoTurno` (reversión), `GestionTurnoService`/`TurnoComandos` (dos comandos nuevos), `TurnoController` (dos endpoints), `TurnoDto`, `PacienteRepository` (incremento y decremento atómicos del contador), nuevo servicio y endpoint de ausencias del paciente, evento `TurnoAusente`, `NotificacionesTurnoListener` (encola `AUSENTE_REPROGRAMAR`), `NotificationService` (descartar la pendiente al revertir).
- **API**: dos `POST` nuevos en `/api/v1/turnos/{id}/…` y un `GET` en `/api/v1/pacientes/{id}/ausencias`, todos privados (sesión; los `POST` con CSRF, ya cubierto por C-02).
- **Consumidores**: el frontend de C-08 (P5, tarea 9 de su change) y C-12. Coordinar con Juan Ignacio el contrato final (DNI fuera del DTO).
- **Governance**: MEDIO.
