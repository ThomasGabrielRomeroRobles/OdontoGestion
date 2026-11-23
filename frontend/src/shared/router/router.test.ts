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

describe('router: segmentos variables', () => {
  let root: HTMLElement;
  let session: SessionStore;
  let router: Router;
  let stop: () => void;
  let token: string | undefined;

  beforeEach(() => {
    document.body.replaceChildren();
    root = document.createElement('div');
    document.body.append(root);
    session = createSessionStore();
    session.clear();
    token = undefined;
    history.replaceState(null, '', '/');
    router = createRouter({
      root,
      session,
      routes: [
        textRoute('/acciones/nuevo', 'public', 'VISTA-EXACTA'),
        {
          path: '/acciones/:token',
          access: 'public',
          render(container, context) {
            token = context.params['token'];
            container.textContent = `VISTA-TOKEN:${token ?? ''}`;
          },
        },
        {
          path: '/turnos/:turnoId/notas/:notaId',
          access: 'private',
          render(container, context) {
            container.textContent = `${context.params['turnoId']}/${context.params['notaId']}`;
          },
        },
        textRoute('/login', 'public', 'VISTA-LOGIN'),
      ],
      notFound: (container) => {
        container.textContent = 'PAGINA-NO-ENCONTRADA';
      },
      layout: (host) => {
        host.replaceChildren();
        const frame = document.createElement('div');
        frame.dataset['layout'] = 'si';
        const main = document.createElement('main');
        frame.append(main);
        host.append(frame);
        return main;
      },
    });
  });
  afterEach(() => stop?.());

  it('/acciones/:token resuelve /acciones/abc123 con params.token sin sesión ni layout', () => {
    stop = router.start();
    router.navigate('/acciones/abc123');

    expect(location.pathname).toBe('/acciones/abc123');
    expect(token).toBe('abc123');
    expect(root.textContent).toBe('VISTA-TOKEN:abc123');
    expect(root.querySelector('[data-layout]')).toBeNull();
  });

  it('otro valor del segmento entrega otro parámetro', () => {
    stop = router.start();
    router.navigate('/acciones/Zx_9-Y');
    expect(token).toBe('Zx_9-Y');
  });

  it('el segmento llega decodificado', () => {
    stop = router.start();
    router.navigate('/acciones/a%20b');
    expect(token).toBe('a b');
  });

  it('una ruta pública con segmento variable no usa el layout autenticado aunque haya sesión', () => {
    session.setAuthenticated('odontologo@consultorio.example');
    stop = router.start();
    router.navigate('/acciones/abc123');

    expect(root.textContent).toBe('VISTA-TOKEN:abc123');
    expect(root.querySelector('[data-layout]')).toBeNull();
  });

  it('segmento vacío o segmentos de más o de menos → no encontrada', () => {
    stop = router.start();
    for (const ruta of ['/acciones/', '/acciones', '/acciones/abc/extra']) {
      router.navigate(ruta);
      expect(root.textContent, ruta).toBe('PAGINA-NO-ENCONTRADA');
    }
    expect(token).toBeUndefined();
  });

  it('una codificación inválida no coincide en lugar de fallar', () => {
    stop = router.start();
    router.navigate('/acciones/%E0%A4%A');
    expect(root.textContent).toBe('PAGINA-NO-ENCONTRADA');
  });

  it('una ruta exacta tiene prioridad sobre una con segmento variable', () => {
    stop = router.start();
    router.navigate('/acciones/nuevo');
    expect(root.textContent).toBe('VISTA-EXACTA');
  });

  it('varios segmentos variables y ruta privada sin sesión redirige a login', () => {
    stop = router.start();
    router.navigate('/turnos/7/notas/3');

    expect(location.pathname).toBe('/login');
    expect(location.search).toBe('?next=%2Fturnos%2F7%2Fnotas%2F3');
  });

  it('varios segmentos variables con sesión llegan como parámetros', () => {
    session.setAuthenticated('a@b.c');
    stop = router.start();
    router.navigate('/turnos/7/notas/3');

    expect(root.querySelector('[data-layout] main')?.textContent).toBe('7/3');
  });

  it('las rutas exactas existentes entregan params vacíos', () => {
    let recibido: Readonly<Record<string, string>> | undefined;
    const exacto = createRouter({
      root,
      session,
      routes: [
        {
          path: '/publico',
          access: 'public',
          render(_container, context) {
            recibido = context.params;
          },
        },
      ],
      notFound: () => undefined,
      layout: (host) => host,
    });
    history.replaceState(null, '', '/publico');
    stop = exacto.start();

    expect(recibido).toEqual({});
  });
});
