export interface FieldError {
  readonly field: string;
  readonly message: string;
}

/** Cuerpo `application/problem+json` (RFC 9457) tal como lo emite la API. */
export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly errors?: readonly FieldError[];
}

export function isFieldError(value: unknown): value is FieldError {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as Record<string, unknown>)['field'] === 'string' &&
    typeof (value as Record<string, unknown>)['message'] === 'string'
  );
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
  const v = value as Record<string, unknown>;
  const hasShape = typeof v['title'] === 'string' || typeof v['detail'] === 'string' || typeof v['status'] === 'number';
  const errors = v['errors'];
  return hasShape && (errors === undefined || (Array.isArray(errors) && errors.every(isFieldError)));
}

const STANDARD_MEMBERS: ReadonlySet<string> = new Set(['type', 'title', 'status', 'detail', 'instance', 'errors']);

/** Miembros de extensión (RFC 9457 §3.2): todo lo que no es un miembro estándar ni `errors`. */
export function extensionMembers(problem: ProblemDetail): Readonly<Record<string, unknown>> {
  const extensions: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(problem)) {
    if (!STANDARD_MEMBERS.has(key)) extensions[key] = value;
  }
  return extensions;
}
