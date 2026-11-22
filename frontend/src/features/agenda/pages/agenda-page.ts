import type { RouteContext } from '../../../shared/router/router';
import type { Turno } from '../agenda-api';
import {
  agruparPorDia,
  diasDeSemana,
  etiquetaDia,
  fechaCorta,
  fechaLocal,
  parsearVistaYFecha,
  rangoDia,
  rangoSemana,
  sumarDias,
  urlAgenda,
  type Vista,
} from '../calendario';
import type { AgendaDeps } from '../deps';
import { traducirErrorAccion } from '../errores';
import { esOcultoPorDefecto } from '../modelo';
import { alerta, boton, el } from './dom';
import { crearItemTurno } from './turno-item';

/** Hooks para abrir paneles sobre la agenda (detalle, nuevo turno). Los completa `index.ts`. */
export interface PanelesAgenda {
  readonly detalle?: (host: HTMLElement, turno: Turno, alCambiar: AlCambiar) => void;
  readonly nuevoTurno?: (host: HTMLElement, alCrear: (turno: Turno) => void, cerrar: () => void) => void;
}

/** Tras una acción: recargar la vista y, si corresponde, cerrar el panel y dejar un mensaje en la vista. */
export type AlCambiar = (opciones: { readonly cerrar: boolean; readonly mensaje?: string }) => void;

/** `/agenda?vista=dia|semana&fecha=yyyy-MM-dd` (design D4). */
export function createAgendaPage(deps: AgendaDeps, paneles: PanelesAgenda = {}) {
  return (container: HTMLElement, context: RouteContext): void => {
    const hoy = fechaLocal(deps.ahora());
    const { vista, fecha, normalizado } = parsearVistaYFecha(context.url.searchParams, hoy);
    if (normalizado) window.history.replaceState(null, '', urlAgenda(vista, fecha));

    let turnos: Turno[] = [];
    let mostrarCancelados = false;

    const ir = (v: Vista, f: string): void => context.navigate(urlAgenda(v, f));
    const paso = vista === 'dia' ? 1 : 7;
    const dias = vista === 'dia' ? [fecha] : diasDeSemana(fecha);
    const rango = vista === 'dia' ? rangoDia(fecha) : rangoSemana(fecha);

    const titulo = el('h1', 'Agenda');
    const periodo = el(
      'p',
      vista === 'dia' ? etiquetaDia(fecha) : `Semana del ${fechaCorta(dias[0] ?? fecha)} al ${fechaCorta(dias[6] ?? fecha)}`,
      { 'data-periodo': '' },
    );

    const botonDia = boton('Día', () => ir('dia', fecha));
    const botonSemana = boton('Semana', () => ir('semana', fecha));
    botonDia.setAttribute('aria-pressed', String(vista === 'dia'));
    botonSemana.setAttribute('aria-pressed', String(vista === 'semana'));

    const enlaceHoy = el('a', 'Turnos de hoy', { href: '/agenda/hoy', 'data-link': '' });
    const control = el('input', undefined, { type: 'checkbox', name: 'mostrarCancelados', id: 'agenda-mostrar-cancelados' });
    const etiquetaControl = el('label', 'Mostrar cancelados y expirados', { for: 'agenda-mostrar-cancelados' });
    control.addEventListener('change', () => {
      mostrarCancelados = control.checked;
      renderTurnos();
    });

    const barra = el('div', undefined, { role: 'toolbar', 'aria-label': 'Navegación de la agenda' });
    barra.append(
      botonDia,
      botonSemana,
      boton('Anterior', () => ir(vista, sumarDias(fecha, -paso))),
      boton('Hoy', () => ir(vista, hoy)),
      boton('Siguiente', () => ir(vista, sumarDias(fecha, paso))),
      boton('Actualizar', () => cargar()),
      boton('Nuevo turno', () => abrirNuevoTurno()),
      enlaceHoy,
      control,
      etiquetaControl,
    );

    const aviso = alerta();
    const reintentar = boton('Reintentar', () => cargar());
    reintentar.hidden = true;
    const panel = el('section', undefined, { 'data-panel': '', 'aria-live': 'polite' });
    const grilla = el('div', undefined, { 'data-vista': vista });
    grilla.classList.add('agenda', `agenda--${vista}`);

    container.replaceChildren(titulo, periodo, barra, aviso, reintentar, panel, grilla);

    function cerrarPanel(): void {
      panel.replaceChildren();
    }

    const alCambiar: AlCambiar = ({ cerrar, mensaje }) => {
      if (cerrar) cerrarPanel();
      void cargar().then(() => {
        if (mensaje !== undefined) aviso.textContent = mensaje;
      });
    };

    function abrirDetalle(turno: Turno): void {
      if (paneles.detalle === undefined) return;
      paneles.detalle(panel, turno, alCambiar);
    }

    function abrirNuevoTurno(): void {
      if (paneles.nuevoTurno === undefined) return;
      paneles.nuevoTurno(
        panel,
        (creado) => {
          cerrarPanel();
          context.navigate(urlAgenda('dia', fechaLocal(creado.inicio)));
        },
        cerrarPanel,
      );
    }

    function renderTurnos(): void {
      const visibles = mostrarCancelados ? turnos : turnos.filter((t) => !esOcultoPorDefecto(t.estado));
      const porDia = agruparPorDia(visibles);
      grilla.replaceChildren(
        ...dias.map((dia) => {
          const columna = el('section', undefined, { 'data-dia': dia });
          columna.classList.add('agenda__dia');
          columna.append(el('h2', etiquetaDia(dia)));
          const delDia = porDia.get(dia) ?? [];
          if (delDia.length === 0) columna.append(el('p', 'Sin turnos'));
          else {
            const lista = el('ul');
            lista.append(...delDia.map((t) => crearItemTurno(t, abrirDetalle)));
            columna.append(lista);
          }
          return columna;
        }),
      );
    }

    async function cargar(): Promise<void> {
      try {
        turnos = await deps.api.listarAgenda(rango.desde, rango.hasta);
        aviso.textContent = '';
        reintentar.hidden = true;
      } catch (error: unknown) {
        aviso.textContent = traducirErrorAccion(error).mensaje;
        reintentar.hidden = false;
      }
      renderTurnos();
    }

    renderTurnos();
    void cargar();
  };
}
