import type { SessionUser } from '../features/auth/auth-api';
import { sanitizeNext } from '../shared/router/next';
import type { RouteContext } from '../shared/router/router';
import type { SessionStore } from '../shared/state/session';

export interface LoginPageDeps {
  readonly session: SessionStore;
  readonly login: (email: string, password: string) => Promise<SessionUser>;
}

function isUnauthorized(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'kind' in error &&
    'status' in error &&
    error.kind === 'http' &&
    error.status === 401
  );
}

function createField(
  form: HTMLFormElement,
  name: 'email' | 'password',
  label: string,
  type: 'email' | 'password',
): HTMLInputElement {
  const wrapper = document.createElement('div');
  const labelEl = document.createElement('label');
  labelEl.htmlFor = `login-${name}`;
  labelEl.textContent = label;
  const input = document.createElement('input');
  input.id = `login-${name}`;
  input.name = name;
  input.type = type;
  input.autocomplete = name === 'email' ? 'username' : 'current-password';
  wrapper.append(labelEl, input);
  form.append(wrapper);
  return input;
}

/** Vista de inicio de sesión. Construye el DOM con nodos (nunca innerHTML con datos del usuario). */
export function createLoginPage(deps: LoginPageDeps): (container: HTMLElement, context: RouteContext) => void {
  return (container, context) => {
    const heading = document.createElement('h1');
    heading.textContent = 'Iniciar sesión';

    const form = document.createElement('form');
    form.noValidate = true;
    const emailInput = createField(form, 'email', 'Email', 'email');
    const passwordInput = createField(form, 'password', 'Contraseña', 'password');

    const alert = document.createElement('p');
    alert.setAttribute('role', 'alert');
    const button = document.createElement('button');
    button.type = 'submit';
    button.textContent = 'Ingresar';
    form.append(alert, button);

    const next = sanitizeNext(context.url.searchParams.get('next'));

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const email = emailInput.value.trim();
      const password = passwordInput.value;
      if (email === '') {
        alert.textContent = 'El email es obligatorio.';
        return;
      }
      if (password === '') {
        alert.textContent = 'La contraseña es obligatoria.';
        return;
      }
      alert.textContent = '';
      button.disabled = true;
      deps
        .login(email, password)
        .then((user) => {
          deps.session.setAuthenticated(user.email);
          context.navigate(next);
        })
        .catch((error: unknown) => {
          passwordInput.value = '';
          alert.textContent = isUnauthorized(error)
            ? 'Credenciales inválidas.'
            : 'No se pudo iniciar sesión. Intente nuevamente.';
        })
        .finally(() => {
          button.disabled = false;
        });
    });

    container.replaceChildren(heading, form);
  };
}
