import { afterEach, describe, expect, it, vi } from 'vitest';
import { aceptarAviso, inscribir, listar, obtenerAviso, retirar } from './lista-espera-api';
import { entradaActiva, entradaCubierta, inscripcionValida, problema409, problema410, resumenAviso } from './test-fixtures';

type FetchMock = ReturnType<typeof vi.fn<typeof fetch>>;

function mockFetch(response: Response): FetchMock {
  const fn = vi.fn<typeof fetch>().mockImplementation(() => Promise.resolve(response.clone()));
  vi.stubGlobal('fetch', fn);
  return fn;
}

function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  const tipo = status >= 400 ? 'application/problem+json' : 'application/json';
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': tipo, ...headers } });
}

function lastCall(fn: FetchMock): { url: string; method: string | undefined; csrf: string | null; body: unknown } {
  const call = fn.mock.calls.at(-1);
  if (!call) throw new Error('fetch no fue invocado');
  const init = call[1] ?? {};
  const body: unknown = typeof init.body === 'string' ? JSON.parse(init.body) : undefined;
  return { url: String(call[0]), method: init.method, csrf: new Headers(init.headers).get('X-XSRF-TOKEN'), body };
}

describe('lista-espera-api', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  describe('inscribir', () => {
    it('POST /publico/lista-espera con el cuerpo tal cual y 202 es aceptada', async () => {
      document.cookie = 'XSRF-TOKEN=tok; path=/';
      const fn = mockFetch(json({ mensaje: 'Recibimos tu pedido.' }, 202));

      await expect(inscribir(inscripcionValida)).resolves.toEqual({ tipo: 'aceptada', mensaje: 'Recibimos tu pedido.' });

      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/publico/lista-espera', method: 'POST', csrf: 'tok', body: inscripcionValida });
    });

    it('429 es limite con los segundos de Retry-After', async () => {
      mockFetch(json({ title: 'Demasiadas solicitudes', status: 429 }, 429, { 'Retry-After': '3600' }));
      await expect(inscribir(inscripcionValida)).resolves.toEqual({ tipo: 'limite', retryAfterSeconds: 3600 });
    });

    it('400 y 422 con errores por campo son invalida', async () => {
      const errors = [{ field: 'dni', message: 'Debe tener 7 u 8 dígitos.' }];
      mockFetch(json({ title: 'Solicitud inválida', status: 400, errors }, 400));
      await expect(inscribir(inscripcionValida)).resolves.toEqual({ tipo: 'invalida', fieldErrors: errors });

      const errores422 = [{ field: 'fechaHasta', message: 'No puede ser anterior a hoy.' }];
      mockFetch(json({ title: 'Regla', status: 422, errors: errores422 }, 422));
      await expect(inscribir(inscripcionValida)).resolves.toEqual({ tipo: 'invalida', fieldErrors: errores422 });
    });

    it('un 500 se propaga como error', async () => {
      mockFetch(json({ title: 'Error', status: 500 }, 500));
      await expect(inscribir(inscripcionValida)).rejects.toMatchObject({ kind: 'http', status: 500 });
    });
  });

  describe('obtenerAviso', () => {
    it('GET /avisos/{token} devuelve el resumen y codifica el token', async () => {
      const fn = mockFetch(json(resumenAviso));

      await expect(obtenerAviso('a/b c')).resolves.toEqual({ tipo: 'resumen', resumen: resumenAviso });

      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/avisos/a%2Fb%20c', method: 'GET', csrf: null });
    });

    it('410 es noDisponible y un cuerpo con otra forma es error', async () => {
      mockFetch(json(problema410, 410));
      await expect(obtenerAviso('x')).resolves.toEqual({ tipo: 'noDisponible' });

      mockFetch(json({ consultorio: 'X' }));
      await expect(obtenerAviso('x')).rejects.toMatchObject({ kind: 'http', status: 502 });
    });
  });

  describe('aceptarAviso', () => {
    it('POST /avisos/{token}/aceptar con 200 es reservado', async () => {
      document.cookie = 'XSRF-TOKEN=tok; path=/';
      const cuerpo = { prestacion: 'Consulta', inicio: resumenAviso.inicio, fin: resumenAviso.fin, estado: 'RESERVADO' };
      const fn = mockFetch(json(cuerpo));

      await expect(aceptarAviso('abc')).resolves.toEqual({ tipo: 'reservado', turno: cuerpo });

      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/avisos/abc/aceptar', method: 'POST', csrf: 'tok' });
    });

    it('409 es noDisponible y 410 es vencido', async () => {
      mockFetch(json(problema409, 409));
      await expect(aceptarAviso('abc')).resolves.toEqual({ tipo: 'noDisponible' });
      mockFetch(json(problema410, 410));
      await expect(aceptarAviso('abc')).resolves.toEqual({ tipo: 'vencido' });
    });
  });

  describe('listar y retirar', () => {
    it('GET /lista-espera devuelve las entradas tipadas', async () => {
      const fn = mockFetch(json([entradaActiva, entradaCubierta]));

      await expect(listar()).resolves.toEqual([entradaActiva, entradaCubierta]);

      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/lista-espera', method: 'GET' });
    });

    it('una fila que no cumple el contrato es error', async () => {
      mockFetch(json([{ ...entradaActiva, estado: 'RARO' }]));
      await expect(listar()).rejects.toMatchObject({ kind: 'http', status: 502 });
    });

    it('DELETE /lista-espera/{id} con 204 resuelve y envía CSRF', async () => {
      document.cookie = 'XSRF-TOKEN=tok; path=/';
      const fn = mockFetch(new Response(null, { status: 204 }));

      await expect(retirar(11)).resolves.toBeUndefined();

      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/lista-espera/11', method: 'DELETE', csrf: 'tok' });
    });
  });
});
