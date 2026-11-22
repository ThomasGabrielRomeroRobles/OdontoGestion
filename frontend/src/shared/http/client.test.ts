import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiRequest, configureHttpClient, type ApiError } from './client';

type FetchMock = ReturnType<typeof vi.fn<typeof fetch>>;

/** Cada llamada recibe una copia nueva (el cuerpo de un Response solo se lee una vez). */
function mockFetch(response: Response): FetchMock {
  const fn = vi.fn<typeof fetch>().mockImplementation(() => Promise.resolve(response.clone()));
  vi.stubGlobal('fetch', fn);
  return fn;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function lastCall(fn: FetchMock): { url: string; init: RequestInit; headers: Headers } {
  const call = fn.mock.calls.at(-1);
  if (!call) throw new Error('fetch no fue invocado');
  const init = call[1] ?? {};
  return { url: String(call[0]), init, headers: new Headers(init.headers) };
}

function clearCookies(): void {
  for (const pair of document.cookie.split(';')) {
    const name = pair.split('=')[0]?.trim();
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  }
}

describe('apiRequest: forma de la solicitud', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/';
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    clearCookies();
  });

  it.each(['POST', 'PUT', 'PATCH', 'DELETE'] as const)('%s envía X-XSRF-TOKEN', async (method) => {
    const fn = mockFetch(jsonResponse({}));
    await apiRequest(method, 'auth/login');
    expect(lastCall(fn).headers.get('X-XSRF-TOKEN')).toBe('abc123');
  });

  it('GET no envía X-XSRF-TOKEN', async () => {
    const fn = mockFetch(jsonResponse({ email: 'a@b.c' }));
    await apiRequest('GET', 'auth/me');
    expect(lastCall(fn).headers.has('X-XSRF-TOKEN')).toBe(false);
  });

  it('una escritura sin cookie se envía sin cabecera CSRF', async () => {
    clearCookies();
    const fn = mockFetch(new Response(null, { status: 204 }));
    await apiRequest('DELETE', 'pacientes/1');
    expect(lastCall(fn).headers.has('X-XSRF-TOKEN')).toBe(false);
  });

  it('decodifica la cookie antes de enviarla (a%2Bb -> a+b)', async () => {
    document.cookie = 'XSRF-TOKEN=a%2Bb; path=/';
    const fn = mockFetch(jsonResponse({}));
    await apiRequest('PUT', 'pacientes/1', { nombre: 'x' });
    expect(lastCall(fn).headers.get('X-XSRF-TOKEN')).toBe('a+b');
  });

  it('usa ruta relativa bajo /api/v1 y tolera una barra inicial', async () => {
    const fn = mockFetch(jsonResponse({}));
    await apiRequest('GET', 'auth/me');
    expect(lastCall(fn).url).toBe('/api/v1/auth/me');
    await apiRequest('GET', '/pacientes?q=ana');
    expect(lastCall(fn).url).toBe('/api/v1/pacientes?q=ana');
  });

  it('incluye credenciales del mismo origen en cualquier solicitud', async () => {
    const fn = mockFetch(jsonResponse({}));
    await apiRequest('GET', 'auth/me');
    expect(lastCall(fn).init.credentials).toBe('same-origin');
    await apiRequest('POST', 'auth/logout');
    expect(lastCall(fn).init.credentials).toBe('same-origin');
  });

  it('serializa el cuerpo como JSON con Content-Type application/json', async () => {
    const fn = mockFetch(jsonResponse({}));
    await apiRequest('POST', 'auth/login', { email: 'a@b.c', password: 'x' });
    const { init, headers } = lastCall(fn);
    expect(init.body).toBe('{"email":"a@b.c","password":"x"}');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('Accept')).toContain('application/json');
  });

  it('sin cuerpo no envía Content-Type ni body', async () => {
    const fn = mockFetch(jsonResponse({}));
    await apiRequest('GET', 'auth/me');
    const { init, headers } = lastCall(fn);
    expect(init.body).toBeUndefined();
    expect(headers.has('Content-Type')).toBe(false);
  });

  it('200 devuelve el JSON decodificado', async () => {
    mockFetch(jsonResponse({ email: 'odontologo@consultorio.example' }));
    await expect(apiRequest<{ email: string }>('GET', 'auth/me')).resolves.toEqual({
      email: 'odontologo@consultorio.example',
    });
  });

  it('204 devuelve undefined', async () => {
    mockFetch(new Response(null, { status: 204 }));
    await expect(apiRequest('POST', 'auth/logout')).resolves.toBeUndefined();
  });
});

function problemResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/problem+json' } });
}

