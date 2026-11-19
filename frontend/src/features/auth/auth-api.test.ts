import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { configureHttpClient } from '../../shared/http/client';
import { login, logout, me } from './auth-api';

type FetchMock = ReturnType<typeof vi.fn<typeof fetch>>;

function mockFetch(factory: () => Response): FetchMock {
  const fn = vi.fn<typeof fetch>().mockImplementation(() => Promise.resolve(factory()));
  vi.stubGlobal('fetch', fn);
  return fn;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function lastRequest(fn: FetchMock): { url: string; method: string | undefined; body: unknown } {
  const call = fn.mock.calls.at(-1);
  if (!call) throw new Error('fetch no fue invocado');
  const init = call[1];
  const raw = init?.body;
  return { url: String(call[0]), method: init?.method, body: typeof raw === 'string' ? JSON.parse(raw) : undefined };
}

describe('auth-api', () => {
  const onUnauthorized = vi.fn<(path: string) => void>();

  beforeEach(() => {
    onUnauthorized.mockReset();
    configureHttpClient({ onUnauthorized });
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    configureHttpClient({});
  });

  it('me() devuelve el usuario con 200', async () => {
    const fn = mockFetch(() => json({ email: 'odontologo@consultorio.example' }));
    await expect(me()).resolves.toEqual({ email: 'odontologo@consultorio.example' });
    expect(lastRequest(fn)).toMatchObject({ url: '/api/v1/auth/me', method: 'GET' });
  });

  it('me() devuelve null con 401 y no dispara la redirección global', async () => {
    mockFetch(() => json({ title: 'No autenticado', status: 401 }, 401));
    await expect(me()).resolves.toBeNull();
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('me() propaga errores que no son 401', async () => {
    mockFetch(() => json({ title: 'Error interno', status: 500 }, 500));
    await expect(me()).rejects.toMatchObject({ kind: 'http', status: 500 });
  });

  it('login() hace POST /api/v1/auth/login con email y contraseña', async () => {
    const fn = mockFetch(() => json({ email: 'a@b.c' }));
    await expect(login('a@b.c', 'secreta')).resolves.toEqual({ email: 'a@b.c' });
    expect(lastRequest(fn)).toEqual({
      url: '/api/v1/auth/login',
      method: 'POST',
      body: { email: 'a@b.c', password: 'secreta' },
    });
  });

  it('login() con 401 rechaza con el error sin redirigir', async () => {
    mockFetch(() => json({ title: 'Credenciales inválidas', status: 401 }, 401));
    await expect(login('a@b.c', 'mala')).rejects.toMatchObject({ kind: 'http', status: 401 });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('logout() hace POST /api/v1/auth/logout y resuelve con 204', async () => {
    const fn = mockFetch(() => new Response(null, { status: 204 }));
    await expect(logout()).resolves.toBeUndefined();
    expect(lastRequest(fn)).toMatchObject({ url: '/api/v1/auth/logout', method: 'POST' });
  });
});
