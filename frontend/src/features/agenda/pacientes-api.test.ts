import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchJson, ultimaSolicitud } from './fetch-test-helpers';
import { pacientesApi } from './pacientes-api';
import { pacienteFicticio } from './test-fixtures';

describe('pacientesApi (contrato real de C-04)', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok-pac; path=/';
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('buscarPorDni: GET /pacientes?dni= sin CSRF', async () => {
    const fn = mockFetchJson([pacienteFicticio]);
    const r = await pacientesApi.buscarPorDni('30.111.222');
    const s = ultimaSolicitud(fn);
    expect(s).toMatchObject({ method: 'GET', path: '/api/v1/pacientes', csrf: null });
    expect(s.query.get('dni')).toBe('30.111.222');
    expect(r[0]?.dni).toBe('30111222');
  });

  it('crearPaciente: POST /pacientes con el cuerpo y CSRF', async () => {
    const fn = mockFetchJson(pacienteFicticio, 201);
    const input = {
      nombre: 'Lucía',
      apellido: 'Gómez',
      dni: '30111222',
      telefono: '+54 11 5555-0101',
      email: 'lucia.gomez@ejemplo.test',
    };
    const creado = await pacientesApi.crearPaciente(input);
    expect(ultimaSolicitud(fn)).toMatchObject({ method: 'POST', path: '/api/v1/pacientes', body: input, csrf: 'tok-pac' });
    expect(creado.id).toBe(7);
  });
});