async function rejection(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error('la promesa no fue rechazada');
}

describe('apiRequest: errores tipados', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('422 con ProblemDetail expone status, title y detail', async () => {
    mockFetch(problemResponse({ title: 'Regla incumplida', detail: 'Duración inválida', status: 422 }, 422));
    expect(await rejection(apiRequest('POST', 'turnos', {}))).toEqual({
      kind: 'http',
      status: 422,
      title: 'Regla incumplida',
      detail: 'Duración inválida',
      fieldErrors: [],
      extensions: {},
    });
  });

  it('409 conserva los miembros de extensión sin repetir los estándar', async () => {
    const alternativas = [{ inicio: '2026-11-24T13:00:00Z', fin: '2026-11-24T13:45:00Z' }];
    mockFetch(problemResponse({ title: 'Conflicto de agenda', status: 409, alternativas }, 409));
    const error = await rejection(apiRequest('POST', 'turnos', {}));
    expect(error.kind === 'http' && error.extensions).toEqual({ alternativas });
  });

  it('un ProblemDetail sin extensiones entrega extensiones vacías', async () => {
    mockFetch(
      problemResponse(
        { type: 'about:blank', title: 'X', status: 422, detail: 'Y', instance: '/api/v1/turnos', errors: [] },
        422,
      ),
    );
    const error = await rejection(apiRequest('POST', 'turnos', {}));
    expect(error.kind === 'http' && error.extensions).toEqual({});
  });

  it('400 con errors expone los errores por campo', async () => {
    mockFetch(
      problemResponse(
        { title: 'Solicitud inválida', status: 400, errors: [{ field: 'email', message: 'formato inválido' }] },
        400,
      ),
    );
    const error = await rejection(apiRequest('POST', 'pacientes', {}));
    expect(error).toMatchObject({ kind: 'http', status: 400 });
    expect(error.kind === 'http' && error.fieldErrors).toEqual([{ field: 'email', message: 'formato inválido' }]);
  });

  it('502 con cuerpo HTML devuelve status y título genérico', async () => {
    mockFetch(new Response('<html>Bad Gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }));
    const error = await rejection(apiRequest('GET', 'pacientes'));
    expect(error).toEqual({ kind: 'http', status: 502, title: 'Error en la solicitud', fieldErrors: [], extensions: {} });
  });

  it('JSON de error que no es ProblemDetail también cae al título genérico', async () => {
    mockFetch(jsonResponse({ cualquier: 'cosa' }, 500));
    expect(await rejection(apiRequest('GET', 'x'))).toMatchObject({ kind: 'http', status: 500, title: 'Error en la solicitud' });
  });

  it('una falla de red se distingue de los errores HTTP', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch')));
    expect(await rejection(apiRequest('GET', 'pacientes'))).toEqual({ kind: 'network' });
  });
});

describe('apiRequest: sesión ausente o vencida (401)', () => {
  const onUnauthorized = vi.fn<(currentPath: string) => void>();

  beforeEach(() => {
    onUnauthorized.mockReset();
    configureHttpClient({ onUnauthorized });
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    configureHttpClient({});
    history.replaceState(null, '', '/');
  });

  it('invoca onUnauthorized con la ruta actual y rechaza con 401', async () => {
    history.replaceState(null, '', '/agenda');
    mockFetch(problemResponse({ title: 'No autenticado', status: 401 }, 401));
    expect(await rejection(apiRequest('GET', 'turnos'))).toMatchObject({ kind: 'http', status: 401 });
    expect(onUnauthorized).toHaveBeenCalledExactlyOnceWith('/agenda');
  });

  it('conserva la query de la ruta actual', async () => {
    history.replaceState(null, '', '/pacientes?q=ana');
    mockFetch(problemResponse({ title: 'No autenticado', status: 401 }, 401));
    await rejection(apiRequest('GET', 'pacientes'));
    expect(onUnauthorized).toHaveBeenCalledWith('/pacientes?q=ana');
  });

  it('el 401 de POST auth/login no redirige y entrega el error', async () => {
    mockFetch(problemResponse({ title: 'Credenciales inválidas', status: 401 }, 401));
    expect(await rejection(apiRequest('POST', 'auth/login', {}))).toMatchObject({ kind: 'http', status: 401 });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('un 401 de GET auth/me sí invoca el manejador (solo el login queda exento)', async () => {
    mockFetch(problemResponse({ title: 'No autenticado', status: 401 }, 401));
    await rejection(apiRequest('GET', 'auth/login'));
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('una respuesta 200 no invoca el manejador', async () => {
    mockFetch(jsonResponse({ ok: true }));
    await apiRequest('GET', 'turnos');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});
