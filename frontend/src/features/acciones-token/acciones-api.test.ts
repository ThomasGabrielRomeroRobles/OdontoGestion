import { afterEach, describe, expect, it, vi } from 'vitest';
import { ejecutar, obtenerResumen } from './acciones-api';
import {
  ejecutado,
  problema409Concurrente,
  problema410,
  problema422Transicion,
  resumenCancelar,
  resumenConfirmarSolicitud,
} from './test-fixtures';

type FetchMock = ReturnType<typeof vi.fn<typeof fetch>>;

function mockFetch(response: Response): FetchMock {
  const fn = vi.fn<typeof fetch>().mockImplementation(() => Promise.resolve(response.clone()));
  vi.stubGlobal('fetch', fn);
  return fn;
}

function json(body: unknown, status = 200): Response {
  const tipo = status >= 400 ? 'application/problem+json' : 'application/json';
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': tipo } });
}

function lastCall(fn: FetchMock): { url: string; method: string | undefined; csrf: string | null; body: unknown } {
  const call = fn.mock.calls.at(-1);
  if (!call) throw new Error('fetch no fue invocado');
  const init = call[1] ?? {};
  const body: unknown = typeof init.body === 'string' ? JSON.parse(init.body) : undefined;
  return { url: String(call[0]), method: init.method, csrf: new Headers(init.headers).get('X-XSRF-TOKEN'), body };
}

describe('acciones-api', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  describe('obtenerResumen', () => {
    it('GET /acciones/{token} devuelve el resumen tipado del contrato', async () => {
      const fn = mockFetch(json(resumenCancelar));

      await expect(obtenerResumen('abc123')).resolves.toEqual({ tipo: 'resumen', resumen: resumenCancelar });

      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/acciones/abc123', method: 'GET', csrf: null });
    });

    it('el token se codifica con encodeURIComponent en la URL', async () => {
      const fn = mockFetch(json(resumenConfirmarSolicitud));

      await obtenerResumen('a/b?c d#e');

      expect(lastCall(fn).url).toBe('/api/v1/acciones/a%2Fb%3Fc%20d%23e');
    });

    it('410 → noDisponible, sin propagar el error', async () => {
      mockFetch(json(problema410, 410));
      await expect(obtenerResumen('vencido')).resolves.toEqual({ tipo: 'noDisponible' });
    });

    it('un cuerpo que no tiene la forma del contrato se rechaza como error', async () => {
      mockFetch(json({ accion: 'OTRA_COSA' }));
      await expect(obtenerResumen('abc')).rejects.toBeDefined();
    });

    it('un error de red o 5xx se propaga para el mensaje genérico', async () => {
      mockFetch(json({ title: 'Error', status: 500 }, 500));
      await expect(obtenerResumen('abc')).rejects.toMatchObject({ kind: 'http', status: 500 });
      vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(new TypeError('boom')));
      await expect(obtenerResumen('abc')).rejects.toEqual({ kind: 'network' });
    });
  });

  describe('ejecutar', () => {
    it('POST con la acción y el CSRF devuelve el resumen con el estado nuevo', async () => {
      document.cookie = 'XSRF-TOKEN=csrf-1; path=/';
      const nuevo = ejecutado(resumenCancelar, 'CANCELADO');
      const fn = mockFetch(json(nuevo));

      await expect(ejecutar('abc123', 'CANCELAR_TURNO')).resolves.toEqual({ tipo: 'ejecutada', resumen: nuevo });

      expect(lastCall(fn)).toEqual({
        url: '/api/v1/acciones/abc123',
        method: 'POST',
        csrf: 'csrf-1',
        body: { accion: 'CANCELAR_TURNO' },
      });
    });

    it('otra acción y otro token arman otro POST', async () => {
      const fn = mockFetch(json(ejecutado(resumenConfirmarSolicitud, 'RESERVADO')));
      await ejecutar('x y', 'CONFIRMAR_SOLICITUD');
      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/acciones/x%20y', body: { accion: 'CONFIRMAR_SOLICITUD' } });
    });

    it('410 → noDisponible', async () => {
      mockFetch(json(problema410, 410));
      await expect(ejecutar('abc', 'CANCELAR_TURNO')).resolves.toEqual({ tipo: 'noDisponible' });
    });

    it('422 (transición o límite) y 409 (concurrencia) → estadoCambiado', async () => {
      mockFetch(json(problema422Transicion, 422));
      await expect(ejecutar('abc', 'CANCELAR_TURNO')).resolves.toEqual({ tipo: 'estadoCambiado' });
      mockFetch(json(problema409Concurrente, 409));
      await expect(ejecutar('abc', 'CANCELAR_TURNO')).resolves.toEqual({ tipo: 'estadoCambiado' });
    });

    it('un 403 (CSRF) o un error de red se propagan', async () => {
      mockFetch(json({ title: 'Acceso denegado', status: 403 }, 403));
      await expect(ejecutar('abc', 'CANCELAR_TURNO')).rejects.toMatchObject({ kind: 'http', status: 403 });
    });
  });
});
