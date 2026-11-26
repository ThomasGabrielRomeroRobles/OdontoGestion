import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockFetchJson, ultimaSolicitud } from '../agenda/fetch-test-helpers';
import { pacientesApi } from './pacientes-api';

describe('pacientesApi (listado y ficha)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('buscar sin filtro: GET /pacientes sin query', async () => {
    const fn = mockFetchJson([]);
    await pacientesApi.buscar({});
    const s = ultimaSolicitud(fn);
    expect(s).toMatchObject({ method: 'GET', path: '/api/v1/pacientes' });
    expect([...s.query.keys()]).toEqual([]);
  });

  it('buscar por dni: manda solo dni', async () => {
    const fn = mockFetchJson([]);
    await pacientesApi.buscar({ dni: '30111222', q: 'x' });
    const s = ultimaSolicitud(fn);
    expect(s.query.get('dni')).toBe('30111222');
    expect(s.query.has('q')).toBe(false);
  });

  it('buscar por texto: manda q', async () => {
    const fn = mockFetchJson([]);
    await pacientesApi.buscar({ q: 'gómez' });
    expect(ultimaSolicitud(fn).query.get('q')).toBe('gómez');
  });

  it('obtener y ausencias: GET por id', async () => {
    const fn = mockFetchJson({ id: 7 });
    await pacientesApi.obtener(7);
    expect(ultimaSolicitud(fn)).toMatchObject({ method: 'GET', path: '/api/v1/pacientes/7' });
    await pacientesApi.ausencias(7);
    expect(ultimaSolicitud(fn)).toMatchObject({ method: 'GET', path: '/api/v1/pacientes/7/ausencias' });
  });
});
