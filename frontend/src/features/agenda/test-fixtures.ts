/** Datos FICTICIOS para los tests de la agenda (regla dura #12). Solo los importan tests. */
import { vi, type Mock } from 'vitest';
import type { ApiError } from '../../shared/http/client';
import type { AgendaApi, EstadoTurno, Turno } from './agenda-api';
import type { PacienteResponse } from './pacientes-api';

/** Turno base: Lucía Gómez, Limpieza, martes 24/11/2026 09:00–09:45 en el consultorio (12:00Z). */
export function turnoFicticio(cambios: Partial<Turno> = {}): Turno {
  return {
    id: 41,
    paciente: { id: 7, nombre: 'Lucía', apellido: 'Gómez', cantidadAusencias: 0 },
    prestacion: { id: 3, nombre: 'Limpieza' },
    inicio: '2026-11-24T12:00:00Z',
    fin: '2026-11-24T12:45:00Z',
    estado: 'RESERVADO',
    origen: 'ODONTOLOGO',
    pendienteHasta: null,
    motivoCancelacion: null,
    canceladoPor: null,
    version: 0,
    ausenteMarcadoEn: null,
    ...cambios,
  };
}

export function turnoEn(estado: EstadoTurno, cambios: Partial<Turno> = {}): Turno {
  return turnoFicticio({ estado, ...cambios });
}

export const pacienteFicticio: PacienteResponse = {
  id: 7,
  nombre: 'Lucía',
  apellido: 'Gómez',
  dni: '30111222',
  fechaNacimiento: null,
  telefono: '+54 11 5555-0101',
  email: 'lucia.gomez@ejemplo.test',
  direccion: null,
  cantidadAusencias: 0,
  activo: true,
};

export function errorHttp(status: number, cambios: Partial<Extract<ApiError, { kind: 'http' }>> = {}): ApiError {
  return { kind: 'http', status, title: `Error ${status}`, fieldErrors: [], extensions: {}, ...cambios };
}

export const errorRed: ApiError = { kind: 'network' };

export type Mocks<T> = { [K in keyof T]: T[K] extends (...args: infer A) => infer R ? Mock<(...args: A) => R> : never };

/** Doble completo de `AgendaApi`: todo resuelve vacío salvo que el test lo cambie. */
export function crearAgendaApiDoble(): Mocks<AgendaApi> {
  const turno = turnoFicticio();
  return {
    listarAgenda: vi.fn<AgendaApi['listarAgenda']>().mockResolvedValue([]),
    disponibilidad: vi.fn<AgendaApi['disponibilidad']>().mockResolvedValue([]),
    turnosHoy: vi.fn<AgendaApi['turnosHoy']>().mockResolvedValue([]),
    crearTurno: vi.fn<AgendaApi['crearTurno']>().mockResolvedValue(turno),
    moverTurno: vi.fn<AgendaApi['moverTurno']>().mockResolvedValue(turno),
    cancelarTurno: vi.fn<AgendaApi['cancelarTurno']>().mockResolvedValue(turnoEn('CANCELADO')),
    marcarAtendido: vi.fn<AgendaApi['marcarAtendido']>().mockResolvedValue(turnoEn('ATENDIDO')),
    marcarAusente: vi.fn<AgendaApi['marcarAusente']>().mockResolvedValue(turnoEn('AUSENTE')),
    revertirAusencia: vi.fn<AgendaApi['revertirAusencia']>().mockResolvedValue(turno),
    ausenciasPaciente: vi.fn<AgendaApi['ausenciasPaciente']>().mockResolvedValue({ cantidad: 0, ausencias: [] }),
  };
}

/** Espera a que se resuelvan las promesas pendientes (microtareas + un macrotask). */
export function flush(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}
