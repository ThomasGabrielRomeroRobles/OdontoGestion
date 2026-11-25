import { apiRequest } from '../../shared/http/client';
import { fechaLocal } from './calendario';

/**
 * Contrato REAL de C-07 (`contrato-c08.md` del archivo de C-07) y del backend de ausentismo de C-08 (P3):
 * `TurnoDto`, `TurnoController` y `PacienteController#ausencias` (tarea 9.1, design "Diferencias con el
 * contrato real"). Instantes ISO-8601 en UTC.
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

/** El turno no expone DNI, email ni teléfono del paciente (regla dura #10). */
export interface PacienteResumen {
  readonly id: number;
  readonly nombre: string;
  readonly apellido: string;
  /** Contador oficial de ausencias del paciente (RN-AS-02). */
  readonly cantidadAusencias: number;
}

/** C-07 no expone la duración: sale de `fin - inicio` o de `GET /prestaciones`. */
export interface PrestacionResumen {
  readonly id: number;
  readonly nombre: string;
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
  /** Crece con cada cambio; hoy solo informa (C-07 no usa `If-Match`). */
  readonly version: number;
  /** Instante en que se marcó AUSENTE (`null` si no lo está); abre la ventana de 24 h de reversión (RN-AS-06). */
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

/** `GET /pacientes/{id}/ausencias`: `ausencias` viene de la más reciente a la más antigua. */
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
  /** Instantes UTC `[desde, hasta)` del rango visible (ver `rangoDia`/`rangoSemana`). */
  listarAgenda(desde: string, hasta: string): Promise<Turno[]>;
  /**
   * `fecha` es `yyyy-MM-dd` en el calendario del consultorio. Al mover, el rango del propio turno sigue
   * ocupado: C-07 no tiene `excluirTurnoId`.
   */
  disponibilidad(prestacionId: number, fecha: string): Promise<Hueco[]>;
  turnosHoy(): Promise<Turno[]>;
  crearTurno(input: CrearTurnoInput): Promise<Turno>;
  moverTurno(id: number, inicio: string): Promise<Turno>;
  cancelarTurno(id: number, motivo: string | null): Promise<Turno>;
  marcarAtendido(id: number): Promise<Turno>;
  /** `422` con `type` `.../ausente-antes-de-tolerancia` o `.../transicion-invalida`. */
  marcarAusente(id: number): Promise<Turno>;
  /** Devuelve el turno en su estado previo real; `422` `.../reversion-fuera-de-plazo` o `.../transicion-invalida`. */
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

/**
 * C-07 recibe `desde`/`hasta` como fechas `yyyy-MM-dd` INCLUSIVAS en la zona del consultorio. Convierte el
 * rango de instantes `[desde, hasta)`: el último día es el del instante anterior a `hasta`.
 */
export function fechasInclusivas(desde: string, hasta: string): { desde: string; hasta: string } {
  return { desde: fechaLocal(desde), hasta: fechaLocal(new Date(Date.parse(hasta) - 1)) };
}

/** Implementación sobre la API REST (con sesión; las escrituras llevan CSRF vía `apiRequest`). */
export const agendaApi: AgendaApi = {
  listarAgenda: (desde, hasta) => apiRequest<Turno[]>('GET', conQuery('agenda', fechasInclusivas(desde, hasta))),
  disponibilidad: (prestacionId, fecha) =>
    apiRequest<Hueco[]>(
      'GET',
      conQuery('agenda/disponibilidad', { prestacionId: String(prestacionId), desde: fecha, hasta: fecha }),
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
