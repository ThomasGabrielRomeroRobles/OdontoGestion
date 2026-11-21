import { apiRequest } from '../../shared/http/client';

/**
 * Contrato real de C-02 (spec `consultorio-config`, design.md D1): los parámetros viajan anidados
 * en `parametros`, siempre los siete. Si C-02 cambia el contrato, solo se ajusta este módulo.
 */
export interface ParametrosConsultorio {
  readonly granularidadMin: number;
  readonly anticipacionMinReservaMin: number;
  readonly limiteCancelacionHoras: number;
  readonly recordatorioHorasAntes: number;
  readonly vencimientoAvisoMin: number;
  readonly vencimientoPendienteMin: number;
  readonly toleranciaAusenteMin: number;
}

/** Cuerpo de `PUT /consultorio` (sin `zonaHoraria`, que no se edita). */
export interface ConsultorioEditable {
  readonly nombre: string;
  readonly direccion: string;
  readonly telefono: string;
  readonly emailContacto: string;
  readonly parametros: ParametrosConsultorio;
}

export interface Consultorio extends ConsultorioEditable {
  /** Solo lectura en el MVP. */
  readonly zonaHoraria: string;
}

export interface PerfilEditable {
  readonly nombre: string;
  readonly apellido: string;
  readonly matricula: string;
}

export interface Perfil extends PerfilEditable {
  /** Solo lectura: es el usuario de login. */
  readonly email: string;
}

export interface CambioPassword {
  readonly passwordActual: string;
  readonly passwordNueva: string;
}

export function obtenerConsultorio(): Promise<Consultorio> {
  return apiRequest<Consultorio>('GET', 'consultorio');
}

export function actualizarConsultorio(input: ConsultorioEditable): Promise<Consultorio> {
  return apiRequest<Consultorio>('PUT', 'consultorio', input);
}

export function obtenerPerfil(): Promise<Perfil> {
  return apiRequest<Perfil>('GET', 'perfil');
}

export function actualizarPerfil(input: PerfilEditable): Promise<Perfil> {
  return apiRequest<Perfil>('PUT', 'perfil', input);
}

export function cambiarPassword(input: CambioPassword): Promise<void> {
  return apiRequest<void>('POST', 'perfil/password', input);
}

/** Operaciones inyectables en las vistas. */
export interface ConfigApi {
  readonly obtenerConsultorio: typeof obtenerConsultorio;
  readonly actualizarConsultorio: typeof actualizarConsultorio;
  readonly obtenerPerfil: typeof obtenerPerfil;
  readonly actualizarPerfil: typeof actualizarPerfil;
  readonly cambiarPassword: typeof cambiarPassword;
}

export const configApi: ConfigApi = {
  obtenerConsultorio,
  actualizarConsultorio,
  obtenerPerfil,
  actualizarPerfil,
  cambiarPassword,
};
