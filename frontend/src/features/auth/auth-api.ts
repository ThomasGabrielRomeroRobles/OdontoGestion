import { apiRequest } from '../../shared/http/client';

/** Contrato del Flujo 1 (D11). C-02 publica el definitivo; si difiere, solo cambia este módulo. */
export interface SessionUser {
  readonly email: string;
}

export function login(email: string, password: string): Promise<SessionUser> {
  return apiRequest<SessionUser>('POST', 'auth/login', { email, password });
}

export function logout(): Promise<void> {
  return apiRequest<void>('POST', 'auth/logout');
}

/** Usuario de la sesión actual, o `null` si no hay sesión (401). */
export async function me(): Promise<SessionUser | null> {
  try {
    return await apiRequest<SessionUser>('GET', 'auth/me', undefined, { handleUnauthorized: false });
  } catch (error) {
    if (typeof error === 'object' && error !== null && 'kind' in error && 'status' in error && error.kind === 'http' && error.status === 401) {
      return null;
    }
    throw error;
  }
}
