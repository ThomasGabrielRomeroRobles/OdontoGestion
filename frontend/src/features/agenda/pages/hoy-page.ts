import type { RouteContext } from '../../../shared/router/router';
import type { Turno } from '../agenda-api';
import { fechaLarga, fechaLocal } from '../calendario';
import type { AgendaDeps } from '../deps';
import { traducirErrorAccion } from '../errores';
import { esOcultoPorDefecto } from '../modelo';
import type { AlCambiar, PanelesAgenda } from './agenda-page';
import { alerta, boton, el } from './dom';
import { crearItemTurno } from './turno-item';

/** `/agenda/hoy` (US-024): turnos del día del consultorio; las acciones se operan desde el detalle. */
export function createHoyPage(deps: AgendaDeps, detalle: NonNullable<PanelesAgenda['detalle']>) {
  return (container: HTMLElement, _context: RouteContext): void => {
    const titulo = el('h1', 'Turnos de hoy');
    const fecha = el('p', fechaLarga(fechaLocal(deps.ahora())));
    const volver = el('a', 'Volver a la agenda', { href: '/agenda', 'data-link': '' });
    const actualizar = boton('Actualizar', () => void cargar());
    const aviso = alerta();
    const reintentar = boton('Reintentar', () => void cargar());
    reintentar.hidden = true;
    const panel = el('section', undefined, { 'data-panel': '', 'aria-live': 'polite' });
    const lista = el('ul');
    lista.classList.add('agenda__dia');
    const vacia = el('p', '');

    container.replaceChildren(titulo, fecha, volver, ' ', actualizar, aviso, reintentar, panel, vacia, lista);

    const alCambiar: AlCambiar = ({ cerrar, mensaje }) => {
      if (cerrar) panel.replaceChildren();
      void cargar().then(() => {
        if (mensaje !== undefined) aviso.textContent = mensaje;
      });
    };

    function abrir(turno: Turno): void {
      detalle(panel, turno, alCambiar);
    }

    async function cargar(): Promise<void> {
      try {
        const turnos = (await deps.api.turnosHoy())
          .filter((t) => !esOcultoPorDefecto(t.estado))
          .sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio));
        aviso.textContent = '';
        reintentar.hidden = true;
        vacia.textContent = turnos.length === 0 ? 'No hay turnos para hoy' : '';
        lista.replaceChildren(...turnos.map((t) => crearItemTurno(t, abrir)));
      } catch (error: unknown) {
        aviso.textContent = traducirErrorAccion(error).mensaje;
        reintentar.hidden = false;
      }
    }

    void cargar();
  };
}
