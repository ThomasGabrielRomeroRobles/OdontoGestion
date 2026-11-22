import type { Route } from '../../shared/router/router';
import { notificacionesApi, type NotificacionesApi } from './notificaciones-api';
import { createNotificacionesPage } from './notificaciones-page';

export type { NotificacionesApi } from './notificaciones-api';

/** Ruta privada `/notificaciones` (registro de envíos, C-03). Por defecto, conectada a la API real. */
export function createNotificacionesRoute(api: NotificacionesApi = notificacionesApi): Route {
  const render = createNotificacionesPage(api);
  return { path: '/notificaciones', access: 'private', render: (container) => render(container) };
}
