import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { RouteContext } from '../../../shared/router/router';
import { createConfigRoutes, SECCIONES, type ConfigSections, type SeccionId } from './config-page';

function seccionesDobles(): ConfigSections & Record<SeccionId, ReturnType<typeof vi.fn>> {
  const crear = (id: SeccionId) =>
    vi.fn((container: HTMLElement) => {
      const p = document.createElement('p');
      p.textContent = `contenido-${id}`;
      container.replaceChildren(p);
    });
  return {
    consultorio: crear('consultorio'),
    perfil: crear('perfil'),
    prestaciones: crear('prestaciones'),
    horarios: crear('horarios'),
    bloqueos: crear('bloqueos'),
  };
}

function ctx(path: string): RouteContext {
  return { url: new URL(`http://localhost${path}`), navigate: vi.fn() };
}

describe('página de configuración (7.1)', () => {
  let container: HTMLElement;
  let secciones: ReturnType<typeof seccionesDobles>;

  beforeEach(() => {
    container = document.createElement('div');
    secciones = seccionesDobles();
  });

  function render(path: string): void {
    const route = createConfigRoutes(secciones).find((r) => r.path === path);
    if (route === undefined) throw new Error(`sin ruta ${path}`);
    route.render(container, ctx(path));
  }

  function pestañas(): HTMLAnchorElement[] {
    return [...container.querySelectorAll<HTMLAnchorElement>('nav a')];
  }

  it('registra /configuracion y una ruta privada por sección', () => {
    const rutas = createConfigRoutes(secciones);
    expect(rutas.map((r) => r.path)).toEqual([
      '/configuracion',
      '/configuracion/consultorio',
      '/configuracion/perfil',
      '/configuracion/prestaciones',
      '/configuracion/horarios',
      '/configuracion/bloqueos',
    ]);
    expect(rutas.every((r) => r.access === 'private')).toBe(true);
  });

  it('/configuracion muestra las 5 pestañas con Consultorio activa', () => {
    render('/configuracion');
    expect(pestañas().map((a) => a.textContent)).toEqual(['Consultorio', 'Perfil', 'Prestaciones', 'Horarios', 'Bloqueos']);
    const activa = pestañas().filter((a) => a.getAttribute('aria-current') === 'page');
    expect(activa.map((a) => a.textContent)).toEqual(['Consultorio']);
    expect(secciones.consultorio).toHaveBeenCalledTimes(1);
    expect(container.textContent).toContain('contenido-consultorio');
  });

  it('/configuracion/horarios activa Horarios y solo monta esa sección', () => {
    render('/configuracion/horarios');
    const activa = pestañas().find((a) => a.getAttribute('aria-current') === 'page');
    expect(activa?.textContent).toBe('Horarios');
    expect(secciones.horarios).toHaveBeenCalledTimes(1);
    expect(secciones.consultorio).not.toHaveBeenCalled();
  });

  it('las pestañas son enlaces data-link a la URL de cada sección', () => {
    render('/configuracion');
    expect(pestañas().every((a) => a.hasAttribute('data-link'))).toBe(true);
    expect(pestañas().map((a) => a.getAttribute('href'))).toEqual(SECCIONES.map((s) => `/configuracion/${s.id}`));
  });

  it('tiene un título de página', () => {
    render('/configuracion/perfil');
    expect(container.querySelector('h1')?.textContent).toBe('Configuración');
  });
});
