import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  consultarDisponibilidad,
  listarPrestaciones,
  parseAlternativas,
  solicitarTurno,
  type ResultadoSolicitud,
} from './publico-api';
import {
  alternativas,
  huecos,
  mensajeGenerico,
  prestaciones,
  problema400Dni,
  problema409,
  problema422Inicio,
  problema429,
  solicitudValida,
} from './test-fixtures';

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

describe('publico-api', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  describe('listarPrestaciones', () => {
    it('GET /publico/prestaciones devuelve la lista tipada del contrato', async () => {
      const fn = mockFetch(json(prestaciones));

      await expect(listarPrestaciones()).resolves.toEqual(prestaciones);

      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/publico/prestaciones', method: 'GET', csrf: null });
    });

    it('una lista vacía es válida', async () => {
      mockFetch(json([]));
      await expect(listarPrestaciones()).resolves.toEqual([]);
    });
  });

  describe('consultarDisponibilidad', () => {
    it('GET con prestacionId y la misma fecha como desde y hasta', async () => {
      const fn = mockFetch(json(huecos));

      await expect(consultarDisponibilidad(3, '2026-11-23')).resolves.toEqual(huecos);

      expect(lastCall(fn).url).toBe('/api/v1/publico/disponibilidad?prestacionId=3&desde=2026-11-23&hasta=2026-11-23');
    });

    it('otra prestación y otro día arman otra consulta', async () => {
      const fn = mockFetch(json([]));
      await consultarDisponibilidad(4, '2026-12-01');
      expect(lastCall(fn).url).toContain('prestacionId=4&desde=2026-12-01&hasta=2026-12-01');
    });
  });

  describe('solicitarTurno', () => {
    it('202 → aceptada, con POST, CSRF y el cuerpo del contrato incluido el señuelo', async () => {
      document.cookie = 'XSRF-TOKEN=tok; path=/';
      const fn = mockFetch(json({ mensaje: mensajeGenerico }, 202));

      const resultado = await solicitarTurno(solicitudValida);

      expect(resultado).toEqual({ tipo: 'aceptada', mensaje: mensajeGenerico });
      expect(lastCall(fn)).toEqual({
        url: '/api/v1/publico/solicitudes-turno',
        method: 'POST',
        csrf: 'tok',
        body: solicitudValida,
      });
    });

    it('el señuelo se envía tal como esté', async () => {
      const fn = mockFetch(json({ mensaje: mensajeGenerico }, 202));
      await solicitarTurno({ ...solicitudValida, sitioWeb: 'http://spam.example' });
      expect(lastCall(fn).body).toMatchObject({ sitioWeb: 'http://spam.example' });
    });

    it('409 hueco-no-disponible → conflicto con las alternativas', async () => {
      mockFetch(json(problema409, 409));
      await expect(solicitarTurno(solicitudValida)).resolves.toEqual({ tipo: 'conflicto', alternativas });
    });

    it('409 con alternativas mal formadas descarta lo inválido sin romper', async () => {
      mockFetch(json({ ...problema409, alternativas: [alternativas[0], { inicio: 3 }, 'x', null] }, 409));
      await expect(solicitarTurno(solicitudValida)).resolves.toEqual({
        tipo: 'conflicto',
        alternativas: [alternativas[0]],
      });
    });

    it('409 sin alternativas entrega una lista vacía', async () => {
      mockFetch(json({ ...problema409, alternativas: undefined }, 409));
      await expect(solicitarTurno(solicitudValida)).resolves.toEqual({ tipo: 'conflicto', alternativas: [] });
    });

    it('429 → limite con los segundos de Retry-After', async () => {
      mockFetch(json(problema429, 429, { 'Retry-After': '1800' }));
      await expect(solicitarTurno(solicitudValida)).resolves.toEqual({ tipo: 'limite', retryAfterSeconds: 1800 });
    });

    it('429 sin Retry-After → limite sin segundos', async () => {
      mockFetch(json(problema429, 429));
      const resultado = await solicitarTurno(solicitudValida);
      expect(resultado).toEqual({ tipo: 'limite' });
      expect(resultado).not.toHaveProperty('retryAfterSeconds');
    });

    it('400 → invalida con los errores por campo', async () => {
      mockFetch(json(problema400Dni, 400));
      await expect(solicitarTurno(solicitudValida)).resolves.toEqual({
        tipo: 'invalida',
        fieldErrors: problema400Dni.errors,
      });
    });

    it('422 con error en inicio → horarioInvalido', async () => {
      mockFetch(json(problema422Inicio, 422));
      await expect(solicitarTurno(solicitudValida)).resolves.toEqual({
        tipo: 'horarioInvalido',
        fieldErrors: problema422Inicio.errors,
      });
    });

    it('422 con otro campo → invalida', async () => {
      const cuerpo = { ...problema422Inicio, errors: [{ field: 'prestacionId', message: 'La prestación está desactivada.' }] };
      mockFetch(json(cuerpo, 422));
      const resultado: ResultadoSolicitud = await solicitarTurno(solicitudValida);
      expect(resultado.tipo).toBe('invalida');
    });

    it('un error de red o inesperado se propaga para el mensaje genérico', async () => {
      vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch')));
      await expect(solicitarTurno(solicitudValida)).rejects.toEqual({ kind: 'network' });

      mockFetch(json({ title: 'Error interno', status: 500 }, 500));
      await expect(solicitarTurno(solicitudValida)).rejects.toMatchObject({ kind: 'http', status: 500 });
    });
  });

  describe('parseAlternativas', () => {
    it('acepta solo objetos con inicio y fin de texto', () => {
      expect(parseAlternativas(alternativas)).toEqual(alternativas);
      expect(parseAlternativas([{ inicio: 'a', fin: 'b' }, { inicio: 'a' }, 7, undefined])).toEqual([
        { inicio: 'a', fin: 'b' },
      ]);
    });

    it('algo que no es una lista entrega una lista vacía', () => {
      expect(parseAlternativas('x')).toEqual([]);
      expect(parseAlternativas(undefined)).toEqual([]);
      expect(parseAlternativas({ inicio: 'a', fin: 'b' })).toEqual([]);
    });
  });
});
