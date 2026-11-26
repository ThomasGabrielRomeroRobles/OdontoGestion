import type { Autor, Evolucion, Historia, Pieza } from './historia-api';
import { PIEZAS_PERMANENTES } from './modelo';

/** Datos 100 % ficticios para los tests de la historia clínica. */
export const AUTOR: Autor = { id: 1, nombre: 'Laura Gómez' };

export function pieza(fdi: number, cambios: Partial<Pieza> = {}): Pieza {
  return { fdi, estado: null, observacion: '', actualizadoPor: null, actualizadoEn: null, ...cambios };
}

export function evolucion(id: number, cambios: Partial<Evolucion> = {}): Evolucion {
  return {
    id,
    fecha: '2026-11-20',
    nota: `Nota ficticia ${id}`,
    turnoId: null,
    autor: AUTOR,
    creadaEn: '2026-11-20T13:00:00Z',
    editadaEn: null,
    editable: true,
    ...cambios,
  };
}

export function historia(cambios: Partial<Historia> = {}): Historia {
  return {
    paciente: { id: 7, nombre: 'Ana', apellido: 'Pérez' },
    anamnesis: {
      alergias: '',
      medicacionActual: '',
      enfermedadesPrevias: '',
      antecedentesRelevantes: '',
      embarazo: null,
      observaciones: '',
      actualizadoPor: null,
      actualizadoEn: null,
    },
    piezas: PIEZAS_PERMANENTES.map((fdi) => pieza(fdi)),
    evoluciones: [],
    ...cambios,
  };
}
