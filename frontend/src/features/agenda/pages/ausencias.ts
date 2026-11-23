import { formatearInstante } from '../../config/zoned-time';
import type { AgendaApi } from '../agenda-api';
import { el } from './dom';

export interface ComponenteAusencias {
  readonly elemento: HTMLElement;
  recargar(): Promise<void>;
}

/**
 * Historial de ausencias del paciente (US-061, design D9): cantidad y fechas en hora del consultorio,
 * la más reciente primero. Reutilizable por la futura pantalla de pacientes.
 */
export function crearAusencias(ausenciasPaciente: AgendaApi['ausenciasPaciente'], pacienteId: number): ComponenteAusencias {
  const elemento = el('section', undefined, { 'data-ausencias-paciente': '' });
  const titulo = el('h3', 'Ausencias');

  async function recargar(): Promise<void> {
    try {
      const { cantidad, ausencias } = await ausenciasPaciente(pacienteId);
      if (cantidad === 0 && ausencias.length === 0) {
        elemento.replaceChildren(titulo, el('p', 'Sin ausencias registradas'));
        return;
      }
      const lista = el('ul');
      const ordenadas = [...ausencias].sort((a, b) => Date.parse(b.inicio) - Date.parse(a.inicio));
      lista.append(...ordenadas.map((a) => el('li', `${formatearInstante(a.inicio)} — ${a.prestacionNombre}`)));
      elemento.replaceChildren(titulo, el('p', `Ausencias: ${cantidad}`, { 'data-cantidad': '' }), lista);
    } catch {
      elemento.replaceChildren(titulo, el('p', 'No se pudieron cargar las ausencias.'));
    }
  }

  elemento.append(titulo);
  void recargar();
  return { elemento, recargar };
}
