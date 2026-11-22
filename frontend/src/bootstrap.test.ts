import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { bootstrap, type BootstrapDeps } from './bootstrap';
import type { NotificacionesApi } from './features/notificaciones/notificaciones-api';
import { apiRequest, configureHttpClient } from './shared/http/client';
import { createSessionStore, type SessionStore } from './shared/state/session';

describe('bootstrap', () => {
  let root: HTMLElement;
  let session: SessionStore;
  let me: ReturnType<typeof vi.fn<BootstrapDeps['me']>>;
  let stop: (() => void) | undefined;

  function deps(): BootstrapDeps {
    return { root, session, me, login: vi.fn(), logout: vi.fn() };
  }

  beforeEach(() => {
    document.body.replaceChildren();
    root = document.createElement('div');
    document.body.append(root);
    session = createSessionStore();
    me = vi.fn<BootstrapDeps['me']>();
    history.replaceState(null, '', '/');
  });
  afterEach(() => {
    stop?.();
    stop = undefined;
    configureHttpClient({});
    vi.unstubAllGlobals();
  });

  it('con sesión existente queda autenticado y renderiza la ruta actual dentro del layout', async () => {
    me.mockResolvedValue({ email: 'odontologo@consultorio.example' });
    history.replaceState(null, '', '/agenda');

    stop = (await bootstrap(deps())).stop;

    expect(me).toHaveBeenCalledTimes(1);
    expect(session.get()).toEqual({ status: 'authenticated', email: 'odontologo@consultorio.example' });
    expect(location.pathname).toBe('/agenda');
    expect(root.textContent).toContain('odontologo@consultorio.example');
    expect(root.querySelector('main')?.textContent).toContain('Agenda');
  });

  it('sin sesión queda anónimo y una ruta privada redirige a login con next', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/pacientes');

    stop = (await bootstrap(deps())).stop;

    expect(session.get()).toEqual({ status: 'anonymous' });
    expect(location.pathname).toBe('/login');
    expect(location.search).toBe('?next=%2Fpacientes');
    expect(root.querySelector('input[type="password"]')).not.toBeNull();
  });

  it('si la consulta de sesión falla queda anónimo', async () => {
    me.mockRejectedValue({ kind: 'network' });
    history.replaceState(null, '', '/agenda');

    stop = (await bootstrap(deps())).stop;

    expect(session.get()).toEqual({ status: 'anonymous' });
    expect(location.pathname).toBe('/login');
  });

  it('sin sesión, /configuracion/prestaciones redirige a login con next', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/configuracion/prestaciones');

    stop = (await bootstrap(deps())).stop;

    expect(location.pathname).toBe('/login');
    expect(location.search).toBe('?next=%2Fconfiguracion%2Fprestaciones');
  });

  it('con sesión, /configuracion/horarios renderiza la sección dentro del layout', async () => {
    me.mockResolvedValue({ email: 'odontologo@consultorio.example' });
    history.replaceState(null, '', '/configuracion/horarios');
    const seccion = (nombre: string) => (container: HTMLElement) => {
      container.textContent = `seccion-${nombre}`;
    };
    const configSections = {
      consultorio: seccion('consultorio'),
      perfil: seccion('perfil'),
      prestaciones: seccion('prestaciones'),
      horarios: seccion('horarios'),
      bloqueos: seccion('bloqueos'),
    };

    stop = (await bootstrap({ ...deps(), configSections })).stop;

    expect(location.pathname).toBe('/configuracion/horarios');
    expect(root.textContent).toContain('seccion-horarios');
    expect(root.textContent).toContain('odontologo@consultorio.example');
  });

  it('sin sesión, /notificaciones redirige a login con next', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/notificaciones');

    stop = (await bootstrap(deps())).stop;

    expect(location.pathname).toBe('/login');
    expect(location.search).toBe('?next=%2Fnotificaciones');
  });

  it('con sesión, /notificaciones renderiza el registro de envíos dentro del layout', async () => {
    me.mockResolvedValue({ email: 'odontologo@consultorio.example' });
    history.replaceState(null, '', '/notificaciones');
    const notificacionesApi = {
      listar: vi.fn<NotificacionesApi['listar']>().mockResolvedValue({
        contenido: [],
        pagina: 0,
        tamanio: 20,
        totalElementos: 0,
        totalPaginas: 0,
      }),
      reintentar: vi.fn<NotificacionesApi['reintentar']>(),
    };

    stop = (await bootstrap({ ...deps(), notificacionesApi })).stop;

    expect(location.pathname).toBe('/notificaciones');
    expect(root.querySelector('main h1')?.textContent).toBe('Registro de envíos');
    expect(root.textContent).toContain('odontologo@consultorio.example');
    await vi.waitFor(() => expect(root.textContent).toContain('No hay envíos registrados'));
    expect(notificacionesApi.listar).toHaveBeenCalledWith({ page: 0, size: 20 });
  });

  it('una ruta pública se renderiza sin sesión y sin redirigir', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/publico');

    stop = (await bootstrap(deps())).stop;

    expect(location.pathname).toBe('/publico');
  });

  it('una ruta desconocida muestra página no encontrada', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/no-existe');

    stop = (await bootstrap(deps())).stop;

    expect(root.textContent?.toLowerCase()).toContain('página no encontrada');
  });

  it('un 401 posterior de la API limpia la sesión y navega a login conservando la ruta', async () => {
    me.mockResolvedValue({ email: 'a@b.c' });
    history.replaceState(null, '', '/agenda');
    stop = (await bootstrap(deps())).stop;
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(JSON.stringify({ title: 'No autenticado', status: 401 }), {
          status: 401,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    );

    await expect(apiRequest('GET', 'turnos')).rejects.toMatchObject({ status: 401 });

    expect(session.get()).toEqual({ status: 'anonymous' });
    expect(location.pathname).toBe('/login');
    expect(location.search).toBe('?next=%2Fagenda');
  });
});
