import { createAgendaRoutes, createDefaultAgendaDeps, type AgendaDeps } from './features/agenda';
import { login, logout, me, type SessionUser } from './features/auth/auth-api';
import { createConfigRoutes, createDefaultConfigSections, type ConfigSections } from './features/config';
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

  const routes: readonly Route[] = [
    {
      path: '/',
      access: 'public',
      render: (_container, context) => context.navigate(session.get().status === 'authenticated' ? '/agenda' : '/login'),
    },
    { path: '/login', access: 'public', render: createLoginPage({ session, login: deps.login }) },
    placeholderRoute('/publico', 'public', 'Solicitud de turno'),
    ...createAgendaRoutes(deps.agenda ?? createDefaultAgendaDeps()),
    placeholderRoute('/pacientes', 'private', 'Pacientes'),
    ...createConfigRoutes(deps.configSections ?? createDefaultConfigSections()),
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
