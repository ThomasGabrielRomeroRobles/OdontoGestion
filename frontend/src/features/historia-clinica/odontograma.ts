import { formatearInstante } from '../config/zoned-time';
import { alerta, boton, el } from '../agenda/pages/dom';
import type { HistoriaApi, Pieza } from './historia-api';
import {
  ESTADOS_PIEZA,
  FILA_INFERIOR,
  FILA_SUPERIOR,
  claseEstado,
  esEstadoPieza,
  etiquetaEstado,
  mensajeProblema,
} from './modelo';

export interface OpcionesOdontograma {
  readonly api: Pick<HistoriaApi, 'guardarPieza'>;
  readonly pacienteId: number;
  readonly piezas: readonly Pieza[];
}

export function textoActualizacion(nombre: string | undefined, instante: string | null): string {
  if (nombre === undefined || instante === null) return '';
  return `Actualizada por ${nombre} el ${formatearInstante(instante)}`;
}

/**
 * Odontograma FDI de 32 piezas (US-071): dos filas de 16 en orden de cuadrante. Cada pieza muestra su número y su
 * estado por texto y por clase CSS. Al elegirla se abre el editor; guardar actualiza solo esa pieza.
 */
export function crearOdontograma({ api, pacienteId, piezas }: OpcionesOdontograma): HTMLElement {
  const porFdi = new Map<number, Pieza>(piezas.map((p) => [p.fdi, p]));
  const seccion = el('section', undefined, { 'data-odontograma': '' });
  const editor = el('div', undefined, { 'data-editor-pieza': '' });
  const botones = new Map<number, HTMLButtonElement>();

  function pintar(boton: HTMLButtonElement, fdi: number): void {
    const p = porFdi.get(fdi);
    const estado = p?.estado ?? null;
    boton.className = `pieza ${claseEstado(estado)}`;
    boton.setAttribute('aria-label', `Pieza ${fdi}: ${etiquetaEstado(estado)}`);
    boton.replaceChildren(el('span', String(fdi), { class: 'pieza__numero' }),
      el('span', etiquetaEstado(estado), { 'data-estado-pieza': '' }));
  }

  function fila(numeros: readonly number[], nombre: string): HTMLElement {
    const contenedor = el('div', undefined, { class: 'odontograma__fila', 'data-fila': nombre });
    for (const fdi of numeros) {
      const b = el('button', undefined, { type: 'button', 'data-fdi': String(fdi) });
      pintar(b, fdi);
      b.addEventListener('click', () => abrirEditor(fdi));
      botones.set(fdi, b);
      contenedor.append(b);
    }
    return contenedor;
  }

  function abrirEditor(fdi: number): void {
    const actual = porFdi.get(fdi);
    const select = el('select', undefined, { name: 'estado' });
    for (const estado of ESTADOS_PIEZA) {
      const opcion = el('option', etiquetaEstado(estado), { value: estado });
      opcion.selected = estado === (actual?.estado ?? 'SANA');
      select.append(opcion);
    }
    const observacion = el('textarea', undefined, { name: 'observacion', maxlength: '500' });
    observacion.value = actual?.observacion ?? '';
    const meta = el('p', textoActualizacion(actual?.actualizadoPor?.nombre, actual?.actualizadoEn ?? null),
      { 'data-meta': '' });
    const aviso = alerta();
    const etiquetaEstadoCampo = el('label', 'Estado ');
    etiquetaEstadoCampo.append(select);
    const etiquetaObs = el('label', 'Observación ');
    etiquetaObs.append(observacion);

    const guardar = boton('Guardar', () => {
      const estado = select.value;
      if (!esEstadoPieza(estado)) return;
      guardar.disabled = true;
      aviso.textContent = '';
      api.guardarPieza(pacienteId, fdi, { estado, observacion: observacion.value.trim() })
        .then((guardada) => {
          porFdi.set(fdi, guardada);
          const b = botones.get(fdi);
          if (b !== undefined) pintar(b, fdi);
          meta.textContent = textoActualizacion(guardada.actualizadoPor?.nombre, guardada.actualizadoEn);
        })
        .catch((error: unknown) => {
          aviso.textContent = mensajeProblema(error);
        })
        .finally(() => {
          guardar.disabled = false;
        });
    });
    const cerrar = boton('Cerrar', () => editor.replaceChildren());
    editor.replaceChildren(el('h3', `Pieza ${fdi}`), etiquetaEstadoCampo, etiquetaObs, meta, aviso, guardar, cerrar);
  }

  seccion.append(el('h2', 'Odontograma'), fila(FILA_SUPERIOR, 'superior'), fila(FILA_INFERIOR, 'inferior'), editor);
  return seccion;
}
