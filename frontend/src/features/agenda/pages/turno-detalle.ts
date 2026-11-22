import type { Turno } from '../agenda-api';
import { fechaCorta, fechaLocal } from '../calendario';
import type { AgendaDeps } from '../deps';
import { traducirErrorAccion, type ErrorTraducido } from '../errores';
import { accionesHabilitadas, claseEstado, etiquetaEstado, type Accion } from '../modelo';
import type { AlCambiar } from './agenda-page';
import { crearAusencias } from './ausencias';
import { crearCancelarTurno } from './cancelar-turno';
import { alerta, boton, el } from './dom';
import { nombrePaciente, rangoHorario } from './turno-item';

/** Subpanel "Mover" (grupo 7): se monta dentro del detalle. */
export type CrearMover = (
  turno: Turno,
  alMovido: (turno: Turno) => void,
  alVolver: () => void,
  alError: (error: ErrorTraducido) => void,
) => HTMLElement;

export interface OpcionesDetalle {
  readonly mover?: CrearMover;
}

async function toleranciaDe(deps: AgendaDeps): Promise<number> {
  try {
    return (await deps.obtenerConsultorio()).parametros.toleranciaAusenteMin;
  } catch {
    return 0; // design D5: el servidor decide con su 422.
  }
}

/** Panel de detalle del turno: datos, acciones habilitadas y ausencias del paciente (D5, D7, D9). */
export function createTurnoDetalle(deps: AgendaDeps, opciones: OpcionesDetalle = {}) {
  return (host: HTMLElement, inicial: Turno, alCambiar: AlCambiar): void => {
    let turno = inicial;
    let tolerancia: number | null = null;

    const titulo = el('h2', 'Turno');
    const datos = el('div', undefined, { 'data-datos': '' });
    const aviso = alerta();
    const acciones = el('div', undefined, { 'data-acciones': '' });
    const nota = el('p', '', { 'data-nota-acciones': '' });
    const subpanel = el('div', undefined, { 'data-subpanel': '' });
    const ausencias = crearAusencias(deps.api.ausenciasPaciente, turno.paciente.id);
    const cerrar = boton('Cerrar', () => host.replaceChildren());

    host.replaceChildren(titulo, datos, aviso, acciones, nota, subpanel, ausencias.elemento, cerrar);

    function renderDatos(): void {
      const estado = el('span', etiquetaEstado(turno.estado), { 'data-estado': '' });
      estado.classList.add(claseEstado(turno.estado));
      const filas: HTMLElement[] = [
        el('p', `${fechaCorta(fechaLocal(turno.inicio))} ${rangoHorario(turno)}`),
        el('p', `${nombrePaciente(turno)} (DNI ${turno.paciente.dni})`),
        el('p', turno.prestacion.nombre),
        el('p'),
      ];
      filas[3]?.append('Estado: ', estado);
      if (turno.motivoCancelacion !== null && turno.motivoCancelacion !== '') {
        filas.push(el('p', `Motivo: ${turno.motivoCancelacion}`));
      }
      datos.replaceChildren(...filas);
    }

    function manejarError(error: ErrorTraducido): void {
      if (error.cerrar) {
        alCambiar({ cerrar: true, mensaje: error.mensaje });
        return;
      }
      aviso.textContent = error.mensaje;
      if (error.recargar) alCambiar({ cerrar: false });
    }

    /** El subpanel ya mostró el mensaje: acá solo se recarga o se cierra. */
    function propagarDeSubpanel(error: ErrorTraducido): void {
      if (error.cerrar) manejarError(error);
      else if (error.recargar) alCambiar({ cerrar: false });
    }

    function actualizado(nuevo: Turno, recargarAusencias: boolean): void {
      turno = nuevo;
      subpanel.replaceChildren();
      renderDatos();
      renderAcciones();
      if (recargarAusencias) void ausencias.recargar();
      alCambiar({ cerrar: false });
    }

    function ejecutar(boton: HTMLButtonElement, peticion: () => Promise<Turno>, recargarAusencias: boolean): void {
      aviso.textContent = '';
      boton.disabled = true;
      peticion()
        .then((nuevo) => actualizado(nuevo, recargarAusencias))
        .catch((e: unknown) => {
          boton.disabled = false;
          manejarError(traducirErrorAccion(e));
        });
    }

    function alActivar(accion: Accion, b: HTMLButtonElement): void {
      const nombre = nombrePaciente(turno);
      switch (accion) {
        case 'atendido':
          ejecutar(b, () => deps.api.marcarAtendido(turno.id), false);
          return;
        case 'ausente':
          if (deps.confirmar(`¿Marcar como ausente a ${nombre}?`)) ejecutar(b, () => deps.api.marcarAusente(turno.id), true);
          return;
        case 'revertir':
          if (deps.confirmar(`¿Revertir la ausencia de ${nombre}?`)) {
            ejecutar(b, () => deps.api.revertirAusencia(turno.id), true);
          }
          return;
        case 'cancelar':
          aviso.textContent = '';
          subpanel.replaceChildren(
            crearCancelarTurno({
              turno,
              cancelar: deps.api.cancelarTurno,
              alCancelar: () => alCambiar({ cerrar: true }),
              alVolver: () => subpanel.replaceChildren(),
              alError: (error) => {
                propagarDeSubpanel(error);
              },
            }),
          );
          return;
        case 'mover':
          if (opciones.mover === undefined) return;
          aviso.textContent = '';
          subpanel.replaceChildren(
            opciones.mover(
              turno,
              (movido) => actualizado(movido, false),
              () => subpanel.replaceChildren(),
              (error) => {
                propagarDeSubpanel(error);
              },
            ),
          );
          return;
      }
    }

    function renderAcciones(): void {
      if (tolerancia === null) return;
      const disponibles = accionesHabilitadas(turno, deps.ahora(), tolerancia);
      acciones.replaceChildren(
        ...disponibles.map((a) => {
          const b = boton(a.etiqueta);
          b.dataset['accion'] = a.accion;
          b.disabled = !a.habilitada;
          if (a.motivo !== undefined) b.title = a.motivo;
          b.addEventListener('click', () => alActivar(a.accion, b));
          return b;
        }),
      );
      nota.textContent = disponibles.flatMap((a) => (a.motivo === undefined ? [] : [`${a.etiqueta}: ${a.motivo}`])).join(' · ');
    }

    renderDatos();
    void toleranciaDe(deps).then((valor) => {
      tolerancia = valor;
      renderAcciones();
    });
  };
}
