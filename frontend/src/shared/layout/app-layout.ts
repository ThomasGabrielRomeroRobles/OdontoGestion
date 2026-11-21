import type { AuthenticatedSession } from '../router/router';
import type { SessionStore } from '../state/session';

export interface AppLayoutDeps {
  readonly session: SessionStore;
  readonly logout: () => Promise<void>;
  readonly navigate: (to: string) => void;
}

function navLink(href: string, text: string): HTMLAnchorElement {
  const anchor = document.createElement('a');
  anchor.href = href;
  anchor.dataset['link'] = '';
  anchor.textContent = text;
  return anchor;
}

/** Marco de las vistas privadas: navegación, email de la sesión y cierre de sesión. */
export function createAppLayout(
  deps: AppLayoutDeps,
): (host: HTMLElement, authenticated: AuthenticatedSession) => HTMLElement {
  return (host, authenticated) => {
    const header = document.createElement('header');
    const nav = document.createElement('nav');
    nav.append(navLink('/agenda', 'Agenda'), navLink('/pacientes', 'Pacientes'), navLink('/configuracion', 'Configuración'));

    const user = document.createElement('span');
    user.textContent = authenticated.email;

    const logoutButton = document.createElement('button');
    logoutButton.type = 'button';
    logoutButton.textContent = 'Cerrar sesión';
    logoutButton.addEventListener('click', () => {
      // El cierre local ocurre siempre, aunque la API falle.
      deps
        .logout()
        .catch(() => undefined)
        .finally(() => {
          deps.session.clear();
          deps.navigate('/login');
        });
    });

    header.append(nav, user, logoutButton);
    const main = document.createElement('main');
    host.replaceChildren(header, main);
    return main;
  };
}
