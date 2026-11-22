import type { Route } from '../../../shared/router/router';

export type SeccionId = 'consultorio' | 'perfil' | 'prestaciones' | 'horarios' | 'bloqueos';

/** Monta una sección dentro del contenedor (carga sus datos por su cuenta). */
export type SectionRenderer = (container: HTMLElement) => void;
export type ConfigSections = Readonly<Record<SeccionId, SectionRenderer>>;

export const SECCIONES: readonly { readonly id: SeccionId; readonly titulo: string }[] = [
  { id: 'consultorio', titulo: 'Consultorio' },
  { id: 'perfil', titulo: 'Perfil' },
  { id: 'prestaciones', titulo: 'Prestaciones' },
  { id: 'horarios', titulo: 'Horarios' },
  { id: 'bloqueos', titulo: 'Bloqueos' },
];

const BASE = '/configuracion';

function renderPagina(container: HTMLElement, activa: SeccionId, secciones: ConfigSections): void {
  const heading = document.createElement('h1');
  heading.textContent = 'Configuración';

  const nav = document.createElement('nav');
  nav.setAttribute('aria-label', 'Secciones de configuración');
  for (const { id, titulo } of SECCIONES) {
    const link = document.createElement('a');
    link.href = `${BASE}/${id}`;
    link.dataset['link'] = '';
    link.textContent = titulo;
    if (id === activa) link.setAttribute('aria-current', 'page');
    nav.append(link);
  }

  const contenido = document.createElement('section');
  container.replaceChildren(heading, nav, contenido);
  secciones[activa](contenido);
}

/** Rutas privadas de configuración: una por sección, porque el router compara el path exacto (D6). */
export function createConfigRoutes(secciones: ConfigSections): Route[] {
  const ruta = (path: string, seccion: SeccionId): Route => ({
    path,
    access: 'private',
    render: (container) => renderPagina(container, seccion, secciones),
  });
  return [ruta(BASE, 'consultorio'), ...SECCIONES.map(({ id }) => ruta(`${BASE}/${id}`, id))];
}
