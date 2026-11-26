import { crearListaEsperaApiMock } from './dev/lista-espera-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { crearHistoriaApiMock } from './dev/historia-mocks';
import { bootstrap, type BootstrapDeps } from './bootstrap';
import { crearAgendaDepsDoble } from './features/agenda/deps-test-helpers';
import { turnoFicticio } from './features/agenda/test-fixtures';
import type { NotificacionesApi } from './features/notificaciones/notificaciones-api';
import type { AccionesApi } from './features/acciones-token/acciones-api';
import { resumenCancelar } from './features/acciones-token/test-fixtures';
import type { PublicoApi } from './features/publico/publico-api';
import { huecos, prestaciones } from './features/publico/test-fixtures';
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

  it('sin sesión, /pacientes/7/historia redirige a login con next (C-05)', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/pacientes/7/historia');

    stop = (await bootstrap(deps())).stop;

    expect(location.pathname).toBe('/login');
    expect(location.search).toBe('?next=%2Fpacientes%2F7%2Fhistoria');
  });

  it('con sesión, /pacientes/7/historia renderiza la historia clínica dentro del layout (C-05)', async () => {
    me.mockResolvedValue({ email: 'odontologo@consultorio.example' });
    history.replaceState(null, '', '/pacientes/7/historia');
    const historiaApi = crearHistoriaApiMock(() => new Date('2026-11-26T13:00:00Z'));

    stop = (await bootstrap({ ...deps(), historiaApi })).stop;
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(root.querySelector('main h1')?.textContent).toBe('Historia clínica — Ficticio 7, Paciente');
    expect(root.querySelectorAll('main [data-odontograma] button[data-fdi]')).toHaveLength(32);
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

  it('/publico sin sesión no redirige, no usa el layout del odontólogo y solo llama a /api/v1/publico', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/publico');
    const publicoApi: PublicoApi = {
      listarPrestaciones: vi.fn<PublicoApi['listarPrestaciones']>().mockResolvedValue(prestaciones),
      consultarDisponibilidad: vi.fn<PublicoApi['consultarDisponibilidad']>().mockResolvedValue(huecos),
      solicitarTurno: vi.fn<PublicoApi['solicitarTurno']>(),
    };
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal('fetch', fetchMock);

    stop = (await bootstrap({ ...deps(), publicoApi })).stop;

    expect(location.pathname).toBe('/publico');
    expect(root.querySelector('h1')?.textContent).toBe('Solicitá tu turno');
    expect(root.querySelector('nav, header')).toBeNull();
    expect(root.querySelector('select[name="prestacion"]')).not.toBeNull();
    await vi.waitFor(() => expect(publicoApi.listarPrestaciones).toHaveBeenCalledTimes(1));
    // La única llamada de red del arranque es la de la sesión (mockeada); la página usa la API inyectada.
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('/publico/lista-espera usa las prestaciones de la API pública inyectada, sin llamar a la red', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/publico/lista-espera');
    const publicoApi: PublicoApi = {
      listarPrestaciones: vi.fn<PublicoApi['listarPrestaciones']>().mockResolvedValue(prestaciones),
      consultarDisponibilidad: vi.fn<PublicoApi['consultarDisponibilidad']>(),
      solicitarTurno: vi.fn<PublicoApi['solicitarTurno']>(),
    };
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal('fetch', fetchMock);

    stop = (await bootstrap({ ...deps(), publicoApi, listaEsperaApi: crearListaEsperaApiMock() })).stop;

    expect(root.querySelector('h1')?.textContent).toBe('Anotate en la lista de espera');
    await vi.waitFor(() => expect(root.querySelectorAll('select[name="prestacion"] option')).toHaveLength(prestaciones.length + 1));
    expect(publicoApi.listarPrestaciones).toHaveBeenCalledTimes(1);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('/publico con sesión activa sigue siendo pública y no se envuelve en el layout', async () => {
    me.mockResolvedValue({ email: 'odontologo@consultorio.example' });
    history.replaceState(null, '', '/publico');
    const publicoApi: PublicoApi = {
      listarPrestaciones: vi.fn<PublicoApi['listarPrestaciones']>().mockResolvedValue([]),
      consultarDisponibilidad: vi.fn<PublicoApi['consultarDisponibilidad']>(),
      solicitarTurno: vi.fn<PublicoApi['solicitarTurno']>(),
    };

    stop = (await bootstrap({ ...deps(), publicoApi })).stop;

    expect(root.textContent).not.toContain('odontologo@consultorio.example');
  });

  it('/acciones/:token sin sesión no redirige a login, no usa el layout y pasa el token a la página', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/acciones/tok-123');
    const accionesApi: AccionesApi = {
      obtenerResumen: vi.fn<AccionesApi['obtenerResumen']>().mockResolvedValue({ tipo: 'resumen', resumen: resumenCancelar }),
      ejecutar: vi.fn<AccionesApi['ejecutar']>(),
    };

    stop = (await bootstrap({ ...deps(), accionesApi })).stop;

    expect(location.pathname).toBe('/acciones/tok-123');
    expect(session.get()).toEqual({ status: 'anonymous' });
    await vi.waitFor(() => expect(root.querySelector('button[data-accion="CANCELAR_TURNO"]')).not.toBeNull());
    expect(accionesApi.obtenerResumen).toHaveBeenCalledWith('tok-123');
    expect(accionesApi.ejecutar).not.toHaveBeenCalled();
    expect(root.querySelector('header, nav')).toBeNull();
  });

  it('/avisos/:token sin sesión no redirige, no usa el layout y consulta el aviso sin aceptarlo', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/avisos/tok-aviso');
    const listaEsperaApi = crearListaEsperaApiMock();
    const obtenerAviso = vi.spyOn(listaEsperaApi, 'obtenerAviso');
    const aceptarAviso = vi.spyOn(listaEsperaApi, 'aceptarAviso');

    stop = (await bootstrap({ ...deps(), listaEsperaApi })).stop;

    expect(session.get()).toEqual({ status: 'anonymous' });
    await vi.waitFor(() => expect(root.querySelector('button[data-accion="aceptar"]')).toBeNull());
    expect(obtenerAviso).toHaveBeenCalledWith('tok-aviso');
    expect(aceptarAviso).not.toHaveBeenCalled();
    expect(root.querySelector('header, nav')).toBeNull();
  });

  it('/publico/lista-espera es pública y /lista-espera exige sesión', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/publico/lista-espera');
    stop = (await bootstrap({ ...deps(), listaEsperaApi: crearListaEsperaApiMock() })).stop;
    expect(location.pathname).toBe('/publico/lista-espera');
    expect(root.querySelector('form')).not.toBeNull();
    stop();
    history.replaceState(null, '', '/lista-espera');
    stop = (await bootstrap({ ...deps(), listaEsperaApi: crearListaEsperaApiMock() })).stop;
    expect(location.pathname).toBe('/login');
  });

  it('/acciones/ sin token es una página no encontrada', async () => {
    me.mockResolvedValue(null);
    history.replaceState(null, '', '/acciones/');

    stop = (await bootstrap(deps())).stop;

    expect(root.querySelector('h1')?.textContent).toBe('Página no encontrada');
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
