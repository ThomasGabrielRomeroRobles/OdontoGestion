import { readCsrfToken } from './csrf';
import { extensionMembers, isProblemDetail, type FieldError } from './problem';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type WriteMethod = Exclude<HttpMethod, 'GET'>;

export type ApiError =
  | {
      readonly kind: 'http';
      readonly status: number;
      readonly title: string;
      readonly detail?: string;
      readonly fieldErrors: readonly FieldError[];
      /** Miembros del ProblemDetail que no son estándar (p. ej. `alternativas` en un 409). `{}` si no hay. */
      readonly extensions: Readonly<Record<string, unknown>>;
      /** Último segmento de `type` (p. ej. `hueco-no-disponible`); ausente si el problema no trae un `type` útil. */
      readonly code?: string;
      /** Segundos de espera de la cabecera `Retry-After` de un 429; ausente si falta o no es un entero. */
      readonly retryAfterSeconds?: number;
    }
  | { readonly kind: 'network' };

export interface HttpClientOptions {
  /** Se invoca ante un 401 (salvo el de POST auth/login) con la ruta actual, para volver tras autenticarse. */
  readonly onUnauthorized?: (currentPath: string) => void;
}

let options: HttpClientOptions = {};

export function configureHttpClient(next: HttpClientOptions): void {
  options = next;
}

const GENERIC_TITLE = 'Error en la solicitud';
const API_BASE = '/api/v1';
const CSRF_HEADER = 'X-XSRF-TOKEN';

function isWriteMethod(method: HttpMethod): method is WriteMethod {
  return method !== 'GET';
}

function stripLeadingSlashes(path: string): string {
  return path.replace(/^[/]+/, '');
}

function buildUrl(path: string): string {
  return `${API_BASE}/${stripLeadingSlashes(path)}`;
}

export interface RequestOptions {
  /** `false` evita la redirección global ante 401 (p. ej. la consulta inicial de sesión). */
  readonly handleUnauthorized?: boolean;
}

/** Solicitud a la API del mismo origen. Resuelve con el JSON (o `undefined` en 204). */
export async function apiRequest<T = unknown>(
  method: HttpMethod,
  path: string,
  body?: unknown,
  requestOptions: RequestOptions = {},
): Promise<T> {
  const headers = new Headers({ Accept: 'application/json' });
  if (isWriteMethod(method)) {
    const token = readCsrfToken();
    if (token !== null) headers.set(CSRF_HEADER, token);
  }
  const init: RequestInit = { method, headers, credentials: 'same-origin' };
  if (body !== undefined) {
    headers.set('Content-Type', 'application/json');
    init.body = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path), init);
  } catch {
    throw { kind: 'network' } satisfies ApiError;
  }

  if (!response.ok) {
    if (response.status === 401 && requestOptions.handleUnauthorized !== false && !isLoginRequest(method, path)) {
      options.onUnauthorized?.(`${window.location.pathname}${window.location.search}`);
    }
    throw await toHttpError(response);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

function isLoginRequest(method: HttpMethod, path: string): boolean {
  return method === 'POST' && stripLeadingSlashes(path) === 'auth/login';
}

async function toHttpError(response: Response): Promise<ApiError> {
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    body = undefined;
  }
  const retryAfterSeconds = readRetryAfter(response);
  if (!isProblemDetail(body)) {
    return withRetryAfter(
      { kind: 'http', status: response.status, title: GENERIC_TITLE, fieldErrors: [], extensions: {} },
      retryAfterSeconds,
    );
  }
  const base = {
    kind: 'http',
    status: response.status,
    title: body.title ?? GENERIC_TITLE,
    fieldErrors: body.errors ?? [],
    extensions: extensionMembers(body),
  } as const;
  const code = problemCode(body.type);
  const withDetail = body.detail === undefined ? base : { ...base, detail: body.detail };
  return withRetryAfter(code === undefined ? withDetail : { ...withDetail, code }, retryAfterSeconds);
}

/** Último segmento del `type` de un ProblemDetail (`.../problemas/hueco-no-disponible`); `about:blank` no tiene. */
function problemCode(type: string | undefined): string | undefined {
  if (type === undefined || !/^https?:\/\//.test(type)) return undefined;
  const segment = type.slice(type.lastIndexOf('/') + 1);
  return segment === '' ? undefined : segment;
}

/** `Retry-After` en segundos enteros de un 429; una fecha HTTP, un valor vacío o no numérico se ignoran. */
function readRetryAfter(response: Response): number | undefined {
  if (response.status !== 429) return undefined;
  const raw = response.headers.get('Retry-After')?.trim();
  return raw !== undefined && /^\d+$/.test(raw) ? Number(raw) : undefined;
}

function withRetryAfter(error: Extract<ApiError, { kind: 'http' }>, seconds: number | undefined): ApiError {
  return seconds === undefined ? error : { ...error, retryAfterSeconds: seconds };
}
