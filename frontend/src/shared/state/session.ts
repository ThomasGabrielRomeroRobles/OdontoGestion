export type SessionState =
  | { readonly status: 'unknown' }
  | { readonly status: 'anonymous' }
  | { readonly status: 'authenticated'; readonly email: string };

export type SessionListener = (state: SessionState) => void;

export interface SessionStore {
  get(): SessionState;
  setAuthenticated(email: string): void;
  clear(): void;
  /** Devuelve la función para cancelar la suscripción. */
  subscribe(listener: SessionListener): () => void;
}

export function createSessionStore(): SessionStore {
  let state: SessionState = { status: 'unknown' };
  const listeners = new Set<SessionListener>();

  function update(next: SessionState): void {
    state = next;
    for (const listener of [...listeners]) listener(state);
  }

  return {
    get: () => state,
    setAuthenticated: (email) => update({ status: 'authenticated', email }),
    clear: () => update({ status: 'anonymous' }),
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

/** Estado de sesión compartido por toda la aplicación. */
export const session: SessionStore = createSessionStore();
