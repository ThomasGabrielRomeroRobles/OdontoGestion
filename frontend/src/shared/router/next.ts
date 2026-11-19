export const DEFAULT_NEXT = '/agenda';

/** Solo se acepta una ruta interna: empieza con "/" y no con "//" ni "/\" (evita open redirect). */
export function sanitizeNext(value: string | null | undefined): string {
  if (value === null || value === undefined) return DEFAULT_NEXT;
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return DEFAULT_NEXT;
  return value;
}
