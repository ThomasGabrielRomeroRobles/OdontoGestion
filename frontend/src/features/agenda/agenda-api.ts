import { apiRequest } from '../../shared/http/client';

/**
 * Contrato PROVISIONAL v0 de C-07 y del backend de ausentismo de C-08 (design.md D1).
 * Instantes ISO-8601 en UTC. Cuando el backend real difiera, solo se ajusta este módulo (D2, grupo 9).
 */
export type EstadoTurno =
  | 'PENDIENTE_CONFIRMACION'
  | 'RESERVADO'
  | 'CONFIRMADO'
  | 'CANCELADO'
  | 'AUSENTE'
  | 'ATENDIDO'
  | 'EXPIRADO';

export type OrigenTurno = 'PUBLICA' | 'ODONTOLOGO' | 'LISTA_ESPERA';
export type CanceladoPor = 'ODONTOLOGO' | 'PACIENTE' | 'SISTEMA';

export interface PacienteResumen {
  readonly id: number;
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly cantidadAusencias: number;
}

export interface PrestacionResumen {
  readonly id: number;
  readonly nombre: string;
  readonly duracionMin: number;
}

export interface Turno {
  readonly id: number;
  readonly paciente: PacienteResumen;
  readonly prestacion: PrestacionResumen;
  readonly inicio: string;
  readonly fin: string;
  readonly estado: EstadoTurno;
  readonly origen: OrigenTurno;
  readonly pendienteHasta: string | null;
  readonly motivoCancelacion: string | null;
  readonly canceladoPor: CanceladoPor | null;
  /** Instante en que se marcó AUSENTE; abre la ventana de 24 h de reversión (RN-AS-06). */
  readonly ausenteMarcadoEn: string | null;
}

export interface Hueco {
  readonly inicio: string;
  readonly fin: string;
}

export interface Ausencia {
  readonly turnoId: number;
  readonly inicio: string;
  readonly prestacionNombre: string;
}

export interface AusenciasPaciente {
  readonly cantidad: number;
  readonly ausencias: readonly Ausencia[];
}

export interface CrearTurnoInput {
  readonly pacienteId: number;
  readonly prestacionId: number;
  readonly inicio: string;
}

export interface AgendaApi {
  listarAgenda(desde: string, hasta: string): Promise<Turno[]>;
  /** `fecha` es `yyyy-MM-dd` en el calendario del consultorio. */
  disponibilidad(prestacionId: number, fecha: string, excluirTurnoId?: number): Promise<Hueco[]>;
  turnosHoy(): Promise<Turno[]>;
  crearTurno(input: CrearTurnoInput): Promise<Turno>;
  moverTurno(id: number, inicio: string): Promise<Turno>;
  cancelarTurno(id: number, motivo: string | null): Promise<Turno>;
  marcarAtendido(id: number): Promise<Turno>;
  marcarAusente(id: number): Promise<Turno>;
  revertirAusencia(id: number): Promise<Turno>;
  ausenciasPaciente(pacienteId: number): Promise<AusenciasPaciente>;
}

function conQuery(path: string, params: Readonly<Record<string, string | undefined>>): string {
  const query = new URLSearchParams();
  for (const [clave, valor] of Object.entries(params)) {
    if (valor !== undefined) query.set(clave, valor);
  }
  const texto = query.toString();
  return texto === '' ? path : `${path}?${texto}`;
}

/** Implementación sobre la API REST (con sesión; las escrituras llevan CSRF vía `apiRequest`). */
export const agendaApi: AgendaApi = {
  listarAgenda: (desde, hasta) => apiRequest<Turno[]>('GET', conQuery('agenda', { desde, hasta })),
  disponibilidad: (prestacionId, fecha, excluirTurnoId) =>
    apiRequest<Hueco[]>(
      'GET',
      conQuery('agenda/disponibilidad', {
        prestacionId: String(prestacionId),
        fecha,
        excluirTurnoId: excluirTurnoId === undefined ? undefined : String(excluirTurnoId),
      }),
    ),
  turnosHoy: () => apiRequest<Turno[]>('GET', 'turnos/hoy'),
  crearTurno: (input) => apiRequest<Turno>('POST', 'turnos', input),
  moverTurno: (id, inicio) => apiRequest<Turno>('PUT', `turnos/${id}/mover`, { inicio }),
  cancelarTurno: (id, motivo) => apiRequest<Turno>('POST', `turnos/${id}/cancelar`, motivo === null ? {} : { motivo }),
  marcarAtendido: (id) => apiRequest<Turno>('POST', `turnos/${id}/atendido`),
  marcarAusente: (id) => apiRequest<Turno>('POST', `turnos/${id}/ausente`),
  revertirAusencia: (id) => apiRequest<Turno>('POST', `turnos/${id}/revertir-ausencia`),
  ausenciasPaciente: (pacienteId) => apiRequest<AusenciasPaciente>('GET', `pacientes/${pacienteId}/ausencias`),
};
