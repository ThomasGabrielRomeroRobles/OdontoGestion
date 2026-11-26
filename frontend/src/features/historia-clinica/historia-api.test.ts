import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { historiaApi } from './historia-api';
import { evolucion, historia, pieza } from './test-fixtures';

type FetchMock = ReturnType<typeof vi.fn<typeof fetch>>;

function mockFetch(body: unknown, status = 200): FetchMock {
  const response = new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  const fn = vi.fn<typeof fetch>().mockImplementation(() => Promise.resolve(response.clone()));
  vi.stubGlobal('fetch', fn);
  return fn;
}

function ultimaLlamada(fn: FetchMock): { url: string; method: string | undefined; body: unknown; csrf: string | null } {
  const call = fn.mock.calls.at(-1);
  if (!call) throw new Error('fetch no fue invocado');
  const init = call[1] ?? {};
  const body = typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined;
  return { url: String(call[0]), method: init.method, body, csrf: new Headers(init.headers).get('X-XSRF-TOKEN') };
}

describe('historia-api (contrato C-05 D7)', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok; path=/';
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('obtiene la ficha con GET', async () => {
    const fn = mockFetch(historia());
    const h = await historiaApi.obtener(7);
    expect(h.paciente.apellido).toBe('Pérez');
    expect(ultimaLlamada(fn)).toMatchObject({ url: '/api/v1/pacientes/7/historia', method: 'GET' });
  });

  it('guarda la anamnesis con PUT y CSRF', async () => {
    const fn = mockFetch(historia().anamnesis);
    await historiaApi.guardarAnamnesis(7, {
      alergias: 'Penicilina',
      medicacionActual: '',
      enfermedadesPrevias: '',
      antecedentesRelevantes: '',
      embarazo: null,
      observaciones: '',
    });
    const llamada = ultimaLlamada(fn);
    expect(llamada).toMatchObject({ url: '/api/v1/pacientes/7/historia/anamnesis', method: 'PUT', csrf: 'tok' });
    expect(llamada.body).toMatchObject({ alergias: 'Penicilina', embarazo: null });
  });

  it('guarda una pieza con PUT en su ruta FDI', async () => {
    const fn = mockFetch(pieza(16, { estado: 'CARIES' }));
    await historiaApi.guardarPieza(7, 16, { estado: 'CARIES', observacion: 'Oclusal' });
    const llamada = ultimaLlamada(fn);
    expect(llamada).toMatchObject({ url: '/api/v1/pacientes/7/historia/odontograma/piezas/16', method: 'PUT' });
    expect(llamada.body).toEqual({ estado: 'CARIES', observacion: 'Oclusal' });
  });

  it('lista, crea y edita evoluciones', async () => {
    let fn = mockFetch([evolucion(1)]);
    await historiaApi.listarEvoluciones(7);
    expect(ultimaLlamada(fn)).toMatchObject({ url: '/api/v1/pacientes/7/historia/evoluciones', method: 'GET' });

    fn = mockFetch(evolucion(2), 201);
    await historiaApi.crearEvolucion(7, { nota: 'Control', fecha: '2026-11-20' });
    expect(ultimaLlamada(fn)).toMatchObject({ url: '/api/v1/pacientes/7/historia/evoluciones', method: 'POST' });
    expect(ultimaLlamada(fn).body).toEqual({ nota: 'Control', fecha: '2026-11-20' });

    fn = mockFetch(evolucion(2));
    await historiaApi.editarEvolucion(7, 2, { nota: 'Control corregido' });
    expect(ultimaLlamada(fn)).toMatchObject({ url: '/api/v1/pacientes/7/historia/evoluciones/2', method: 'PUT' });
  });
});
