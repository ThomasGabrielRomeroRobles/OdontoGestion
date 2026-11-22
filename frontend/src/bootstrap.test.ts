import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { bootstrap, type BootstrapDeps } from './bootstrap';
import { crearAgendaDepsDoble } from './features/agenda/deps-test-helpers';
import { turnoFicticio } from './features/agenda/test-fixtures';
import { apiRequest, configureHttpClient } from './shared/http/client';
import { createSessionStore, type SessionStore } from './shared/state/session';

describe('bootstrap', () => {
  let root: HTMLElement;
  let session: SessionStore;
  let me: ReturnType<typeof vi.fn<BootstrapDeps['me']>>;
  let stop: (() => void) | undefined;

  function deps(): BootstrapDeps {
    return { root, session, me, login: vi.fn(), logout: vi.fn(), agenda: crearAgendaDepsDoble(new Date('2026-11-25T13:00:00Z')) };
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

  it('con sesión, /agenda renderiza la agenda real con las deps inyectadas', async () => {
    me.mockResolvedValue({ email: 'odontologo@consultorio.example' });
    history.replaceState(null, '', '/agenda?vista=dia&fecha=2026-11-24');
    const agenda = crearAgendaDepsDoble(new Date('2026-11-25T13:00:00Z'));
    agenda.api.listarAgenda.mockResolvedValue([turnoFicticio()]);

    stop = (await bootstrap({ ...deps(), agenda })).stop;
    await vi.waitFor(() => expect(root.querySelector('[data-turno-id="41"]')).not.toBeNull());

    expect(agenda.api.listarAgenda).toHaveBeenCalledWith('2026-11-24T03:00:00Z', '2026-11-25T03:00:00Z');
    expect(root.querySelector('main')?.textContent).toContain('Gómez, Lucía');
  });

  it('con sesión, /agenda/hoy lista los turnos del día', async () => {
    me.mockResolvedValue({ email: 'odontologo@consultorio.example' });
    history.replaceState(null, '', '/agenda/hoy');
    const agenda = crearAgendaDepsDoble(new Date('2026-11-24T13:00:00Z'));
    agenda.api.turnosHoy.mockResolvedValue([turnoFicticio()]);

    stop = (await bootstrap({ ...deps(), agenda })).stop;
    await vi.waitFor(() => expect(root.querySelector('[data-turno-id="41"]')).not.toBeNull());

    expect(root.querySelector('main h1')?.textContent).toBe('Turnos de hoy');
  });

  it('sin sesión, /agenda con parámetros redirige a login con la ruta completa en next', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/agenda?vista=dia&fecha=2026-11-24');

    stop = (await bootstrap(deps())).stop;

    expect(location.pathname).toBe('/login');
    expect(location.search).toBe('?next=%2Fagenda%3Fvista%3Ddia%26fecha%3D2026-11-24');
  });

  it('un 401 posterior de la API limpia la sesión y navega a login conservando la ruta', async () => {
    me.mockResolvedValue({ email: 'a@b.c' });
    history.replaceState(null, '', '/pacientes');
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
    expect(location.search).toBe('?next=%2Fpacientes');
  });
});
