import type { AgendaApi, Turno } from '../agenda-api';
import { traducirErrorAccion, type ErrorTraducido } from '../errores';
import { alerta, boton, el } from './dom';

const MOTIVO_MAX = 200;

export interface CancelarTurnoOpciones {
  readonly turno: Turno;
  readonly cancelar: AgendaApi['cancelarTurno'];
  readonly alCancelar: (turno: Turno) => void;
  readonly alVolver: () => void;
  /** Se invoca con el error ya traducido (además de mostrarlo acá), para recargar o cerrar (D7). */
  readonly alError: (error: ErrorTraducido) => void;
}

/** Diálogo de cancelación (US-023): motivo opcional ≤ 200 y confirmación explícita. */
export function crearCancelarTurno(opciones: CancelarTurnoOpciones): HTMLElement {
  const contenedor = el('div', undefined, { 'data-dialogo': 'cancelar' });
  const id = `motivo-cancelacion-${opciones.turno.id}`;
  const etiqueta = el('label', 'Motivo (opcional)', { for: id });
  const motivo = el('textarea', undefined, { name: 'motivo', id, rows: '2' });
  const error = el('p', '', { 'data-error-for': 'motivo' });
  const aviso = alerta();
  const confirmar = boton('Confirmar cancelación');
  const volver = boton('Volver', () => opciones.alVolver());

  confirmar.addEventListener('click', () => {
    error.textContent = '';
    aviso.textContent = '';
    const texto = motivo.value.trim();
    if (texto.length > MOTIVO_MAX) {
      error.textContent = `Máximo ${MOTIVO_MAX} caracteres.`;
      return;
    }
    confirmar.disabled = true;
    opciones
      .cancelar(opciones.turno.id, texto === '' ? null : texto)
      .then((cancelado) => opciones.alCancelar(cancelado))
      .catch((e: unknown) => {
        const traducido = traducirErrorAccion(e);
        aviso.textContent = traducido.mensaje;
        opciones.alError(traducido);
      })
      .finally(() => {
        confirmar.disabled = false;
      });
  });

  contenedor.append(el('h3', '¿Cancelar el turno?'), etiqueta, motivo, error, aviso, confirmar, ' ', volver);
  return contenedor;
}
