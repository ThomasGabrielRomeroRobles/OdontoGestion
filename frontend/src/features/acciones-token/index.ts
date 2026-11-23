import type { Route } from '../../shared/router/router';
import { accionesApi, type AccionesApi } from './acciones-api';
import { createAccionesPage } from './acciones-page';

export type { AccionesApi } from './acciones-api';

/** Ruta pública `/acciones/:token` (C-09): sin sesión ni layout del odontólogo. Por defecto, conectada a la API real. */
export function createAccionesRoute(api: AccionesApi = accionesApi): Route {
  const render = createAccionesPage(api);
  return {
    path: '/acciones/:token',
    access: 'public',
    render: (container, context) => {
      const token = context.params['token'];
      if (token !== undefined) render(container, token);
    },
  };
}
