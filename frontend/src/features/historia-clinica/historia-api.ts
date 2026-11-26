import { apiRequest } from '../../shared/http/client';
import type { EstadoPieza } from './modelo';

/** Contrato de C-05 (design.md D7). Si cambia, solo se ajusta acá. Instantes ISO en UTC; fechas civiles `YYYY-MM-DD`. */
export interface Autor {
  readonly id: number;
  readonly nombre: string;
}

export interface DatosAnamnesis {
  readonly alergias: string;
  readonly medicacionActual: string;
  readonly enfermedadesPrevias: string;
  readonly antecedentesRelevantes: string;
  readonly embarazo: boolean | null;
  readonly observaciones: string;
}

export interface Anamnesis extends DatosAnamnesis {
  readonly actualizadoPor: Autor | null;
  readonly actualizadoEn: string | null;
}

export interface Pieza {
  readonly fdi: number;
  readonly estado: EstadoPieza | null;
  readonly observacion: string;
  readonly actualizadoPor: Autor | null;
  readonly actualizadoEn: string | null;
}

export interface DatosPieza {
  readonly estado: EstadoPieza;
  readonly observacion: string;
}

export interface Evolucion {
  readonly id: number;
  readonly fecha: string;
  readonly nota: string;
  readonly turnoId: number | null;
  readonly autor: Autor;
  readonly creadaEn: string;
  readonly editadaEn: string | null;
  /** Lo calcula la API (autor + ventana de edición): el frontend no duplica la regla. */
  readonly editable: boolean;
}

export interface NuevaEvolucion {
  readonly nota: string;
  readonly fecha?: string;
  readonly turnoId?: number;
}

export interface EdicionEvolucion {
  readonly nota: string;
  readonly fecha?: string;
}

export interface PacienteHistoria {
  readonly id: number;
  readonly nombre: string;
  readonly apellido: string;
}

export interface Historia {
  readonly paciente: PacienteHistoria;
  readonly anamnesis: Anamnesis;
  readonly piezas: readonly Pieza[];
  readonly evoluciones: readonly Evolucion[];
}

export interface HistoriaApi {
  readonly obtener: (pacienteId: number) => Promise<Historia>;
  readonly guardarAnamnesis: (pacienteId: number, datos: DatosAnamnesis) => Promise<Anamnesis>;
  readonly guardarPieza: (pacienteId: number, fdi: number, datos: DatosPieza) => Promise<Pieza>;
  readonly listarEvoluciones: (pacienteId: number) => Promise<readonly Evolucion[]>;
  readonly crearEvolucion: (pacienteId: number, datos: NuevaEvolucion) => Promise<Evolucion>;
  readonly editarEvolucion: (pacienteId: number, evolucionId: number, datos: EdicionEvolucion) => Promise<Evolucion>;
}

const base = (pacienteId: number): string => `pacientes/${pacienteId}/historia`;

export const historiaApi: HistoriaApi = {
  obtener: (pacienteId) => apiRequest<Historia>('GET', base(pacienteId)),
  guardarAnamnesis: (pacienteId, datos) => apiRequest<Anamnesis>('PUT', `${base(pacienteId)}/anamnesis`, datos),
  guardarPieza: (pacienteId, fdi, datos) =>
    apiRequest<Pieza>('PUT', `${base(pacienteId)}/odontograma/piezas/${fdi}`, datos),
  listarEvoluciones: (pacienteId) => apiRequest<readonly Evolucion[]>('GET', `${base(pacienteId)}/evoluciones`),
  crearEvolucion: (pacienteId, datos) => apiRequest<Evolucion>('POST', `${base(pacienteId)}/evoluciones`, datos),
  editarEvolucion: (pacienteId, evolucionId, datos) =>
    apiRequest<Evolucion>('PUT', `${base(pacienteId)}/evoluciones/${evolucionId}`, datos),
};
