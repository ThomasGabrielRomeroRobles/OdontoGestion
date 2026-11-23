/** Ayudas para los tests de `*-api.ts`: `fetch` mockeado y lectura de la última solicitud. Solo tests. */
import { vi, type Mock } from 'vitest';

export type FetchMock = Mock<typeof fetch>;

export function mockFetchJson(body: unknown, status = 200): FetchMock {
  const fn = vi
    .fn<typeof fetch>()
    .mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })),
    );
  vi.stubGlobal('fetch', fn);
  return fn;
}

export interface Solicitud {
  readonly method: string;
  readonly path: string;
  readonly query: URLSearchParams;
  readonly body: unknown;
  readonly csrf: string | null;
}

export function ultimaSolicitud(fn: FetchMock): Solicitud {
  const call = fn.mock.calls.at(-1);
  if (call === undefined) throw new Error('fetch no fue invocado');
  const init = call[1] ?? {};
  const url = new URL(String(call[0]), 'http://localhost');
  const raw = init.body;
  return {
    method: init.method ?? 'GET',
    path: url.pathname,
    query: url.searchParams,
    body: typeof raw === 'string' ? (JSON.parse(raw) as unknown) : undefined,
    csrf: new Headers(init.headers).get('X-XSRF-TOKEN'),
  };
}
