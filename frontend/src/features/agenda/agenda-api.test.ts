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

  it('listarAgenda: un día del consultorio viaja como desde = hasta (fechas inclusivas de C-07)', async () => {
    const fn = mockFetchJson([turnoFicticio()]);
    const turnos = await agendaApi.listarAgenda('2026-11-24T03:00:00Z', '2026-11-25T03:00:00Z');
    const s = ultimaSolicitud(fn);
    expect(s.method).toBe('GET');
    expect(s.path).toBe('/api/v1/agenda');
    expect(s.query.get('desde')).toBe('2026-11-24');
    expect(s.query.get('hasta')).toBe('2026-11-24');
    expect(s.csrf).toBeNull();
    expect(turnos[0]?.id).toBe(41);
  });

  it('listarAgenda: una semana (lunes a domingo) incluye el domingo como hasta', async () => {
    const fn = mockFetchJson([]);
    await agendaApi.listarAgenda('2026-11-23T03:00:00Z', '2026-11-30T03:00:00Z');
    const s = ultimaSolicitud(fn);
    expect(s.query.get('desde')).toBe('2026-11-23');
    expect(s.query.get('hasta')).toBe('2026-11-29');
  });

  it('listarAgenda: las fechas no dependen de la zona del navegador', async () => {
    vi.stubEnv('TZ', 'Asia/Tokyo');
    try {
      const fn = mockFetchJson([]);
      await agendaApi.listarAgenda('2026-11-24T03:00:00Z', '2026-11-25T03:00:00Z');
      expect(ultimaSolicitud(fn).query.get('desde')).toBe('2026-11-24');
      expect(ultimaSolicitud(fn).query.get('hasta')).toBe('2026-11-24');
    } finally {
      vi.unstubAllEnvs();
    }
  });

  it('disponibilidad: pide el día con desde = hasta = fecha (C-07 no tiene fecha ni excluirTurnoId)', async () => {
    const fn = mockFetchJson([{ inicio: '2026-11-24T12:00:00Z', fin: '2026-11-24T12:45:00Z' }]);
    const huecos = await agendaApi.disponibilidad(3, '2026-11-24');
    const s = ultimaSolicitud(fn);
    expect(s.path).toBe('/api/v1/agenda/disponibilidad');
    expect(s.query.get('prestacionId')).toBe('3');
    expect(s.query.get('desde')).toBe('2026-11-24');
    expect(s.query.get('hasta')).toBe('2026-11-24');
    expect(s.query.has('fecha')).toBe(false);
    expect(s.query.has('excluirTurnoId')).toBe(false);
    expect(huecos).toHaveLength(1);
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

  it('ausenciasPaciente: GET /pacientes/{id}/ausencias con {cantidad, ausencias} (más reciente primero)', async () => {
    const cuerpo = {
      cantidad: 2,
      ausencias: [
        { turnoId: 31, inicio: '2026-11-17T12:00:00Z', prestacionNombre: 'Limpieza' },
        { turnoId: 30, inicio: '2026-11-10T12:00:00Z', prestacionNombre: 'Consulta' },
      ],
    };
    const fn = mockFetchJson(cuerpo);
    const r = await agendaApi.ausenciasPaciente(7);
    expect(ultimaSolicitud(fn)).toMatchObject({ method: 'GET', path: '/api/v1/pacientes/7/ausencias' });
    expect(r).toEqual(cuerpo);
  });

  it('TurnoDto real (C-07 + P3): sin DNI, con ausenteMarcadoEn y paciente.cantidadAusencias', async () => {
    const real = {
      id: 41,
      inicio: '2026-11-24T12:00:00Z',
      fin: '2026-11-24T12:45:00Z',
      estado: 'AUSENTE',
      origen: 'ODONTOLOGO',
      paciente: { id: 7, nombre: 'Lucía', apellido: 'Gómez', cantidadAusencias: 1 },
      prestacion: { id: 3, nombre: 'Limpieza' },
      pendienteHasta: null,
      canceladoPor: null,
      motivoCancelacion: null,
      version: 2,
      ausenteMarcadoEn: '2026-11-24T12:20:00Z',
    };
    mockFetchJson(real);
    const t = await agendaApi.marcarAusente(41);
    expect(t).toEqual(real);
    expect(t.paciente).not.toHaveProperty('dni');
    const marcadoEn: string | null = t.ausenteMarcadoEn;
    const cantidad: number = t.paciente.cantidadAusencias;
    expect([marcadoEn, cantidad]).toEqual(['2026-11-24T12:20:00Z', 1]);
  });

  it.each(['ausente-antes-de-tolerancia', 'reversion-fuera-de-plazo', 'transicion-invalida'])(
    '422 %s: el ApiError conserva el último segmento del type como code',
    async (codigo) => {
      mockFetchJson(
        { type: `https://odontogestion.example/problemas/${codigo}`, title: 'Regla de negocio incumplida', status: 422, detail: 'x' },
        422,
      );
      await expect(agendaApi.revertirAusencia(41)).rejects.toMatchObject({ kind: 'http', status: 422, code: codigo, detail: 'x' });
    },
  );
});
