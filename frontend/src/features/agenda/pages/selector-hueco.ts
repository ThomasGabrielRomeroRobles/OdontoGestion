import type { Prestacion } from '../../config/agenda-config-api';
import type { AgendaApi, Hueco } from '../agenda-api';
import { horaLocal } from '../calendario';
import { traducirErrorAccion } from '../errores';
import { boton, el } from './dom';

export type FuentePrestaciones =
  | { readonly tipo: 'lista'; readonly cargar: () => Promise<Prestacion[]> }
  | { readonly tipo: 'fija'; readonly id: number };

export interface OpcionesSelectorHueco {
  readonly disponibilidad: AgendaApi['disponibilidad'];
  readonly prestaciones: FuentePrestaciones;
}

export interface SeleccionHueco {
  readonly prestacionId: number;
  readonly hueco: Hueco;
}

export interface SelectorHueco {
  readonly elemento: HTMLElement;
  seleccion(): SeleccionHueco | null;
  /** Vuelve a pedir la disponibilidad de la prestación y fecha elegidas; limpia la selección. */
  refrescar(): Promise<void>;
  /** Muestra las `alternativas` de un 409 como opciones elegibles. */
  mostrarAlternativas(alternativas: readonly Hueco[]): void;
}

let secuencia = 0;

/** "Prestación → fecha → huecos" reutilizado por nuevo turno y mover (design D6). */
export function crearSelectorHueco(opciones: OpcionesSelectorHueco): SelectorHueco {
  secuencia += 1;
  const idFecha = `selector-fecha-${secuencia}`;
  const elemento = el('div', undefined, { 'data-selector-hueco': '' });
  const fecha = el('input', undefined, { type: 'date', name: 'fecha', id: idFecha });
  const listaHuecos = el('div', undefined, { 'data-huecos': '' });
  const estado = el('p', '', { 'data-estado-huecos': '' });
  const listaAlternativas = el('div', undefined, { 'data-alternativas': '' });

  let prestacionId: number | null = opciones.prestaciones.tipo === 'fija' ? opciones.prestaciones.id : null;
  let elegido: Hueco | null = null;
  let pedido = 0;

  const select = opciones.prestaciones.tipo === 'lista' ? el('select', undefined, { name: 'prestacionId' }) : null;
  if (select !== null) {
    const idSelect = `selector-prestacion-${secuencia}`;
    select.id = idSelect;
    select.append(el('option', 'Elegí una prestación', { value: '' }));
    elemento.append(el('label', 'Prestación', { for: idSelect }), select);
    select.addEventListener('change', () => {
      prestacionId = select.value === '' ? null : Number(select.value);
      listaAlternativas.replaceChildren();
      void refrescar();
    });
  }
  elemento.append(el('label', 'Fecha', { for: idFecha }), fecha, estado, listaHuecos, listaAlternativas);
  fecha.addEventListener('change', () => {
    listaAlternativas.replaceChildren();
    void refrescar();
  });

  if (opciones.prestaciones.tipo === 'lista' && select !== null) {
    opciones.prestaciones
      .cargar()
      .then((lista) => {
        select.append(
          ...lista
            .filter((p) => p.activa)
            .map((p) => el('option', `${p.nombre} (${p.duracionMin} min)`, { value: String(p.id) })),
        );
      })
      .catch(() => {
        estado.textContent = 'No se pudieron cargar las prestaciones.';
      });
  }

  function marcarElegido(): void {
    for (const b of elemento.querySelectorAll<HTMLButtonElement>('[data-inicio]')) {
      b.setAttribute('aria-pressed', String(elegido !== null && b.dataset['inicio'] === elegido.inicio));
    }
  }

  function botonHueco(hueco: Hueco): HTMLButtonElement {
    const b = boton(horaLocal(hueco.inicio), () => {
      elegido = hueco;
      marcarElegido();
    });
    b.dataset['inicio'] = hueco.inicio;
    b.setAttribute('aria-pressed', 'false');
    return b;
  }

  async function refrescar(): Promise<void> {
    elegido = null;
    listaHuecos.replaceChildren();
    estado.textContent = '';
    if (prestacionId === null || fecha.value === '') return;
    pedido += 1;
    const este = pedido;
    try {
      const huecos = await opciones.disponibilidad(prestacionId, fecha.value);
      if (este !== pedido) return;
      if (huecos.length === 0) estado.textContent = 'No hay horarios libres para esa fecha';
      listaHuecos.replaceChildren(...huecos.map(botonHueco));
      marcarElegido();
    } catch (error: unknown) {
      if (este === pedido) estado.textContent = traducirErrorAccion(error).mensaje;
    }
  }

  return {
    elemento,
    seleccion: () => (prestacionId === null || elegido === null ? null : { prestacionId, hueco: elegido }),
    refrescar,
    mostrarAlternativas(alternativas) {
      if (alternativas.length === 0) {
        listaAlternativas.replaceChildren();
        return;
      }
      listaAlternativas.replaceChildren(el('p', 'Alternativas:'), ...alternativas.map(botonHueco));
      marcarElegido();
    },
  };
}
