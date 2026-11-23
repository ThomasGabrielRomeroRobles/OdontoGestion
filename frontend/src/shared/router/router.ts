import type { SessionStore } from '../state/session';

export type RouteAccess = 'public' | 'private';

export interface RouteContext {
  readonly url: URL;
  /** Valores de los segmentos variables del `path` (`/acciones/:token` → `{ token }`); `{}` en rutas exactas. */
  readonly params: Readonly<Record<string, string>>;
  navigate(to: string): void;
}

export interface Route {
  /** Ruta exacta (`/agenda`) o con segmentos variables (`/acciones/:token`). */
  readonly path: string;
  readonly access: RouteAccess;
  render(container: HTMLElement, context: RouteContext): void;
}

export interface AuthenticatedSession {
  readonly status: 'authenticated';
  readonly email: string;
}

export interface RouterOptions {
  readonly root: HTMLElement;
  readonly session: SessionStore;
  readonly routes: readonly Route[];
  readonly notFound: (container: HTMLElement, context: RouteContext) => void;
  /** Arma el marco autenticado dentro de `host` y devuelve el elemento donde va la vista. */
  readonly layout: (host: HTMLElement, session: AuthenticatedSession) => HTMLElement;
}

export interface Router {
  /** Renderiza la URL actual y empieza a escuchar clicks y popstate. Devuelve la función de parada. */
  start(): () => void;
  navigate(to: string, mode?: 'push' | 'replace'): void;
}

/**
 * Coincidencia por segmentos: `:nombre` captura exactamente un segmento no vacío (decodificado). Devuelve los
 * parámetros, o `null` si la ruta no coincide (incluida una codificación porcentual inválida).
 */
export function matchPath(pattern: string, pathname: string): Readonly<Record<string, string>> | null {
  if (pattern === pathname) return {};
  const patternSegments = pattern.split('/');
  const pathSegments = pathname.split('/');
  if (patternSegments.length !== pathSegments.length) return null;
  const params: Record<string, string> = {};
  for (let i = 0; i < patternSegments.length; i += 1) {
    const expected = patternSegments[i] ?? '';
    const actual = pathSegments[i] ?? '';
    if (expected.startsWith(':') && expected.length > 1) {
      if (actual === '') return null;
      try {
        params[expected.slice(1)] = decodeURIComponent(actual);
      } catch {
        return null;
      }
    } else if (expected !== actual) {
      return null;
    }
  }
  return params;
}

export function loginUrl(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`;
}

export function createRouter(options: RouterOptions): Router {
  const { root, session, routes, notFound, layout } = options;

  function currentUrl(): URL {
    return new URL(window.location.href);
  }

  function context(url: URL, params: Readonly<Record<string, string>>): RouteContext {
    return { url, params, navigate: (to) => navigate(to) };
  }

  /** Las rutas exactas tienen prioridad sobre las que tienen segmentos variables. */
  function resolve(pathname: string): { route: Route; params: Readonly<Record<string, string>> } | undefined {
    const exact = routes.find((candidate) => candidate.path === pathname);
    if (exact !== undefined) return { route: exact, params: {} };
    for (const candidate of routes) {
      const params = matchPath(candidate.path, pathname);
      if (params !== null) return { route: candidate, params };
    }
    return undefined;
  }

  function render(): void {
    const url = currentUrl();
    const resolved = resolve(url.pathname);
    const route = resolved?.route;
    const state = session.get();

    if (route?.access === 'private' && state.status !== 'authenticated') {
      navigate(loginUrl(`${url.pathname}${url.search}`), 'replace');
      return;
    }

    const ctx = context(url, resolved?.params ?? {});
    const framed = route === undefined || route.access === 'private';
    let outlet: HTMLElement = root;
    if (framed && state.status === 'authenticated') {
      outlet = layout(root, state);
    } else {
      root.replaceChildren();
    }
    if (route === undefined) notFound(outlet, ctx);
    else route.render(outlet, ctx);
  }

  function navigate(to: string, mode: 'push' | 'replace' = 'push'): void {
    if (mode === 'replace') window.history.replaceState(null, '', to);
    else window.history.pushState(null, '', to);
    render();
  }

  function onClick(event: MouseEvent): void {
    if (event.defaultPrevented || event.button !== 0) return;
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const target = event.target;
    if (!(target instanceof Element)) return;
    const anchor = target.closest<HTMLAnchorElement>('a[data-link]');
    if (anchor === null || (anchor.target !== '' && anchor.target !== '_self')) return;
    const url = new URL(anchor.href, window.location.href);
    if (url.origin !== window.location.origin) return;
    event.preventDefault();
    navigate(`${url.pathname}${url.search}${url.hash}`);
  }

  function onPopState(): void {
    render();
  }

  return {
    navigate,
    start() {
      root.addEventListener('click', onClick);
      window.addEventListener('popstate', onPopState);
      render();
      return () => {
        root.removeEventListener('click', onClick);
        window.removeEventListener('popstate', onPopState);
      };
    },
  };
}
