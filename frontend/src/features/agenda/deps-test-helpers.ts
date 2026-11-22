/** Dobles de las dependencias de la feature agenda para tests de vista. Solo tests. */
import { vi, type Mock } from 'vitest';
import type { Prestacion } from '../config/agenda-config-api';
import type { Consultorio } from '../config/config-api';
import type { AgendaApi } from './agenda-api';
import type { AgendaDeps } from './deps';
import type { PacientesApi } from './pacientes-api';
import { crearAgendaApiDoble, pacienteFicticio, type Mocks } from './test-fixtures';

export const consultorioAgenda: Consultorio = {
  nombre: 'Consultorio Demo',
  direccion: 'Calle Falsa 123',
  telefono: '011 5555-0000',
  emailContacto: 'contacto@consultorio.example',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  parametros: {
    granularidadMin: 15,
    anticipacionMinReservaMin: 120,
    limiteCancelacionHoras: 2,
    recordatorioHorasAntes: 24,
    vencimientoAvisoMin: 60,
    vencimientoPendienteMin: 30,
    toleranciaAusenteMin: 15,
  },
};

export const prestacionesAgenda: readonly Prestacion[] = [
  { id: 1, nombre: 'Consulta', duracionMin: 30, activa: true },
  { id: 3, nombre: 'Limpieza', duracionMin: 45, activa: true },
  { id: 9, nombre: 'Blanqueamiento', duracionMin: 60, activa: false },
];

export interface AgendaDepsDoble {
  readonly api: Mocks<AgendaApi>;
  readonly pacientes: Mocks<PacientesApi>;
  readonly listarPrestaciones: Mock<() => Promise<Prestacion[]>>;
  readonly obtenerConsultorio: Mock<() => Promise<Consultorio>>;
  readonly confirmar: Mock<(mensaje: string) => boolean>;
  ahora: () => Date;
}

export function crearAgendaDepsDoble(ahora: Date): AgendaDepsDoble & AgendaDeps {
  return {
    api: crearAgendaApiDoble(),
    pacientes: {
      buscarPorDni: vi.fn<PacientesApi['buscarPorDni']>().mockResolvedValue([]),
      crearPaciente: vi.fn<PacientesApi['crearPaciente']>().mockResolvedValue(pacienteFicticio),
    },
    listarPrestaciones: vi.fn<() => Promise<Prestacion[]>>().mockResolvedValue([...prestacionesAgenda]),
    obtenerConsultorio: vi.fn<() => Promise<Consultorio>>().mockResolvedValue(consultorioAgenda),
    confirmar: vi.fn<(mensaje: string) => boolean>().mockReturnValue(true),
    ahora: () => ahora,
  };
}
