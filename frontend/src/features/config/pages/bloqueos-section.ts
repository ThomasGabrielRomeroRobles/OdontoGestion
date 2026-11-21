import type { AgendaConfigApi, Bloqueo } from '../agenda-config-api';
import { toFormErrors } from '../form-errors';
import { formatearInstante, instanteAInputLocal, localAInstante } from '../zoned-time';
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

export type BloqueosSectionDeps = Pick<
  AgendaConfigApi,
  'listarBloqueos' | 'crearBloqueo' | 'actualizarBloqueo' | 'eliminarBloqueo'
> & { readonly confirmar: (mensaje: string) => boolean };

const MOTIVO_MAX = 200;
const porDesde = (a: Bloqueo, b: Bloqueo): number => Date.parse(a.desde) - Date.parse(b.desde);
const describir = (b: Bloqueo): string => `${formatearInstante(b.desde)} → ${formatearInstante(b.hasta)}`;

export function createBloqueosSection(deps: BloqueosSectionDeps): SectionRenderer {
  return (container) => {
    let bloqueos: Bloqueo[] = [];
    let editandoId: number | null = null;

    const titulo = document.createElement('h2');
    titulo.textContent = 'Bloqueos de agenda';
    const ayuda = document.createElement('p');
    ayuda.textContent = 'Fechas y horas en hora del consultorio (Buenos Aires). Un bloqueo impide nuevas reservas en su rango.';
    const alerta = crearAlerta();
    const vacia = document.createElement('p');
    const lista = document.createElement('ul');

    const form = document.createElement('form');
    form.noValidate = true;
    form.dataset['form'] = 'bloqueo';
    const tituloForm = document.createElement('h3');
    const desde = crearCampo('desde', 'Desde', { type: 'datetime-local' });
    const hasta = crearCampo('hasta', 'Hasta', { type: 'datetime-local' });
    const motivo = crearCampo('motivo', 'Motivo (opcional)');
    motivo.input.maxLength = MOTIVO_MAX;
    const campos = { desde, hasta, motivo };
    const guardar = crearBoton('', 'submit');
    const cancelar = crearBoton('Cancelar edición');
    form.append(tituloForm, desde.wrapper, hasta.wrapper, motivo.wrapper, guardar, cancelar);

    container.replaceChildren(titulo, ayuda, alerta, vacia, lista, form);

    function modoAlta(): void {
      editandoId = null;
      tituloForm.textContent = 'Nuevo bloqueo';
      guardar.textContent = 'Agregar';
      cancelar.hidden = true;
      for (const campo of Object.values(campos)) campo.input.value = '';
      limpiarErrores(Object.values(campos), alerta);
    }

    function modoEdicion(bloqueo: Bloqueo): void {
      editandoId = bloqueo.id;
      tituloForm.textContent = 'Editar bloqueo';
      guardar.textContent = 'Guardar cambios';
      cancelar.hidden = false;
      desde.input.value = instanteAInputLocal(bloqueo.desde);
      hasta.input.value = instanteAInputLocal(bloqueo.hasta);
      motivo.input.value = bloqueo.motivo ?? '';
      limpiarErrores(Object.values(campos), alerta);
    }

    function eliminar(bloqueo: Bloqueo, boton: HTMLButtonElement): void {
      if (!deps.confirmar(`¿Eliminar el bloqueo ${describir(bloqueo)}?`)) return;
      alerta.textContent = '';
      void conBotonOcupado(boton, () => deps.eliminarBloqueo(bloqueo.id))
        .then(() => {
          bloqueos = bloqueos.filter((b) => b.id !== bloqueo.id);
          if (editandoId === bloqueo.id) modoAlta();
          renderLista();
        })
        .catch((error: unknown) => {
          alerta.textContent = toFormErrors(error).general ?? 'No se pudo eliminar el bloqueo.';
        });
    }

    function renderFila(bloqueo: Bloqueo): HTMLLIElement {
      const li = document.createElement('li');
      li.dataset['id'] = String(bloqueo.id);
      const texto = document.createElement('span');
      // Sin motivo puede llegar como "" (el backend nunca devuelve null, D12) o null: ambos son "sin motivo".
      const motivoTexto = bloqueo.motivo ?? '';
      texto.textContent = motivoTexto === '' ? describir(bloqueo) : `${describir(bloqueo)} · ${motivoTexto}`;
      const editar = crearBoton('Editar');
      editar.addEventListener('click', () => modoEdicion(bloqueo));
      const borrar = crearBoton('Eliminar');
      borrar.addEventListener('click', () => eliminar(bloqueo, borrar));
      li.append(texto, ' ', editar, ' ', borrar);
      return li;
    }

    function renderLista(): void {
      lista.replaceChildren(...bloqueos.map(renderFila));
      vacia.textContent = bloqueos.length === 0 ? 'No hay bloqueos vigentes ni futuros.' : '';
    }

    cancelar.addEventListener('click', modoAlta);

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      limpiarErrores(Object.values(campos), alerta);
      const instanteDesde = localAInstante(desde.input.value);
      const instanteHasta = localAInstante(hasta.input.value);
      const textoMotivo = motivo.input.value.trim();
      let valido = true;
      if (instanteDesde === null) {
        marcarError(desde, 'Ingresá fecha y hora.');
        valido = false;
      }
      if (instanteHasta === null) {
        marcarError(hasta, 'Ingresá fecha y hora.');
        valido = false;
      } else if (instanteDesde !== null && Date.parse(instanteHasta) <= Date.parse(instanteDesde)) {
        marcarError(hasta, 'El fin debe ser posterior al inicio.');
        valido = false;
      }
      if (textoMotivo.length > MOTIVO_MAX) {
        marcarError(motivo, `El motivo admite hasta ${MOTIVO_MAX} caracteres.`);
        valido = false;
      }
      if (!valido || instanteDesde === null || instanteHasta === null) return;

      const datos = { desde: instanteDesde, hasta: instanteHasta, motivo: textoMotivo === '' ? null : textoMotivo };
      const id = editandoId;
      const peticion = id === null ? () => deps.crearBloqueo(datos) : () => deps.actualizarBloqueo(id, datos);
      void conBotonOcupado(guardar, peticion)
        .then((guardado) => {
          bloqueos = [...bloqueos.filter((b) => b.id !== guardado.id), guardado].sort(porDesde);
          modoAlta();
          renderLista();
        })
        .catch((error: unknown) => mostrarErrorApi(error, campos, alerta));
    });

    modoAlta();

    deps
      .listarBloqueos()
      .then((lista) => {
        bloqueos = [...lista].sort(porDesde);
        renderLista();
      })
      .catch((error: unknown) => {
        alerta.textContent = toFormErrors(error).general ?? 'No se pudieron cargar los bloqueos.';
      });
  };
}
