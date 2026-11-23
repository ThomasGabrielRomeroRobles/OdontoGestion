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

  it('la disponibilidad excluye ocupados, incluido el propio turno (C-07 no tiene excluirTurnoId)', async () => {
    const reservado = semana.find((t) => t.estado === 'RESERVADO' && Date.parse(t.inicio) > ahora.getTime())!;
    const huecos = await api.disponibilidad(reservado.prestacion.id, fechaLocal(reservado.inicio));
    expect(huecos.map((h) => h.inicio)).not.toContain(reservado.inicio);
  });

  it('mover un turno 15 minutos sobre su propio rango es válido y sube la version', async () => {
    const consulta = await prestacionId('Consulta');
    // Jueves 26/11 15:00 → 15:15 en el consultorio (Consulta = 30 min): el destino pisa el rango actual.
    const creado = await api.crearTurno({ pacienteId: semana[0]!.paciente.id, prestacionId: consulta, inicio: '2026-11-26T18:00:00Z' });
    expect(creado.version).toBe(0);
    await expect(api.moverTurno(creado.id, '2026-11-26T18:15:00Z')).resolves.toMatchObject({
      inicio: '2026-11-26T18:15:00Z',
      fin: '2026-11-26T18:45:00Z',
      version: 1,
    });
  });

  it('los turnos ATENDIDO y AUSENTE siguen ocupando su rango (PA-06)', async () => {
    const atendido = semana.find((t) => t.estado === 'ATENDIDO')!;
    const huecos = await api.disponibilidad(atendido.prestacion.id, fechaLocal(atendido.inicio));
    expect(huecos.map((h) => h.inicio)).not.toContain(atendido.inicio);
  });

  it('el turno no expone DNI ni la duración de la prestación (contrato de C-07)', () => {
    expect(semana[0]!.paciente).not.toHaveProperty('dni');
    expect(semana[0]!.prestacion).not.toHaveProperty('duracionMin');
    expect(semana[0]!.version).toBe(0);
  });

  it('ausente antes de la tolerancia → 422; después suma 1 al contador', async () => {
    const consulta = await prestacionId('Consulta');
    const pac = semana[0]!.paciente;
    const turno = await api.crearTurno({ pacienteId: pac.id, prestacionId: consulta, inicio: '2026-11-24T13:00:00Z' });
    ahora = new Date(Date.parse(turno.inicio) + 14 * MIN);
    expect(await rechazo(api.marcarAusente(turno.id))).toMatchObject({
      status: 422,
      code: 'ausente-antes-de-tolerancia',
      detail: 'Todavía no pasó la tolerancia desde el inicio del turno: no se puede marcar ausente.',
    });
    const antes = (await api.ausenciasPaciente(pac.id)).cantidad;
    ahora = new Date(Date.parse(turno.inicio) + 15 * MIN);
    const ausente = await api.marcarAusente(turno.id);
    expect(ausente.estado).toBe('AUSENTE');
    expect(ausente.ausenteMarcadoEn).toBe(ahora.toISOString());
    expect((await api.ausenciasPaciente(pac.id)).cantidad).toBe(antes + 1);
    expect(ausente.paciente.cantidadAusencias).toBe(antes + 1);
    expect(ausente.paciente).not.toHaveProperty('dni');
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
    expect(await rechazo(api.revertirAusencia(turno.id))).toMatchObject({
      status: 422,
      code: 'reversion-fuera-de-plazo',
      detail: 'La ausencia solo se puede revertir dentro de las 24 horas desde que se marcó.',
    });

    ahora = new Date(marcado + 60 * MIN);
    await expect(api.revertirAusencia(turno.id)).resolves.toMatchObject({ estado: 'RESERVADO', ausenteMarcadoEn: null });
    expect((await api.ausenciasPaciente(pac.id)).cantidad).toBe(conAusencia - 1);
  });

  it('transición inválida → 422 (atender un cancelado)', async () => {
    const cancelado = semana.find((t) => t.estado === 'CANCELADO')!;
    expect(await rechazo(api.marcarAtendido(cancelado.id))).toMatchObject({
      status: 422,
      code: 'transicion-invalida',
      detail: 'No se puede pasar un turno de CANCELADO a ATENDIDO.',
    });
  });

  it('revertir un turno que no está AUSENTE → 422 transicion-invalida', async () => {
    const reservado = semana.find((t) => t.estado === 'RESERVADO')!;
    expect(await rechazo(api.revertirAusencia(reservado.id))).toMatchObject({ status: 422, code: 'transicion-invalida' });
  });

  it('revertir vuelve al estado previo REAL: un CONFIRMADO marcado ausente vuelve a CONFIRMADO', async () => {
    const confirmado = semana.find((t) => t.estado === 'CONFIRMADO')!;
    ahora = new Date(Date.parse(confirmado.inicio) + 30 * MIN);
    await api.marcarAusente(confirmado.id);
    await expect(api.revertirAusencia(confirmado.id)).resolves.toMatchObject({ estado: 'CONFIRMADO', ausenteMarcadoEn: null });
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
    const [existente] = await pacientes.buscarPorDni('30111222');
    expect(existente).toBeDefined();
    expect((await pacientes.buscarPorDni('30.111.222')).map((p) => p.id)).toEqual([existente!.id]);
    expect(await pacientes.buscarPorDni('99999999')).toEqual([]);
    const error = await rechazo(
      pacientes.crearPaciente({ nombre: 'X', apellido: 'Y', dni: existente!.dni, telefono: '1', email: 'x@ejemplo.test' }),
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
