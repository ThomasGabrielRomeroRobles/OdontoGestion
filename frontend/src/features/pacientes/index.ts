import type { Route } from '../../shared/router/router';
import { pacientesApi, type PacientesApi } from './pacientes-api';
import { createPacienteDetallePage, createPacientesListPage } from './pacientes-page';

export type { PacientesApi } from './pacientes-api';

/** Rutas privadas `/pacientes` (listado) y `/pacientes/:id` (ficha). Por defecto, conectadas a la API real. */
export function createPacientesRoutes(api: PacientesApi = pacientesApi): readonly Route[] {
  const lista = createPacientesListPage(api);
  const detalle = createPacienteDetallePage(api);
  return [
    { path: '/pacientes', access: 'private', render: (container) => lista(container) },
    { path: '/pacientes/:id', access: 'private', render: (container, context) => detalle(container, context.params['id'] ?? '') },
  ];
}
