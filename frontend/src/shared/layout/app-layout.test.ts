import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createSessionStore, type SessionStore } from '../state/session';
import { createAppLayout } from './app-layout';

describe('layout autenticado', () => {
  let host: HTMLElement;
  let session: SessionStore;
  let navigate: ReturnType<typeof vi.fn<(to: string) => void>>;
  let logout: ReturnType<typeof vi.fn<() => Promise<void>>>;
  let outlet: HTMLElement;

  beforeEach(() => {
    host = document.createElement('div');
    session = createSessionStore();
    session.setAuthenticated('odontologo@consultorio.example');
    navigate = vi.fn<(to: string) => void>();
    logout = vi.fn<() => Promise<void>>();
    outlet = createAppLayout({ session, logout, navigate })(host, {
      status: 'authenticated',
      email: 'odontologo@consultorio.example',
    });
  });

  function logoutButton(): HTMLButtonElement {
    const button = [...host.querySelectorAll('button')].find((b) => b.textContent === 'Cerrar sesión');
    if (button === undefined) throw new Error('falta el botón Cerrar sesión');
    return button;
  }

  it('muestra el email de la sesión y la acción Cerrar sesión', () => {
    expect(host.textContent).toContain('odontologo@consultorio.example');
    expect(logoutButton()).toBeDefined();
  });

  it('devuelve un contenedor dentro del layout para la vista', () => {
    expect(host.contains(outlet)).toBe(true);
    expect(outlet.tagName).toBe('MAIN');
  });

  it('muestra otro email cuando cambia la sesión', () => {
    const otro = document.createElement('div');
    createAppLayout({ session, logout, navigate })(otro, { status: 'authenticated', email: 'otra@consultorio.example' });
    expect(otro.textContent).toContain('otra@consultorio.example');
    expect(otro.textContent).not.toContain('odontologo@consultorio.example');
  });

  it('la navegación usa enlaces data-link', () => {
    const links = [...host.querySelectorAll('a[data-link]')].map((a) => a.getAttribute('href'));
    expect(links).toContain('/agenda');
    expect(links).toContain('/pacientes');
  });

  it('incluye el enlace Configuración a /configuracion', () => {
    const link = [...host.querySelectorAll<HTMLAnchorElement>('a[data-link]')].find((a) => a.textContent === 'Configuración');
    expect(link?.getAttribute('href')).toBe('/configuracion');
  });

  it('logout con 204 deja la sesión anónima y navega a /login', async () => {
    logout.mockResolvedValue(undefined);
    logoutButton().click();

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/login'));
    expect(logout).toHaveBeenCalledTimes(1);
    expect(session.get()).toEqual({ status: 'anonymous' });
  });

  it('logout con error 500 igualmente deja la sesión anónima y navega a /login', async () => {
    logout.mockRejectedValue({ kind: 'http', status: 500, title: 'Error interno', fieldErrors: [], extensions: {} });
    logoutButton().click();

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/login'));
    expect(session.get()).toEqual({ status: 'anonymous' });
  });
});
