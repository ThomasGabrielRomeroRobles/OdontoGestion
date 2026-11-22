import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { agendaApi } from './agenda-api';
import { mockFetchJson, ultimaSolicitud } from './fetch-test-helpers';
import { turnoFicticio } from './test-fixtures';

describe('agendaApi (contrato provisional v0, design D1)', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok-agenda; path=/';
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('listarAgenda: GET /agenda con desde y hasta', async () => {
    const fn = mockFetchJson([turnoFicticio()]);
    const turnos = await agendaApi.listarAgenda('2026-11-24T03:00:00Z', '2026-11-25T03:00:00Z');
    const s = ultimaSolicitud(fn);
    expect(s.method).toBe('GET');
    expect(s.path).toBe('/api/v1/agenda');
    expect(s.query.get('desde')).toBe('2026-11-24T03:00:00Z');
    expect(s.query.get('hasta')).toBe('2026-11-25T03:00:00Z');
    expect(s.csrf).toBeNull();
    expect(turnos[0]?.id).toBe(41);
  });

  it('disponibilidad: sin excluirTurnoId no envía el parámetro', async () => {
    const fn = mockFetchJson([{ inicio: '2026-11-24T12:00:00Z', fin: '2026-11-24T12:45:00Z' }]);
    const huecos = await agendaApi.disponibilidad(3, '2026-11-24');
    const s = ultimaSolicitud(fn);
    expect(s.path).toBe('/api/v1/agenda/disponibilidad');
    expect(s.query.get('prestacionId')).toBe('3');
    expect(s.query.get('fecha')).toBe('2026-11-24');
    expect(s.query.has('excluirTurnoId')).toBe(false);
    expect(huecos).toHaveLength(1);
  });

  it('disponibilidad: con excluirTurnoId lo envía', async () => {
    const fn = mockFetchJson([]);
    await agendaApi.disponibilidad(3, '2026-11-24', 41);
    expect(ultimaSolicitud(fn).query.get('excluirTurnoId')).toBe('41');
  });

  it('turnosHoy: GET /turnos/hoy', async () => {
    const fn = mockFetchJson([]);
    await agendaApi.turnosHoy();
    expect(ultimaSolicitud(fn)).toMatchObject({ method: 'GET', path: '/api/v1/turnos/hoy' });
  });

  it('crearTurno: POST /turnos con el cuerpo y CSRF', async () => {
    const fn = mockFetchJson(turnoFicticio(), 201);
    await agendaApi.crearTurno({ pacienteId: 7, prestacionId: 3, inicio: '2026-11-24T12:00:00Z' });
    expect(ultimaSolicitud(fn)).toEqual({
      method: 'POST',
      path: '/api/v1/turnos',
      query: new URLSearchParams(),
      body: { pacienteId: 7, prestacionId: 3, inicio: '2026-11-24T12:00:00Z' },
      csrf: 'tok-agenda',
    });
  });

  it('moverTurno: PUT /turnos/{id}/mover con {inicio}', async () => {
    const fn = mockFetchJson(turnoFicticio());
    await agendaApi.moverTurno(41, '2026-11-24T14:00:00Z');
    expect(ultimaSolicitud(fn)).toMatchObject({
      method: 'PUT',
      path: '/api/v1/turnos/41/mover',
      body: { inicio: '2026-11-24T14:00:00Z' },
      csrf: 'tok-agenda',
    });
  });

  it('cancelarTurno: con motivo lo envía', async () => {
    const fn = mockFetchJson(turnoFicticio({ estado: 'CANCELADO' }));
    await agendaApi.cancelarTurno(41, 'Paciente avisó por teléfono');
    expect(ultimaSolicitud(fn)).toMatchObject({
      method: 'POST',
      path: '/api/v1/turnos/41/cancelar',
      body: { motivo: 'Paciente avisó por teléfono' },
      csrf: 'tok-agenda',
    });
  });

  it('cancelarTurno: sin motivo envía un objeto vacío', async () => {
    const fn = mockFetchJson(turnoFicticio({ estado: 'CANCELADO' }));
    await agendaApi.cancelarTurno(41, null);
    expect(ultimaSolicitud(fn).body).toEqual({});
  });

  it.each([
    ['marcarAtendido', 'atendido'],
    ['marcarAusente', 'ausente'],
    ['revertirAusencia', 'revertir-ausencia'],
  ] as const)('%s: POST /turnos/{id}/%s sin cuerpo y con CSRF', async (metodo, sufijo) => {
    const fn = mockFetchJson(turnoFicticio());
    await agendaApi[metodo](41);
    const s = ultimaSolicitud(fn);
    expect(s).toMatchObject({ method: 'POST', path: `/api/v1/turnos/41/${sufijo}`, csrf: 'tok-agenda' });
    expect(s.body).toBeUndefined();
  });

  it('ausenciasPaciente: GET /pacientes/{id}/ausencias', async () => {
    const fn = mockFetchJson({ cantidad: 1, ausencias: [{ turnoId: 30, inicio: '2026-11-10T12:00:00Z', prestacionNombre: 'Consulta' }] });
    const r = await agendaApi.ausenciasPaciente(7);
    expect(ultimaSolicitud(fn)).toMatchObject({ method: 'GET', path: '/api/v1/pacientes/7/ausencias' });
    expect(r.cantidad).toBe(1);
  });
});
