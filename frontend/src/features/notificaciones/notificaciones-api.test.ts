import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { listar, reintentar, type NotificacionDto, type Pagina } from './notificaciones-api';
import { fallida, pagina } from './test-fixtures';

type FetchMock = ReturnType<typeof vi.fn<typeof fetch>>;

function mockFetch(response: Response): FetchMock {
  const fn = vi.fn<typeof fetch>().mockImplementation(() => Promise.resolve(response.clone()));
  vi.stubGlobal('fetch', fn);
  return fn;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function lastCall(fn: FetchMock): { url: string; method: string | undefined; csrf: string | null } {
  const call = fn.mock.calls.at(-1);
  if (!call) throw new Error('fetch no fue invocado');
  const init = call[1] ?? {};
  return { url: String(call[0]), method: init.method, csrf: new Headers(init.headers).get('X-XSRF-TOKEN') };
}

describe('notificaciones-api', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok; path=/';
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  describe('listar', () => {
    it('sin filtros consulta GET /notificaciones con page y size y devuelve la página tipada', async () => {
      const esperada: Pagina<NotificacionDto> = pagina([fallida]);
      const fn = mockFetch(json(esperada));

      await expect(listar({ page: 0, size: 20 })).resolves.toEqual(esperada);

      expect(lastCall(fn)).toEqual({ url: '/api/v1/notificaciones?page=0&size=20', method: 'GET', csrf: null });
    });

    it('agrega solo los filtros presentes', async () => {
      const fn = mockFetch(json(pagina([])));

      await listar({ estado: 'FALLIDA', page: 2, size: 20 });
      expect(lastCall(fn).url).toBe('/api/v1/notificaciones?estado=FALLIDA&page=2&size=20');

      await listar({ estado: 'ENVIADA', tipo: 'RECORDATORIO', page: 0, size: 50 });
      expect(lastCall(fn).url).toBe('/api/v1/notificaciones?estado=ENVIADA&tipo=RECORDATORIO&page=0&size=50');
    });
  });

  describe('reintentar', () => {
    it('hace POST /notificaciones/{id}/reintentar con CSRF y devuelve la notificación actualizada', async () => {
      const actualizada: NotificacionDto = { ...fallida, estado: 'PENDIENTE', reintentosManuales: 1 };
      const fn = mockFetch(json(actualizada));

      await expect(reintentar(7)).resolves.toEqual(actualizada);

      expect(lastCall(fn)).toEqual({ url: '/api/v1/notificaciones/7/reintentar', method: 'POST', csrf: 'tok' });
    });

    it('un 422 se propaga como el ApiError tipado con el detail del ProblemDetail', async () => {
      mockFetch(
        json({ title: 'Regla de negocio', status: 422, detail: 'La notificación ya se reintentó 3 veces manualmente.' }, 422),
      );

      await expect(reintentar(7)).rejects.toEqual({
        kind: 'http',
        status: 422,
        title: 'Regla de negocio',
        detail: 'La notificación ya se reintentó 3 veces manualmente.',
        fieldErrors: [],
        extensions: {},
      });
    });
  });
});
