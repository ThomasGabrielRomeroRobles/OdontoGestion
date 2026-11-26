import { apiRequest } from '../../shared/http/client';
import type { PacienteResponse } from '../agenda/pacientes-api';

export type { PacienteResponse } from '../agenda/pacientes-api';

/** `GET /api/v1/pacientes/{id}/ausencias` (US-061, C-08), la más reciente primero. */
export interface AusenciaDto {
  readonly turnoId: number;
  readonly inicio: string;
  readonly prestacionNombre: string;
}

export interface HistorialAusenciasDto {
  readonly cantidad: number;
  readonly ausencias: readonly AusenciaDto[];
}

/** Filtros de la búsqueda: con `dni` manda la coincidencia exacta; sin ninguno lista (máx. 20). */
export interface FiltroPacientes {
  readonly dni?: string;
  readonly q?: string;
}

export interface PacientesApi {
  buscar(filtro: FiltroPacientes): Promise<PacienteResponse[]>;
  obtener(id: number): Promise<PacienteResponse>;
  ausencias(id: number): Promise<HistorialAusenciasDto>;
}

export const pacientesApi: PacientesApi = {
  buscar: (filtro) => {
    const params = new URLSearchParams();
    if (filtro.dni !== undefined && filtro.dni !== '') params.set('dni', filtro.dni);
    else if (filtro.q !== undefined && filtro.q !== '') params.set('q', filtro.q);
    const qs = params.toString();
    return apiRequest<PacienteResponse[]>('GET', qs === '' ? 'pacientes' : `pacientes?${qs}`);
  },
  obtener: (id) => apiRequest<PacienteResponse>('GET', `pacientes/${id}`),
  ausencias: (id) => apiRequest<HistorialAusenciasDto>('GET', `pacientes/${id}/ausencias`),
};
