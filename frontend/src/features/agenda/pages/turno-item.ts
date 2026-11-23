import type { Turno } from '../agenda-api';
import { horaLocal } from '../calendario';
import { claseEstado, etiquetaEstado } from '../modelo';
import { el } from './dom';

export function rangoHorario(turno: Turno): string {
  return `${horaLocal(turno.inicio)}–${horaLocal(turno.fin)}`;
}

export function nombrePaciente(turno: Turno): string {
  return `${turno.paciente.apellido}, ${turno.paciente.nombre}`;
}

/** Ítem de la agenda: hora, paciente, prestación, etiqueta de estado (texto + estilo) y "N aus." (D4, D9). */
export function crearItemTurno(turno: Turno, alAbrir: (turno: Turno) => void): HTMLLIElement {
  const li = el('li');
  li.classList.add('turno', claseEstado(turno.estado));
  li.dataset['turnoId'] = String(turno.id);
  const abrir = el('button', undefined, { type: 'button' });
  abrir.classList.add('turno__abrir');
  abrir.append(
    el('span', rangoHorario(turno), { 'data-hora': '' }),
    ' ',
    el('span', nombrePaciente(turno), { 'data-paciente': '' }),
    ' · ',
    el('span', turno.prestacion.nombre, { 'data-prestacion': '' }),
    ' ',
    el('span', etiquetaEstado(turno.estado), { 'data-estado': '' }),
  );
  const ausencias = turno.paciente.cantidadAusencias;
  if (ausencias > 0) {
    abrir.append(' ', el('span', `${ausencias} aus.`, { 'data-ausencias': '' }));
  }
  abrir.addEventListener('click', () => alAbrir(turno));
  li.append(abrir);
  return li;
}
