import type { Route } from '../shared/router/router';

/** Vista mínima de relleno hasta que el change correspondiente implemente la pantalla real. */
export function placeholderRoute(path: string, access: Route['access'], title: string): Route {
  return {
    path,
    access,
    render(container) {
      const heading = document.createElement('h1');
      heading.textContent = title;
      container.replaceChildren(heading);
    },
  };
}

export function renderNotFound(container: HTMLElement): void {
  const heading = document.createElement('h1');
  heading.textContent = 'Página no encontrada';
  const back = document.createElement('a');
  back.href = '/agenda';
  back.dataset['link'] = '';
  back.textContent = 'Volver al inicio';
  container.replaceChildren(heading, back);
}
