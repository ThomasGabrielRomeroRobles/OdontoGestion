import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CambioPassword, Perfil, PerfilEditable } from '../config-api';
import { enviar, error422, errorDe, escribir, input, perfilFicticio } from '../test-fixtures';
import { createPerfilSection } from './perfil-section';

describe('sección Perfil (8.2)', () => {
  let container: HTMLElement;
  let obtener: ReturnType<typeof vi.fn<() => Promise<Perfil>>>;
  let actualizar: ReturnType<typeof vi.fn<(input: PerfilEditable) => Promise<Perfil>>>;
  let cambiar: ReturnType<typeof vi.fn<(input: CambioPassword) => Promise<void>>>;

  beforeEach(async () => {
    container = document.createElement('div');
    obtener = vi.fn<() => Promise<Perfil>>().mockResolvedValue(perfilFicticio);
    actualizar = vi.fn<(input: PerfilEditable) => Promise<Perfil>>();
    cambiar = vi.fn<(input: CambioPassword) => Promise<void>>();
    createPerfilSection({ obtenerPerfil: obtener, actualizarPerfil: actualizar, cambiarPassword: cambiar })(container);
    await vi.waitFor(() => expect(input(container, 'matricula').value).toBe('MN 0000'));
  });

  const formPerfil = (): HTMLFormElement | null => container.querySelector('form[data-form="perfil"]');
  const formPassword = (): HTMLFormElement | null => container.querySelector('form[data-form="password"]');
  const estadoDe = (form: HTMLFormElement | null): string => form?.querySelector('[role="status"]')?.textContent ?? '';
  const alertaDe = (form: HTMLFormElement | null): string => form?.querySelector('[role="alert"]')?.textContent ?? '';

  function cargarPasswords(actual: string, nueva: string, repetida: string): void {
    escribir(container, 'passwordActual', actual);
    escribir(container, 'passwordNueva', nueva);
    escribir(container, 'passwordRepetida', repetida);
  }

  it('muestra el email como solo lectura', () => {
    const email = input(container, 'email');
    expect(email.value).toBe('odontologo@consultorio.example');
    expect(email.readOnly).toBe(true);
  });

  it('edición del perfil: envía PUT y confirma', async () => {
    actualizar.mockResolvedValue({ ...perfilFicticio, matricula: 'MN 1111' });
    escribir(container, 'matricula', 'MN 1111');
    enviar(formPerfil());

    await vi.waitFor(() => expect(estadoDe(formPerfil())).toBe('Cambios guardados.'));
    expect(actualizar).toHaveBeenCalledWith({ nombre: 'Ana', apellido: 'Pérez', matricula: 'MN 1111' });
  });

  it('los campos de contraseña son de tipo password con autocomplete adecuado', () => {
    expect(input(container, 'passwordActual').type).toBe('password');
    expect(input(container, 'passwordActual').autocomplete).toBe('current-password');
    expect(input(container, 'passwordNueva').type).toBe('password');
    expect(input(container, 'passwordNueva').autocomplete).toBe('new-password');
    expect(input(container, 'passwordRepetida').type).toBe('password');
  });

  it('contraseñas que no coinciden: no envía e indica el error', () => {
    cargarPasswords('vieja-ficticia', 'nueva-ficticia', 'otra-ficticia');
    enviar(formPassword());

    expect(cambiar).not.toHaveBeenCalled();
    expect(errorDe(container, 'passwordRepetida')).toBe('Las contraseñas no coinciden.');
  });

  it('contraseña actual vacía: no envía', () => {
    cargarPasswords('', 'nueva-ficticia', 'nueva-ficticia');
    enviar(formPassword());

    expect(cambiar).not.toHaveBeenCalled();
    expect(errorDe(container, 'passwordActual')).toBe('Ingresá tu contraseña actual.');
  });

  it('204: confirma y vacía los tres campos', async () => {
    cambiar.mockResolvedValue(undefined);
    cargarPasswords('vieja-ficticia', 'nueva-ficticia', 'nueva-ficticia');
    enviar(formPassword());

    await vi.waitFor(() => expect(estadoDe(formPassword())).toBe('Contraseña actualizada.'));
    expect(cambiar).toHaveBeenCalledWith({ passwordActual: 'vieja-ficticia', passwordNueva: 'nueva-ficticia' });
    for (const name of ['passwordActual', 'passwordNueva', 'passwordRepetida']) {
      expect(input(container, name).value).toBe('');
    }
  });

  it('422 en passwordActual: muestra el error junto al campo y vacía los campos', async () => {
    cambiar.mockRejectedValue(error422('passwordActual', 'La contraseña actual es incorrecta'));
    cargarPasswords('incorrecta', 'nueva-ficticia', 'nueva-ficticia');
    enviar(formPassword());

    await vi.waitFor(() => expect(errorDe(container, 'passwordActual')).toBe('La contraseña actual es incorrecta'));
    for (const name of ['passwordActual', 'passwordNueva', 'passwordRepetida']) {
      expect(input(container, name).value).toBe('');
    }
  });

  it('falla de red al cambiar contraseña: mensaje de conexión', async () => {
    cambiar.mockRejectedValue({ kind: 'network' });
    cargarPasswords('vieja-ficticia', 'nueva-ficticia', 'nueva-ficticia');
    enviar(formPassword());

    await vi.waitFor(() => expect(alertaDe(formPassword())).toContain('No se pudo conectar'));
  });
});
