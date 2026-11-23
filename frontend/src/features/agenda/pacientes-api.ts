import { apiRequest } from '../../shared/http/client';

/**
 * Contrato REAL de C-04 (spec `paciente-registry`, en `main`; verificado contra `PacienteController`,
 * `PacienteResponse` y `PacienteRequest` en la tarea 9.1). Solo lo que usa la agenda: búsqueda exacta por DNI y
 * alta rápida. `cantidadAusencias` ya viene en la ficha (C-04).
 */
export interface PacienteResponse {
  readonly id: number;
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly fechaNacimiento: string | null;
  readonly telefono: string;
  readonly email: string;
  readonly direccion: string | null;
  readonly cantidadAusencias: number;
  readonly activo: boolean;
}

/** Alta rápida: los obligatorios de C-04 (fecha de nacimiento y dirección son opcionales y no se piden). */
export interface PacienteAltaRapida {
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly telefono: string;
  readonly email: string;
}

export interface PacientesApi {
  /** Coincidencia exacta del DNI normalizado (acepta puntos de miles). */
  buscarPorDni(dni: string): Promise<PacienteResponse[]>;
  crearPaciente(input: PacienteAltaRapida): Promise<PacienteResponse>;
}

export const pacientesApi: PacientesApi = {
  buscarPorDni: (dni) => apiRequest<PacienteResponse[]>('GET', `pacientes?${new URLSearchParams({ dni }).toString()}`),
  crearPaciente: (input) => apiRequest<PacienteResponse>('POST', 'pacientes', input),
};
