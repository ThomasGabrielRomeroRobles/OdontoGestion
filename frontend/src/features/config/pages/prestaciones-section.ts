import type { AgendaConfigApi, Prestacion } from '../agenda-config-api';
import type { ConfigApi } from '../config-api';
import { toFormErrors } from '../form-errors';
import { validarDuracion, validarNombrePrestacion } from '../validators';
import type { SectionRenderer } from './config-page';
import {
  conBotonOcupado,
  crearAlerta,
  crearBoton,
  crearCampo,
  limpiarErrores,
  marcarError,
  mostrarErrorApi,
} from './form-kit';

export type PrestacionesSectionDeps = Pick<
  AgendaConfigApi,
  'listarPrestaciones' | 'crearPrestacion' | 'actualizarPrestacion' | 'desactivarPrestacion' | 'activarPrestacion'
> &
  Pick<ConfigApi, 'obtenerConsultorio'> & {
    readonly confirmar: (mensaje: string) => boolean;
  };

const porNombre = (a: Prestacion, b: Prestacion): number => a.nombre.localeCompare(b.nombre, 'es');

export function createPrestacionesSection(deps: PrestacionesSectionDeps): SectionRenderer {
  return (container) => {
    let prestaciones: Prestacion[] = [];
    // `null` = desconocida: se valida solo entero positivo y decide el servidor (D8).
    let granularidad: number | null = null;
    let editandoId: number | null = null;

    const titulo = document.createElement('h2');
    titulo.textContent = 'Prestaciones';
    const alerta = crearAlerta();
    const lista = document.createElement('ul');
    const vacia = document.createElement('p');

    const form = document.createElement('form');
    form.noValidate = true;
    form.dataset['form'] = 'prestacion';
    const tituloForm = document.createElement('h3');
    const nombre = crearCampo('nombre', 'Nombre');
    const duracion = crearCampo('duracionMin', 'Duración (min)', { type: 'number', min: '1', step: '1' });
    const campos = { nombre, duracionMin: duracion };
    const guardar = crearBoton('', 'submit');
    const cancelar = crearBoton('Cancelar edición');
    form.append(tituloForm, nombre.wrapper, duracion.wrapper, guardar, cancelar);

    container.replaceChildren(titulo, alerta, vacia, lista, form);

    function modoAlta(): void {
      editandoId = null;
      tituloForm.textContent = 'Nueva prestación';
      guardar.textContent = 'Agregar';
      cancelar.hidden = true;
      nombre.input.value = '';
      duracion.input.value = '';
      limpiarErrores(Object.values(campos), alerta);
    }

    function modoEdicion(prestacion: Prestacion): void {
      editandoId = prestacion.id;
      tituloForm.textContent = `Editar «${prestacion.nombre}»`;
      guardar.textContent = 'Guardar cambios';
      cancelar.hidden = false;
      nombre.input.value = prestacion.nombre;
      duracion.input.value = String(prestacion.duracionMin);
      limpiarErrores(Object.values(campos), alerta);
      nombre.input.focus();
    }

    function reemplazar(actualizada: Prestacion): void {
      prestaciones = [...prestaciones.filter((p) => p.id !== actualizada.id), actualizada].sort(porNombre);
      renderLista();
    }

    function cambiarEstado(prestacion: Prestacion, boton: HTMLButtonElement): void {
      const activar = !prestacion.activa;
      if (!activar && !deps.confirmar(`¿Desactivar «${prestacion.nombre}»? No se ofrecerá para turnos nuevos.`)) return;
      alerta.textContent = '';
      const accion = activar ? deps.activarPrestacion : deps.desactivarPrestacion;
      void conBotonOcupado(boton, () => accion(prestacion.id))
        .then(reemplazar)
        .catch((error: unknown) => {
          alerta.textContent = toFormErrors(error).general ?? 'No se pudo cambiar el estado.';
        });
    }

    function renderFila(prestacion: Prestacion): HTMLLIElement {
      const li = document.createElement('li');
      li.dataset['id'] = String(prestacion.id);
      const colNombre = document.createElement('span');
      colNombre.dataset['col'] = 'nombre';
      colNombre.textContent = prestacion.nombre;
      const colDuracion = document.createElement('span');
      colDuracion.textContent = ` · ${prestacion.duracionMin} min · `;
      const colEstado = document.createElement('span');
      colEstado.textContent = prestacion.activa ? 'Activa' : 'Inactiva';
      const editar = crearBoton('Editar');
      editar.addEventListener('click', () => modoEdicion(prestacion));
      const estado = crearBoton(prestacion.activa ? 'Desactivar' : 'Activar');
      estado.addEventListener('click', () => cambiarEstado(prestacion, estado));
      li.append(colNombre, colDuracion, colEstado, ' ', editar, ' ', estado);
      return li;
    }

    function renderLista(): void {
      lista.replaceChildren(...prestaciones.map(renderFila));
      vacia.textContent = prestaciones.length === 0 ? 'No hay prestaciones cargadas. Creá la primera con el formulario.' : '';
    }

    cancelar.addEventListener('click', modoAlta);

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      limpiarErrores(Object.values(campos), alerta);
      const vNombre = validarNombrePrestacion(nombre.input.value);
      const vDuracion = validarDuracion(duracion.input.value, granularidad);
      if (!vNombre.ok) marcarError(nombre, vNombre.error);
      if (!vDuracion.ok) marcarError(duracion, vDuracion.error);
      if (!vNombre.ok || !vDuracion.ok) return;

      const datos = { nombre: vNombre.value, duracionMin: vDuracion.value };
      const id = editandoId;
      const peticion = id === null ? () => deps.crearPrestacion(datos) : () => deps.actualizarPrestacion(id, datos);
      void conBotonOcupado(guardar, peticion)
        .then((guardada) => {
          reemplazar(guardada);
          modoAlta();
        })
        .catch((error: unknown) => mostrarErrorApi(error, campos, alerta));
    });

    modoAlta();

    deps
      .obtenerConsultorio()
      .then((consultorio) => {
        granularidad = consultorio.granularidadMin;
      })
      .catch(() => {
        granularidad = null;
      });

    deps
      .listarPrestaciones()
      .then((lista) => {
        prestaciones = [...lista].sort(porNombre);
        renderLista();
      })
      .catch((error: unknown) => {
        alerta.textContent = toFormErrors(error).general ?? 'No se pudieron cargar las prestaciones.';
      });
  };
}
