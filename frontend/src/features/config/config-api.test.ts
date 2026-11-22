import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  actualizarConsultorio,
  actualizarPerfil,
  cambiarPassword,
  obtenerConsultorio,
  obtenerPerfil,
  type Perfil,
} from './config-api';
import { consultorioFicticio } from './test-fixtures';

type FetchMock = ReturnType<typeof vi.fn<typeof fetch>>;

function mockFetch(response: Response): FetchMock {
  const fn = vi.fn<typeof fetch>().mockImplementation(() => Promise.resolve(response.clone()));
  vi.stubGlobal('fetch', fn);
  return fn;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function lastCall(fn: FetchMock): { url: string; method: string | undefined; body: unknown; csrf: string | null } {
  const call = fn.mock.calls.at(-1);
  if (!call) throw new Error('fetch no fue invocado');
  const init = call[1] ?? {};
  return {
    url: String(call[0]),
    method: init.method,
    body: typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined,
    csrf: new Headers(init.headers).get('X-XSRF-TOKEN'),
  };
}


const perfil: Perfil = { email: 'odontologo@consultorio.example', nombre: 'Ana', apellido: 'Pérez', matricula: 'MN 0000' };

describe('config-api', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok; path=/';
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('obtiene el consultorio con GET /consultorio', async () => {
    const fn = mockFetch(json(consultorioFicticio));
    await expect(obtenerConsultorio()).resolves.toEqual(consultorioFicticio);
    expect(lastCall(fn)).toMatchObject({ url: '/api/v1/consultorio', method: 'GET', csrf: null });
  });

  it('actualiza el consultorio con PUT: parámetros anidados (7 campos) y sin zona horaria', async () => {
    const fn = mockFetch(json(consultorioFicticio));
    await actualizarConsultorio({
      nombre: 'Consultorio Demo',
      direccion: 'Calle Falsa 123',
      telefono: '011 5555-0000',
      emailContacto: 'contacto@consultorio.example',
      parametros: { ...consultorioFicticio.parametros, toleranciaAusenteMin: 20 },
    });
    expect(lastCall(fn)).toEqual({
      url: '/api/v1/consultorio',
      method: 'PUT',
      body: {
        nombre: 'Consultorio Demo',
        direccion: 'Calle Falsa 123',
        telefono: '011 5555-0000',
        emailContacto: 'contacto@consultorio.example',
        parametros: {
          granularidadMin: 15,
          anticipacionMinReservaMin: 120,
          limiteCancelacionHoras: 2,
          recordatorioHorasAntes: 24,
          vencimientoAvisoMin: 60,
          vencimientoPendienteMin: 30,
          toleranciaAusenteMin: 20,
        },
      },
      csrf: 'tok',
    });
  });

  it('obtiene y actualiza el perfil sin enviar el email', async () => {
    const fn = mockFetch(json(perfil));
    await expect(obtenerPerfil()).resolves.toEqual(perfil);
    expect(lastCall(fn)).toMatchObject({ url: '/api/v1/perfil', method: 'GET' });

    await actualizarPerfil({ nombre: 'Ana', apellido: 'Pérez', matricula: 'MN 1111' });
    expect(lastCall(fn)).toEqual({
      url: '/api/v1/perfil',
      method: 'PUT',
      body: { nombre: 'Ana', apellido: 'Pérez', matricula: 'MN 1111' },
      csrf: 'tok',
    });
  });

  it('cambia la contraseña con POST /perfil/password', async () => {
    const fn = mockFetch(new Response(null, { status: 204 }));
    await expect(cambiarPassword({ passwordActual: 'vieja-ficticia', passwordNueva: 'nueva-ficticia' })).resolves.toBeUndefined();
    expect(lastCall(fn)).toEqual({
      url: '/api/v1/perfil/password',
      method: 'POST',
      body: { passwordActual: 'vieja-ficticia', passwordNueva: 'nueva-ficticia' },
      csrf: 'tok',
    });
  });
});
