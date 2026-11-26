import { alerta, boton, el } from '../agenda/pages/dom';
import type { Evolucion, HistoriaApi } from './historia-api';
import { fechaCivil, hoyEnConsultorio, mensajeProblema } from './modelo';

export interface OpcionesEvoluciones {
  readonly api: Pick<HistoriaApi, 'crearEvolucion' | 'editarEvolucion' | 'listarEvoluciones'>;
  readonly pacienteId: number;
  readonly evoluciones: readonly Evolucion[];
  readonly ahora: () => Date;
}

const NOTA_OBLIGATORIA = 'La nota es obligatoria.';

/**
 * Notas de evolución (US-072): listado en el orden de la API (cronológico), alta con nota y fecha (hoy por defecto)
 * y edición solo de las que la API marca editables.
 */
export function crearEvoluciones({ api, pacienteId, evoluciones, ahora }: OpcionesEvoluciones): HTMLElement {
  let lista: Evolucion[] = [...evoluciones];
  const seccion = el('section', undefined, { 'data-evoluciones': '' });
  const contenedor = el('div', undefined, { 'data-lista': '' });
  const aviso = alerta();

  function item(e: Evolucion): HTMLElement {
    const li = el('li', undefined, { 'data-evolucion-id': String(e.id) });
    const encabezado = `${fechaCivil(e.fecha)} — ${e.autor.nombre}${e.editadaEn === null ? '' : ' (editada)'}`;
    li.append(el('p', encabezado, { class: 'evolucion__meta' }), el('p', e.nota, { 'data-nota': '' }));
    if (e.editable) li.append(boton('Editar', () => editar(li, e)));
    return li;
  }

  function render(): void {
    if (lista.length === 0) {
      contenedor.replaceChildren(el('p', 'Sin evoluciones registradas'));
      return;
    }
    const ol = el('ol');
    ol.append(...lista.map(item));
    contenedor.replaceChildren(ol);
  }

  async function recargar(): Promise<void> {
    try {
      lista = [...(await api.listarEvoluciones(pacienteId))];
    } finally {
      render();
    }
  }

  function editar(li: HTMLElement, e: Evolucion): void {
    const area = el('textarea', undefined, { name: 'nota-edicion', maxlength: '4000' });
    area.value = e.nota;
    const avisoItem = alerta();
    const guardar = boton('Guardar', () => {
      const nota = area.value.trim();
      if (nota === '') {
        avisoItem.textContent = NOTA_OBLIGATORIA;
        return;
      }
      guardar.disabled = true;
      api.editarEvolucion(pacienteId, e.id, { nota })
        .then((editada) => {
          lista = lista.map((x) => (x.id === editada.id ? editada : x));
          render();
        })
        .catch(async (error: unknown) => {
          aviso.textContent = mensajeProblema(error);
          await recargar().catch(() => undefined);
        });
    });
    li.replaceChildren(area, avisoItem, guardar, boton('Cancelar', render));
  }

  const form = el('form', undefined, { novalidate: '', 'data-alta-evolucion': '' });
  const nota = el('textarea', undefined, { name: 'nota', maxlength: '4000' });
  const fecha = el('input', undefined, { type: 'date', name: 'fecha' });
  const hoy = hoyEnConsultorio(ahora());
  fecha.value = hoy;
  fecha.max = hoy;
  const labelNota = el('label', 'Nueva evolución ');
  labelNota.append(nota);
  const labelFecha = el('label', 'Fecha ');
  labelFecha.append(fecha);
  const avisoAlta = alerta();
  const agregar = el('button', 'Agregar evolución', { type: 'submit' });
  form.append(labelNota, labelFecha, avisoAlta, agregar);

  form.addEventListener('submit', (evento) => {
    evento.preventDefault();
    avisoAlta.textContent = '';
    const texto = nota.value.trim();
    if (texto === '') {
      avisoAlta.textContent = NOTA_OBLIGATORIA;
      return;
    }
    agregar.disabled = true;
    api.crearEvolucion(pacienteId, { nota: texto, fecha: fecha.value === '' ? hoy : fecha.value })
      .then((creada) => {
        lista = [...lista, creada];
        nota.value = '';
        render();
      })
      .catch((error: unknown) => {
        avisoAlta.textContent = mensajeProblema(error);
      })
      .finally(() => {
        agregar.disabled = false;
      });
  });

  render();
  seccion.append(el('h2', 'Evoluciones'), aviso, contenedor, form);
  return seccion;
}
