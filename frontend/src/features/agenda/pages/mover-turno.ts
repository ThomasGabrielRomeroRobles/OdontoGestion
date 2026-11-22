import type { Turno } from '../agenda-api';
import type { AgendaDeps } from '../deps';
import { traducirErrorAccion, type ErrorTraducido } from '../errores';
import { alerta, boton, el } from './dom';
import { crearSelectorHueco } from './selector-hueco';

/** Subpanel "Mover" (US-022, design D6): huecos de la misma prestación excluyendo el propio turno. */
export function createMoverTurno(deps: AgendaDeps) {
  return (
    turno: Turno,
    alMovido: (turno: Turno) => void,
    alVolver: () => void,
    alError: (error: ErrorTraducido) => void,
  ): HTMLElement => {
    const contenedor = el('div', undefined, { 'data-dialogo': 'mover' });
    const selector = crearSelectorHueco({
      disponibilidad: deps.api.disponibilidad,
      prestaciones: { tipo: 'fija', id: turno.prestacion.id },
      excluirTurnoId: turno.id,
    });
    const aviso = alerta();
    const confirmar = boton('Confirmar movimiento');
    const volver = boton('Volver', alVolver);

    confirmar.addEventListener('click', () => {
      aviso.textContent = '';
      const eleccion = selector.seleccion();
      if (eleccion === null) {
        aviso.textContent = 'Elegí fecha y horario.';
        return;
      }
      confirmar.disabled = true;
      deps.api
        .moverTurno(turno.id, eleccion.hueco.inicio)
        .then(alMovido)
        .catch((error: unknown) => {
          const traducido = traducirErrorAccion(error);
          aviso.textContent = traducido.mensaje;
          if (traducido.conflicto) {
            void selector.refrescar();
            selector.mostrarAlternativas(traducido.alternativas);
          }
          alError(traducido);
        })
        .finally(() => {
          confirmar.disabled = false;
        });
    });

    contenedor.append(el('h3', `Mover turno de ${turno.prestacion.nombre}`), selector.elemento, aviso, confirmar, ' ', volver);
    return contenedor;
  };
}
