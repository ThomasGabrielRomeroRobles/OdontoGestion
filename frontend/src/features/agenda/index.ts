import './agenda.css';
import { listarPrestaciones } from '../config/agenda-config-api';
import { obtenerConsultorio } from '../config/config-api';
import type { Route } from '../../shared/router/router';
import { agendaApi } from './agenda-api';
import type { AgendaDeps } from './deps';
import { pacientesApi } from './pacientes-api';
import { createAgendaPage } from './pages/agenda-page';
import { createHoyPage } from './pages/hoy-page';
import { createMoverTurno } from './pages/mover-turno';
import { createNuevoTurno } from './pages/nuevo-turno';
import { createTurnoDetalle } from './pages/turno-detalle';

export type { AgendaDeps } from './deps';
export { crearAusencias } from './pages/ausencias';

/** Rutas privadas de la agenda (design D4): `/agenda` y `/agenda/hoy`, con paneles sobre las mismas deps. */
export function createAgendaRoutes(deps: AgendaDeps): Route[] {
  const detalle = createTurnoDetalle(deps, { mover: createMoverTurno(deps) });
  const nuevoTurno = createNuevoTurno(deps);
  return [
    { path: '/agenda', access: 'private', render: createAgendaPage(deps, { detalle, nuevoTurno }) },
    { path: '/agenda/hoy', access: 'private', render: createHoyPage(deps, detalle) },
  ];
}

/** Dependencias reales: API REST, confirmación del navegador y reloj del sistema. */
export function createDefaultAgendaDeps(): AgendaDeps {
  return {
    api: agendaApi,
    pacientes: pacientesApi,
    listarPrestaciones,
    obtenerConsultorio,
    confirmar: (mensaje) => window.confirm(mensaje),
    ahora: () => new Date(),
  };
}
