import { describe, expect, it, vi } from 'vitest';
import { createSessionStore, type SessionState } from './session';

describe('store de sesión', () => {
  it('arranca en estado unknown', () => {
    expect(createSessionStore().get()).toEqual({ status: 'unknown' });
  });

  it('setAuthenticated guarda el email', () => {
    const store = createSessionStore();
    store.setAuthenticated('odontologo@consultorio.example');
    expect(store.get()).toEqual({ status: 'authenticated', email: 'odontologo@consultorio.example' });
  });

  it('clear deja la sesión anónima y olvida el email', () => {
    const store = createSessionStore();
    store.setAuthenticated('a@b.c');
    store.clear();
    expect(store.get()).toEqual({ status: 'anonymous' });
  });

  it('el suscriptor recibe cada cambio exactamente una vez', () => {
    const store = createSessionStore();
    const listener = vi.fn<(state: SessionState) => void>();
    store.subscribe(listener);

    store.clear();
    store.setAuthenticated('a@b.c');

    expect(listener).toHaveBeenCalledTimes(2);
    expect(listener).toHaveBeenNthCalledWith(1, { status: 'anonymous' });
    expect(listener).toHaveBeenNthCalledWith(2, { status: 'authenticated', email: 'a@b.c' });
  });

  it('tras desuscribirse deja de recibir cambios y otros suscriptores siguen', () => {
    const store = createSessionStore();
    const saliente = vi.fn<(state: SessionState) => void>();
    const restante = vi.fn<(state: SessionState) => void>();
    const unsubscribe = store.subscribe(saliente);
    store.subscribe(restante);

    unsubscribe();
    store.setAuthenticated('a@b.c');

    expect(saliente).not.toHaveBeenCalled();
    expect(restante).toHaveBeenCalledTimes(1);
  });
});
