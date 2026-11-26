import './historia-clinica.css';
import type { Route } from '../../shared/router/router';
import { historiaApi, type HistoriaApi } from './historia-api';
import { createHistoriaPage } from './historia-page';

export type { HistoriaApi } from './historia-api';

/** Ruta privada `/pacientes/:id/historia` (C-05). Por defecto, conectada a la API real. */
export function createHistoriaRoute(api: HistoriaApi = historiaApi, ahora: () => Date = () => new Date()): Route {
  const render = createHistoriaPage(api, ahora);
  return {
    path: '/pacientes/:id/historia',
    access: 'private',
    render: (container, context) => render(container, context.params['id']),
  };
}
