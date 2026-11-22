import { beforeEach, describe, expect, it } from 'vitest';
import type { AgendaApi, Turno } from '../features/agenda/agenda-api';
import { fechaLocal } from '../features/agenda/calendario';
import type { PacientesApi } from '../features/agenda/pacientes-api';
import type { ApiError } from '../shared/http/client';
import { crearAgendaMocks } from './agenda-mocks';
import { crearMocksDesarrollo } from './config-mocks';

/** Martes 24/11/2026 09:00 en el consultorio. */
let ahora = new Date('2026-11-24T12:00:00Z');
const MIN = 60_000;

async function rechazo(promesa: Promise<unknown>): Promise<ApiError> {
  try {
    await promesa;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error('no fue rechazada');
}

describe('mocks de desarrollo de la agenda (8.2, design D10)', () => {
  let api: AgendaApi;
  let pacientes: PacientesApi;
  let semana: Turno[];

  beforeEach(async () => {
    ahora = new Date('2026-11-24T12:00:00Z');
    const config = crearMocksDesarrollo();
    ({ api, pacientes } = crearAgendaMocks({
      listarPrestaciones: config.agenda.listarPrestaciones,
      listarHorarios: config.agenda.listarHorarios,
      listarBloqueos: config.agenda.listarBloqueos,
      obtenerConsultorio: config.config.obtenerConsultorio,
      ahora: () => ahora,
    }));
    semana = await api.listarAgenda('2026-11-23T03:00:00Z', '2026-11-30T03:00:00Z');
  });

  async function prestacionId(nombre: string): Promise<number> {
    const t = semana.find((x) => x.prestacion.nombre === nombre);
    if (t === undefined) throw new Error(`sin turno de ${nombre}`);
    return t.prestacion.id;
  }

  it('la semana actual trae turnos en los 7 estados, todos de pacientes ficticios', () => {
    expect(new Set(semana.map((t) => t.estado))).toEqual(
      new Set(['PENDIENTE_CONFIRMACION', 'RESERVADO', 'CONFIRMADO', 'CANCELADO', 'AUSENTE', 'ATENDIDO', 'EXPIRADO']),
    );
  });

  it('trae un turno marcable como ausente y uno revertible, relativos a ahora', async () => {
    const hoy = await api.turnosHoy();
    const marcable = hoy.find((t) => t.estado === 'RESERVADO' && Date.parse(t.inicio) <= ahora.getTime() - 15 * MIN);
    const revertible = hoy.find((t) => t.estado === 'AUSENTE' && t.ausenteMarcadoEn !== null);
    expect(marcable).toBeDefined();
    expect(revertible).toBeDefined();
    await expect(api.revertirAusencia(revertible!.id)).resolves.toMatchObject({ estado: 'CONFIRMADO' });
  });

  it('crear superpuesto con un activo → 409 con hasta 3 alternativas libres', async () => {
    const activo = semana.find((t) => t.estado === 'RESERVADO' && Date.parse(t.inicio) > ahora.getTime())!;
    const error = await rechazo(
      api.crearTurno({ pacienteId: activo.paciente.id, prestacionId: activo.prestacion.id, inicio: activo.inicio }),
    );
    expect(error).toMatchObject({ kind: 'http', status: 409 });
    const alternativas = error.kind === 'http' ? (error.extensions['alternativas'] as unknown[]) : [];
    expect(alternativas.length).toBeGreaterThan(0);
    expect(alternativas.length).toBeLessThanOrEqual(3);
  });

  it('intervalos semiabiertos: un turno que empieza cuando termina otro no choca', async () => {
    const consulta = await prestacionId('Consulta');
    const pac = semana[0]!.paciente.id;
    // Jueves 26/11 14:00 y 14:30 (Consulta = 30 min).
    await api.crearTurno({ pacienteId: pac, prestacionId: consulta, inicio: '2026-11-26T17:00:00Z' });
    await expect(api.crearTurno({ pacienteId: pac, prestacionId: consulta, inicio: '2026-11-26T17:30:00Z' })).resolves.toMatchObject({
      estado: 'RESERVADO',
    });
  });

  it('un turno cancelado no ocupa el hueco', async () => {
    const cancelado = semana.find((t) => t.estado === 'CANCELADO')!;
    await expect(
      api.crearTurno({ pacienteId: cancelado.paciente.id, prestacionId: cancelado.prestacion.id, inicio: cancelado.inicio }),
    ).resolves.toMatchObject({ estado: 'RESERVADO', inicio: cancelado.inicio });
  });

  it('la disponibilidad excluye ocupados y respeta excluirTurnoId', async () => {
    const reservado = semana.find((t) => t.estado === 'RESERVADO' && Date.parse(t.inicio) > ahora.getTime())!;
    const dia = fechaLocal(reservado.inicio);
    const sin = await api.disponibilidad(reservado.prestacion.id, dia);
    const con = await api.disponibilidad(reservado.prestacion.id, dia, reservado.id);
    expect(sin.map((h) => h.inicio)).not.toContain(reservado.inicio);
    expect(con.map((h) => h.inicio)).toContain(reservado.inicio);
  });

  it('ausente antes de la tolerancia → 422; después suma 1 al contador', async () => {
    const consulta = await prestacionId('Consulta');
    const pac = semana[0]!.paciente;
    const turno = await api.crearTurno({ pacienteId: pac.id, prestacionId: consulta, inicio: '2026-11-24T13:00:00Z' });
    ahora = new Date(Date.parse(turno.inicio) + 14 * MIN);
    expect(await rechazo(api.marcarAusente(turno.id))).toMatchObject({ status: 422 });
    const antes = (await api.ausenciasPaciente(pac.id)).cantidad;
    ahora = new Date(Date.parse(turno.inicio) + 15 * MIN);
    const ausente = await api.marcarAusente(turno.id);
    expect(ausente.estado).toBe('AUSENTE');
    expect(ausente.ausenteMarcadoEn).toBe(ahora.toISOString());
    expect((await api.ausenciasPaciente(pac.id)).cantidad).toBe(antes + 1);
  });

  it('revertir fuera de 24 h → 422; dentro vuelve al estado previo y resta 1', async () => {
    const consulta = await prestacionId('Consulta');
    const pac = semana[0]!.paciente;
    const turno = await api.crearTurno({ pacienteId: pac.id, prestacionId: consulta, inicio: '2026-11-24T13:00:00Z' });
    ahora = new Date(Date.parse(turno.inicio) + 20 * MIN);
    await api.marcarAusente(turno.id);
    const conAusencia = (await api.ausenciasPaciente(pac.id)).cantidad;

    const marcado = ahora.getTime();
    ahora = new Date(marcado + 24 * 60 * MIN);
    expect(await rechazo(api.revertirAusencia(turno.id))).toMatchObject({ status: 422 });

    ahora = new Date(marcado + 60 * MIN);
    await expect(api.revertirAusencia(turno.id)).resolves.toMatchObject({ estado: 'RESERVADO', ausenteMarcadoEn: null });
    expect((await api.ausenciasPaciente(pac.id)).cantidad).toBe(conAusencia - 1);
  });

  it('transición inválida → 422 (atender un cancelado)', async () => {
    const cancelado = semana.find((t) => t.estado === 'CANCELADO')!;
    expect(await rechazo(api.marcarAtendido(cancelado.id))).toMatchObject({ status: 422 });
  });

  it('cancelar guarda el motivo y quién canceló', async () => {
    const reservado = semana.find((t) => t.estado === 'RESERVADO')!;
    await expect(api.cancelarTurno(reservado.id, 'Viaje')).resolves.toMatchObject({
      estado: 'CANCELADO',
      motivoCancelacion: 'Viaje',
      canceladoPor: 'ODONTOLOGO',
    });
  });

  it('acción sobre un turno inexistente → 404', async () => {
    expect(await rechazo(api.marcarAtendido(9999))).toMatchObject({ status: 404 });
  });

  it('turnosHoy devuelve solo los del día del consultorio', async () => {
    const hoy = await api.turnosHoy();
    expect(hoy.length).toBeGreaterThan(0);
    expect(hoy.every((t) => fechaLocal(t.inicio) === '2026-11-24')).toBe(true);
  });

  it('buscarPorDni acepta puntos de miles; DNI duplicado en el alta → 409', async () => {
    const existente = semana[0]!.paciente;
    const conPuntos = existente.dni.replace(/^(\d{1,2})(\d{3})(\d{3})$/, '$1.$2.$3');
    expect((await pacientes.buscarPorDni(conPuntos)).map((p) => p.id)).toEqual([existente.id]);
    expect(await pacientes.buscarPorDni('99999999')).toEqual([]);
    const error = await rechazo(
      pacientes.crearPaciente({ nombre: 'X', apellido: 'Y', dni: existente.dni, telefono: '1', email: 'x@ejemplo.test' }),
    );
    expect(error).toMatchObject({ status: 409 });
  });

  it('un paciente nuevo se puede agendar', async () => {
    const nuevo = await pacientes.crearPaciente({
      nombre: 'Elena',
      apellido: 'Vidal',
      dni: '39.123.456',
      telefono: '+54 11 5555-0909',
      email: ' Elena.Vidal@Ejemplo.TEST ',
    });
    expect(nuevo).toMatchObject({ dni: '39123456', email: 'elena.vidal@ejemplo.test', cantidadAusencias: 0, activo: true });
    const consulta = await prestacionId('Consulta');
    await expect(api.crearTurno({ pacienteId: nuevo.id, prestacionId: consulta, inicio: '2026-11-26T18:00:00Z' })).resolves.toMatchObject({
      paciente: { id: nuevo.id },
    });
  });
});
