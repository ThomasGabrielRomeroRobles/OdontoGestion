import type { Route } from '../../shared/router/router';
import { publicoApi, type PublicoApi } from './publico-api';
import { createPublicoPage } from './publico-page';

export type { PublicoApi } from './publico-api';

/** Ruta pública `/publico` (C-09): sin sesión ni layout del odontólogo. Por defecto, conectada a la API real. */
export function createPublicoRoute(api: PublicoApi = publicoApi): Route {
  const render = createPublicoPage(api);
  return { path: '/publico', access: 'public', render: (container) => render(container) };
}
