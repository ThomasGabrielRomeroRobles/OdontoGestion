import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApiError } from '../shared/http/client';
import type { RouteContext } from '../shared/router/router';
import { createSessionStore, type SessionStore } from '../shared/state/session';
import { createLoginPage } from './login-page';

type LoginFn = (email: string, password: string) => Promise<{ email: string }>;

describe('pantalla /login', () => {
  let container: HTMLElement;
  let session: SessionStore;
  let navigate: ReturnType<typeof vi.fn<(to: string) => void>>;
  let login: ReturnType<typeof vi.fn<LoginFn>>;

  function show(search = ''): void {
    const context: RouteContext = { url: new URL(`http://localhost/login${search}`), params: {}, navigate };
    createLoginPage({ session, login })(container, context);
  }

  function field(selector: string): HTMLInputElement {
    const el = container.querySelector<HTMLInputElement>(selector);
    if (el === null) throw new Error(`falta ${selector}`);
    return el;
  }

  function submit(email: string, password: string): void {
    field('input[name="email"]').value = email;
    field('input[name="password"]').value = password;
    const form = container.querySelector('form');
    if (form === null) throw new Error('falta el formulario');
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  }

  function message(): string {
    return container.querySelector('[role="alert"]')?.textContent ?? '';
  }

  beforeEach(() => {
    container = document.createElement('div');
    session = createSessionStore();
    session.clear();
    navigate = vi.fn<(to: string) => void>();
    login = vi.fn<LoginFn>();
  });

  it('renderiza email, contraseña de tipo password y botón de ingreso', () => {
    show();
    expect(field('input[name="email"]').type).toBe('email');
    expect(field('input[name="password"]').type).toBe('password');
    expect(container.querySelector('button[type="submit"]')?.textContent).toBe('Ingresar');
  });

  it('los campos tienen etiquetas asociadas', () => {
    show();
    expect(container.querySelectorAll('label[for]').length).toBe(2);
  });

  it('email vacío no envía la solicitud y avisa que es obligatorio', () => {
    show();
    submit('', 'secreta');
    expect(login).not.toHaveBeenCalled();
    expect(message()).toContain('obligatorio');
    expect(message().toLowerCase()).toContain('email');
  });

  it('contraseña vacía tampoco envía la solicitud', () => {
    show();
    submit('a@b.c', '');
    expect(login).not.toHaveBeenCalled();
    expect(message().toLowerCase()).toContain('contraseña');
  });

  it('login exitoso con next navega a esa ruta y deja la sesión autenticada', async () => {
    login.mockResolvedValue({ email: 'a@b.c' });
    show('?next=%2Fpacientes');
    submit('a@b.c', 'secreta');

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/pacientes'));
    expect(login).toHaveBeenCalledWith('a@b.c', 'secreta');
    expect(session.get()).toEqual({ status: 'authenticated', email: 'a@b.c' });
  });

  it('login exitoso sin next navega a /agenda', async () => {
    login.mockResolvedValue({ email: 'a@b.c' });
    show();
    submit('a@b.c', 'secreta');
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/agenda'));
  });

  it('un next externo se ignora y navega a /agenda', async () => {
    login.mockResolvedValue({ email: 'a@b.c' });
    show('?next=https%3A%2F%2Fevil.example');
    submit('a@b.c', 'secreta');
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/agenda'));
    expect(navigate).not.toHaveBeenCalledWith('https://evil.example');
  });

  it('401 muestra mensaje genérico, vacía la contraseña y no navega', async () => {
    const error: ApiError = { kind: 'http', status: 401, title: 'Credenciales inválidas', fieldErrors: [], extensions: {} };
    login.mockRejectedValue(error);
    show();
    submit('a@b.c', 'mala');

    await vi.waitFor(() => expect(message()).toContain('Credenciales inválidas'));
    expect(field('input[name="password"]').value).toBe('');
    expect(navigate).not.toHaveBeenCalled();
    expect(session.get()).toEqual({ status: 'anonymous' });
  });

  it('el mensaje de 401 no revela si falló el email o la contraseña', async () => {
    login.mockRejectedValue({ kind: 'http', status: 401, title: 'x', fieldErrors: [], extensions: {} } satisfies ApiError);
    show();
    submit('nadie@b.c', 'mala');
    await vi.waitFor(() => expect(message()).not.toBe(''));
    expect(message().toLowerCase()).not.toContain('no existe');
  });

  it('una falla de red muestra un mensaje distinto al de credenciales', async () => {
    login.mockRejectedValue({ kind: 'network' } satisfies ApiError);
    show();
    submit('a@b.c', 'secreta');
    await vi.waitFor(() => expect(message()).toContain('No se pudo iniciar sesión'));
    expect(navigate).not.toHaveBeenCalled();
  });
});
