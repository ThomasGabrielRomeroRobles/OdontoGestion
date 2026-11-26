import { createAgendaRoutes, createDefaultAgendaDeps, type AgendaDeps } from './features/agenda';
import { login, logout, me, type SessionUser } from './features/auth/auth-api';
import { createAccionesRoute, type AccionesApi } from './features/acciones-token';
import { createHistoriaRoute, type HistoriaApi } from './features/historia-clinica';
import { createConfigRoutes, createDefaultConfigSections, type ConfigSections } from './features/config';
import { createListaEsperaRoutes, type ListaEsperaApi } from './features/lista-espera';
import { createNotificacionesRoute, type NotificacionesApi } from './features/notificaciones';
import { createPublicoRoute, type PublicoApi } from './features/publico';
import { createLoginPage } from './pages/login-page';
import { placeholderRoute, renderNotFound } from './pages/placeholder-pages';
import { configureHttpClient } from './shared/http/client';
import { createAppLayout } from './shared/layout/app-layout';
import { createRouter, loginUrl, type Route, type Router } from './shared/router/router';
import { session as sharedSession, type SessionStore } from './shared/state/session';

export interface BootstrapDeps {
  readonly root: HTMLElement;
  readonly session: SessionStore;
  readonly me: () => Promise<SessionUser | null>;
  readonly login: (email: string, password: string) => Promise<SessionUser>;
  readonly logout: () => Promise<void>;
  /** Secciones de `/configuracion`; los tests inyectan dobles. Por defecto, las conectadas a la API. */
  readonly configSections?: ConfigSections;
  /** Dependencias de la agenda; por defecto, la API real (C-08, design D2). */
  readonly agenda?: AgendaDeps;
  /** API del registro de envíos (`/notificaciones`); los tests inyectan un doble. Por defecto, la real. */
  readonly notificacionesApi?: NotificacionesApi;
  /** API pública de solicitud de turno (`/publico`); los tests inyectan un doble. Por defecto, la real. */
  readonly publicoApi?: PublicoApi;
  /** API de las acciones por token (`/acciones/:token`); los tests inyectan un doble. Por defecto, la real. */
  readonly accionesApi?: AccionesApi;
  /** API de la lista de espera (`/lista-espera`, `/avisos/:token`, `/publico/lista-espera`); los tests inyectan un doble. */
  readonly listaEsperaApi?: ListaEsperaApi;
  /** API de la historia clínica (`/pacientes/:id/historia`, C-05); los tests inyectan un doble. Por defecto, la real. */
  readonly historiaApi?: HistoriaApi;
}

export interface App {
  readonly router: Router;
  readonly stop: () => void;
}

export function defaultDeps(root: HTMLElement): BootstrapDeps {
  return { root, session: sharedSession, me, login, logout };
}

/** Arranque: resuelve la sesión, conecta el 401 del cliente HTTP al estado y al router, y renderiza. */
export async function bootstrap(deps: BootstrapDeps): Promise<App> {
  const { root, session } = deps;

  try {
    const user = await deps.me();
    if (user === null) session.clear();
    else session.setAuthenticated(user.email);
  } catch {
    session.clear();
  }

  // `/publico/lista-espera` lista las mismas prestaciones que `/publico` (mismo cliente inyectado).
  const publico = deps.publicoApi;
  const routes: readonly Route[] = [
    {
      path: '/',
      access: 'public',
      render: (_container, context) => context.navigate(session.get().status === 'authenticated' ? '/agenda' : '/login'),
    },
    { path: '/login', access: 'public', render: createLoginPage({ session, login: deps.login }) },
    createPublicoRoute(deps.publicoApi),
    createAccionesRoute(deps.accionesApi),
    ...createListaEsperaRoutes(deps.listaEsperaApi, publico === undefined ? undefined : () => publico.listarPrestaciones()),
    ...createAgendaRoutes(deps.agenda ?? createDefaultAgendaDeps()),
    createHistoriaRoute(deps.historiaApi),
    placeholderRoute('/pacientes', 'private', 'Pacientes'),
    ...createConfigRoutes(deps.configSections ?? createDefaultConfigSections()),
    createNotificacionesRoute(deps.notificacionesApi),
  ];

  const router: Router = createRouter({
    root,
    session,
    routes,
    notFound: renderNotFound,
    layout: createAppLayout({ session, logout: deps.logout, navigate: (to) => router.navigate(to) }),
  });

  configureHttpClient({
    onUnauthorized: (currentPath) => {
      session.clear();
      router.navigate(loginUrl(currentPath), 'replace');
    },
  });

  return { router, stop: router.start() };
}
