import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createSessionStore, type SessionStore } from '../state/session';
import { createRouter, type Route, type Router } from './router';

function textRoute(path: string, access: Route['access'], text: string): Route {
  return {
    path,
    access,
    render(container) {
      container.textContent = text;
    },
  };
}

describe('router', () => {
  let root: HTMLElement;
  let session: SessionStore;
  let router: Router;
  let stop: () => void;

  function build(): void {
    router = createRouter({
      root,
      session,
      routes: [
        textRoute('/login', 'public', 'VISTA-LOGIN'),
        textRoute('/publico', 'public', 'VISTA-PUBLICA'),
        textRoute('/agenda', 'private', 'VISTA-AGENDA'),
        textRoute('/pacientes', 'private', 'VISTA-PACIENTES'),
      ],
      notFound: (container) => {
        container.textContent = 'PAGINA-NO-ENCONTRADA';
      },
      layout: (host, state) => {
        host.replaceChildren();
        const frame = document.createElement('div');
        frame.dataset['layout'] = state.email;
        const main = document.createElement('main');
        frame.append(main);
        host.append(frame);
        return main;
      },
    });
  }

  beforeEach(() => {
    document.body.replaceChildren();
    root = document.createElement('div');
    document.body.append(root);
    session = createSessionStore();
    session.clear();
    history.replaceState(null, '', '/');
    build();
  });
  afterEach(() => stop?.());

  it('ruta privada sin sesión redirige a /login con next y no renderiza la vista privada', () => {
    stop = router.start();
    router.navigate('/agenda');

    expect(location.pathname).toBe('/login');
    expect(location.search).toBe('?next=%2Fagenda');
    expect(root.textContent).toBe('VISTA-LOGIN');
    expect(root.textContent).not.toContain('VISTA-AGENDA');
  });

  it('el next conserva la query de la ruta privada original', () => {
    stop = router.start();
    router.navigate('/pacientes?q=ana');
    expect(location.search).toBe('?next=%2Fpacientes%3Fq%3Dana');
  });

  it('ruta privada con sesión se renderiza dentro del layout autenticado', () => {
    session.setAuthenticated('odontologo@consultorio.example');
    stop = router.start();
    router.navigate('/agenda');

    expect(location.pathname).toBe('/agenda');
    expect(root.querySelector('[data-layout="odontologo@consultorio.example"] main')?.textContent).toBe('VISTA-AGENDA');
  });

  it('ruta pública sin sesión se renderiza sin redirigir ni layout', () => {
    stop = router.start();
    router.navigate('/publico');

    expect(location.pathname).toBe('/publico');
    expect(root.textContent).toBe('VISTA-PUBLICA');
    expect(root.querySelector('[data-layout]')).toBeNull();
  });

  it('ruta desconocida muestra la vista de página no encontrada', () => {
    stop = router.start();
    router.navigate('/no-existe');
    expect(root.textContent).toBe('PAGINA-NO-ENCONTRADA');
  });

  it('click en a[data-link] navega con pushState sin recargar', () => {
    session.setAuthenticated('a@b.c');
    stop = router.start();
    router.navigate('/agenda');
    const link = document.createElement('a');
    link.href = '/pacientes';
    link.dataset['link'] = '';
    root.append(link);
    const lengthBefore = history.length;

    const event = new MouseEvent('click', { bubbles: true, cancelable: true });
    link.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
    expect(location.pathname).toBe('/pacientes');
    expect(history.length).toBe(lengthBefore + 1);
    expect(root.querySelector('main')?.textContent).toBe('VISTA-PACIENTES');
  });

  it('un click con Ctrl no se intercepta y un enlace sin data-link tampoco', () => {
    session.setAuthenticated('a@b.c');
    stop = router.start();
    router.navigate('/agenda');
    const conData = document.createElement('a');
    conData.href = '/pacientes';
    conData.dataset['link'] = '';
    const sinData = document.createElement('a');
    sinData.href = '/pacientes';
    root.append(conData, sinData);

    const ctrl = new MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true });
    conData.dispatchEvent(ctrl);
    const plano = new MouseEvent('click', { bubbles: true, cancelable: true });
    sinData.addEventListener('click', (e) => e.preventDefault());
    sinData.dispatchEvent(plano);

    expect(ctrl.defaultPrevented).toBe(false);
    expect(location.pathname).toBe('/agenda');
  });

  it('el botón atrás (popstate) vuelve a renderizar la vista anterior', async () => {
    session.setAuthenticated('a@b.c');
    stop = router.start();
    router.navigate('/agenda');
    router.navigate('/pacientes');
    expect(root.querySelector('main')?.textContent).toBe('VISTA-PACIENTES');

    await new Promise<void>((resolve) => {
      window.addEventListener('popstate', () => resolve(), { once: true });
      history.back();
    });

    expect(location.pathname).toBe('/agenda');
    expect(root.querySelector('main')?.textContent).toBe('VISTA-AGENDA');
  });

  it('start() renderiza la URL actual (carga directa de /pacientes con sesión)', () => {
    session.setAuthenticated('a@b.c');
    history.replaceState(null, '', '/pacientes');
    stop = router.start();

    expect(root.querySelector('main')?.textContent).toBe('VISTA-PACIENTES');
  });

  it('carga directa de una ruta privada sin sesión redirige a login', () => {
    history.replaceState(null, '', '/pacientes');
    stop = router.start();

    expect(location.pathname).toBe('/login');
    expect(location.search).toBe('?next=%2Fpacientes');
  });
});
