import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  activarPrestacion,
  actualizarBloqueo,
  actualizarHorario,
  actualizarPrestacion,
  crearBloqueo,
  crearHorario,
  crearPrestacion,
  desactivarPrestacion,
  eliminarBloqueo,
  eliminarHorario,
  listarBloqueos,
  listarHorarios,
  listarPrestaciones,
  type Bloqueo,
  type Horario,
  type Prestacion,
} from './agenda-config-api';

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
  const headers = new Headers(init.headers);
  return {
    url: String(call[0]),
    method: init.method,
    body: typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined,
    csrf: headers.get('X-XSRF-TOKEN'),
  };
}

const prestacion: Prestacion = { id: 1, nombre: 'Consulta', duracionMin: 30, activa: true };
const horario: Horario = { id: 2, diaSemana: 1, horaDesde: '09:00', horaHasta: '13:00' };
const bloqueo: Bloqueo = { id: 3, desde: '2026-12-24T03:00:00Z', hasta: '2026-12-26T03:00:00Z', motivo: 'Fiestas' };

describe('agenda-config-api', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok; path=/';
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  describe('prestaciones', () => {
    it('lista con GET /prestaciones', async () => {
      const fn = mockFetch(json([prestacion]));
      await expect(listarPrestaciones()).resolves.toEqual([prestacion]);
      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/prestaciones', method: 'GET', csrf: null });
    });

    it('crea con POST y CSRF', async () => {
      const fn = mockFetch(json(prestacion, 201));
      await expect(crearPrestacion({ nombre: 'Consulta', duracionMin: 30 })).resolves.toEqual(prestacion);
      expect(lastCall(fn)).toEqual({
        url: '/api/v1/prestaciones',
        method: 'POST',
        body: { nombre: 'Consulta', duracionMin: 30 },
        csrf: 'tok',
      });
    });

    it('edita con PUT /prestaciones/{id}', async () => {
      const fn = mockFetch(json(prestacion));
      await actualizarPrestacion(1, { nombre: 'Consulta', duracionMin: 45 });
      expect(lastCall(fn)).toEqual({
        url: '/api/v1/prestaciones/1',
        method: 'PUT',
        body: { nombre: 'Consulta', duracionMin: 45 },
        csrf: 'tok',
      });
    });

    it('desactiva y activa con POST sin cuerpo', async () => {
      const fn = mockFetch(json(prestacion));
      await desactivarPrestacion(7);
      expect(lastCall(fn)).toEqual({ url: '/api/v1/prestaciones/7/desactivar', method: 'POST', body: undefined, csrf: 'tok' });
      await activarPrestacion(7);
      expect(lastCall(fn)).toEqual({ url: '/api/v1/prestaciones/7/activar', method: 'POST', body: undefined, csrf: 'tok' });
    });
  });

  describe('horarios', () => {
    it('lista con GET /horarios', async () => {
      const fn = mockFetch(json([horario]));
      await expect(listarHorarios()).resolves.toEqual([horario]);
      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/horarios', method: 'GET' });
    });

    it('crea, edita y elimina', async () => {
      const input = { diaSemana: 1, horaDesde: '09:00', horaHasta: '13:00' } as const;
      const fn = mockFetch(json(horario, 201));
      await crearHorario(input);
      expect(lastCall(fn)).toEqual({ url: '/api/v1/horarios', method: 'POST', body: input, csrf: 'tok' });
      await actualizarHorario(2, input);
      expect(lastCall(fn)).toEqual({ url: '/api/v1/horarios/2', method: 'PUT', body: input, csrf: 'tok' });

      const del = mockFetch(new Response(null, { status: 204 }));
      await expect(eliminarHorario(2)).resolves.toBeUndefined();
      expect(lastCall(del)).toEqual({ url: '/api/v1/horarios/2', method: 'DELETE', body: undefined, csrf: 'tok' });
    });
  });

  describe('bloqueos', () => {
    it('lista sin rango con GET /bloqueos', async () => {
      const fn = mockFetch(json([bloqueo]));
      await expect(listarBloqueos()).resolves.toEqual([bloqueo]);
      expect(lastCall(fn)).toMatchObject({ url: '/api/v1/bloqueos', method: 'GET' });
    });

    it('lista con rango en la query codificada', async () => {
      const fn = mockFetch(json([]));
      await listarBloqueos({ desde: '2026-12-01T03:00:00Z', hasta: '2027-01-01T03:00:00Z' });
      expect(lastCall(fn).url).toBe(
        '/api/v1/bloqueos?desde=2026-12-01T03%3A00%3A00Z&hasta=2027-01-01T03%3A00%3A00Z',
      );
    });

    it('crea, edita y elimina', async () => {
      const input = { desde: bloqueo.desde, hasta: bloqueo.hasta, motivo: 'Fiestas' };
      const fn = mockFetch(json(bloqueo, 201));
      await crearBloqueo(input);
      expect(lastCall(fn)).toEqual({ url: '/api/v1/bloqueos', method: 'POST', body: input, csrf: 'tok' });
      await actualizarBloqueo(3, { ...input, motivo: null });
      expect(lastCall(fn)).toEqual({
        url: '/api/v1/bloqueos/3',
        method: 'PUT',
        body: { ...input, motivo: null },
        csrf: 'tok',
      });

      const del = mockFetch(new Response(null, { status: 204 }));
      await eliminarBloqueo(3);
      expect(lastCall(del)).toEqual({ url: '/api/v1/bloqueos/3', method: 'DELETE', body: undefined, csrf: 'tok' });
    });
  });
});
