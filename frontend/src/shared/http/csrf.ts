const CSRF_COOKIE = 'XSRF-TOKEN';

/** Devuelve el valor decodificado de la cookie XSRF-TOKEN, o null si no existe. */
export function readCsrfToken(): string | null {
  for (const pair of document.cookie.split(';')) {
    const separator = pair.indexOf('=');
    if (separator === -1) continue;
    if (pair.slice(0, separator).trim() !== CSRF_COOKIE) continue;
    return decodeURIComponent(pair.slice(separator + 1).trim());
  }
  return null;
}
