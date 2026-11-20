import { apiRequest } from '../../shared/http/client';

/**
 * Contrato asumido de C-02 (design.md D1). Si C-02 publica otro, solo se ajusta este módulo.
 */
export interface ParametrosConsultorio {
  readonly granularidadMin: number;
  readonly anticipacionMinReservaMin: number;
  readonly limiteCancelacionHoras: number;
  readonly recordatorioHorasAntes: number;
  readonly vencimientoAvisoMin: number;
  readonly vencimientoPendienteMin: number;
}

export interface ConsultorioEditable extends ParametrosConsultorio {
  readonly nombre: string;
  readonly direccion: string;
  readonly telefono: string;
  readonly emailContacto: string;
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
