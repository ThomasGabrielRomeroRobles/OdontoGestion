import type { ConfigApi, Perfil } from '../config-api';
import { toFormErrors } from '../form-errors';
import { validarPasswordNueva } from '../validators';
import type { SectionRenderer } from './config-page';
import {
  conBotonOcupado,
  crearAlerta,
  crearBoton,
  crearCampo,
  crearEstado,
  limpiarErrores,
  marcarError,
  mostrarErrorApi,
} from './form-kit';

export type PerfilSectionDeps = Pick<ConfigApi, 'obtenerPerfil' | 'actualizarPerfil' | 'cambiarPassword'>;

function crearFormPerfil(deps: PerfilSectionDeps): { form: HTMLFormElement; cargar: (perfil: Perfil) => void; alerta: HTMLElement } {
  const form = document.createElement('form');
  form.noValidate = true;
  form.dataset['form'] = 'perfil';
  const titulo = document.createElement('h2');
  titulo.textContent = 'Mis datos';
  const email = crearCampo('email', 'Email de acceso', { type: 'email', readOnly: true });
  const nombre = crearCampo('nombre', 'Nombre');
  const apellido = crearCampo('apellido', 'Apellido');
  const matricula = crearCampo('matricula', 'Matrícula');
  const campos = { nombre, apellido, matricula };
  const alerta = crearAlerta();
  const estado = crearEstado();
  const guardar = crearBoton('Guardar datos', 'submit');
  guardar.disabled = true;
  form.append(titulo, email.wrapper, nombre.wrapper, apellido.wrapper, matricula.wrapper, alerta, estado, guardar);

  function cargar(perfil: Perfil): void {
    email.input.value = perfil.email;
    nombre.input.value = perfil.nombre;
    apellido.input.value = perfil.apellido;
    matricula.input.value = perfil.matricula;
    guardar.disabled = false;
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    limpiarErrores(Object.values(campos), alerta);
    estado.textContent = '';
    const datos = {
      nombre: nombre.input.value.trim(),
      apellido: apellido.input.value.trim(),
      matricula: matricula.input.value.trim(),
    };
    void conBotonOcupado(guardar, () => deps.actualizarPerfil(datos))
      .then((perfil) => {
        cargar(perfil);
        estado.textContent = 'Cambios guardados.';
      })
      .catch((error: unknown) => mostrarErrorApi(error, campos, alerta));
  });

  return { form, cargar, alerta };
}

function crearFormPassword(deps: PerfilSectionDeps): HTMLFormElement {
  const form = document.createElement('form');
  form.noValidate = true;
  form.dataset['form'] = 'password';
  const titulo = document.createElement('h2');
  titulo.textContent = 'Cambiar contraseña';
  const actual = crearCampo('passwordActual', 'Contraseña actual', { type: 'password', autocomplete: 'current-password' });
  const nueva = crearCampo('passwordNueva', 'Nueva contraseña', { type: 'password', autocomplete: 'new-password' });
  const repetida = crearCampo('passwordRepetida', 'Repetí la nueva contraseña', { type: 'password', autocomplete: 'new-password' });
  const campos = { passwordActual: actual, passwordNueva: nueva, passwordRepetida: repetida };
  const alerta = crearAlerta();
  const estado = crearEstado();
  const cambiar = crearBoton('Cambiar contraseña', 'submit');
  form.append(titulo, actual.wrapper, nueva.wrapper, repetida.wrapper, alerta, estado, cambiar);

  // Las contraseñas no quedan en el DOM después de un intento (éxito o error).
  const vaciar = (): void => {
    for (const campo of Object.values(campos)) campo.input.value = '';
  };

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    limpiarErrores(Object.values(campos), alerta);
    estado.textContent = '';
    const passwordActual = actual.input.value;
    if (passwordActual === '') {
      marcarError(actual, 'Ingresá tu contraseña actual.');
      return;
    }
    const validacion = validarPasswordNueva(nueva.input.value, repetida.input.value);
    if (!validacion.ok) {
      marcarError(nueva.input.value === '' ? nueva : repetida, validacion.error);
      return;
    }
    void conBotonOcupado(cambiar, () => deps.cambiarPassword({ passwordActual, passwordNueva: validacion.value }))
      .then(() => {
        estado.textContent = 'Contraseña actualizada.';
      })
      .catch((error: unknown) => mostrarErrorApi(error, campos, alerta))
      .finally(vaciar);
  });

  return form;
}

export function createPerfilSection(deps: PerfilSectionDeps): SectionRenderer {
  return (container) => {
    const perfil = crearFormPerfil(deps);
    container.replaceChildren(perfil.form, crearFormPassword(deps));
    deps
      .obtenerPerfil()
      .then(perfil.cargar)
      .catch((error: unknown) => {
        perfil.alerta.textContent = toFormErrors(error).general ?? 'No se pudieron cargar los datos.';
      });
  };
}
