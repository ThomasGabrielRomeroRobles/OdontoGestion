/**
 * SOLO DESARROLLO (C-05 design D9): historia clínica en memoria para usar la ficha sin backend. Se carga con import
 * dinámico detrás de `import.meta.env.DEV`. Datos 100 % ficticios; no persiste nada en el navegador.
 */
import type {
  Anamnesis,
  Evolucion,
  Historia,
  HistoriaApi,
  Pieza,
} from '../features/historia-clinica/historia-api';
import { PIEZAS_PERMANENTES, hoyEnConsultorio } from '../features/historia-clinica/modelo';
import type { ApiError } from '../shared/http/client';

const AUTOR = { id: 1, nombre: 'Laura Gómez' };
const VENTANA_MS = 24 * 60 * 60 * 1000;

function problema(status: number, title: string): ApiError {
  return { kind: 'http', status, title, fieldErrors: [], extensions: {} };
}

interface FichaMock {
  paciente: Historia['paciente'];
  anamnesis: Anamnesis;
  piezas: Map<number, Pieza>;
  evoluciones: Evolucion[];
}

export function crearHistoriaApiMock(ahora: () => Date = () => new Date()): HistoriaApi {
  const fichas = new Map<number, FichaMock>();
  let siguienteId = 500;

  function ficha(pacienteId: number): FichaMock {
    if (pacienteId > 100_000) throw problema(404, 'Recurso no encontrado');
    let f = fichas.get(pacienteId);
    if (f === undefined) {
      f = {
        paciente: { id: pacienteId, nombre: 'Paciente', apellido: `Ficticio ${pacienteId}` },
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
        piezas: new Map(),
        evoluciones: [],
      };
      fichas.set(pacienteId, f);
    }
    return f;
  }

  const conEditable = (e: Evolucion): Evolucion => ({
    ...e,
    editable: ahora().getTime() - Date.parse(e.creadaEn) <= VENTANA_MS,
  });

  const ordenadas = (f: FichaMock): Evolucion[] =>
    [...f.evoluciones]
      .sort((a, b) => a.fecha.localeCompare(b.fecha) || a.creadaEn.localeCompare(b.creadaEn) || a.id - b.id)
      .map(conEditable);

  return {
    obtener: (pacienteId) => {
      try {
        const f = ficha(pacienteId);
        const piezas = PIEZAS_PERMANENTES.map(
          (fdi) => f.piezas.get(fdi) ?? { fdi, estado: null, observacion: '', actualizadoPor: null, actualizadoEn: null },
        );
        return Promise.resolve({ paciente: f.paciente, anamnesis: f.anamnesis, piezas, evoluciones: ordenadas(f) });
      } catch (e) {
        return Promise.reject(e);
      }
    },
    guardarAnamnesis: (pacienteId, datos) => {
      const f = ficha(pacienteId);
      f.anamnesis = { ...datos, actualizadoPor: AUTOR, actualizadoEn: ahora().toISOString() };
      return Promise.resolve(f.anamnesis);
    },
    guardarPieza: (pacienteId, fdi, datos) => {
      if (!PIEZAS_PERMANENTES.includes(fdi)) return Promise.reject(problema(422, 'Regla de negocio incumplida'));
      const p: Pieza = { fdi, ...datos, actualizadoPor: AUTOR, actualizadoEn: ahora().toISOString() };
      ficha(pacienteId).piezas.set(fdi, p);
      return Promise.resolve(p);
    },
    listarEvoluciones: (pacienteId) => Promise.resolve(ordenadas(ficha(pacienteId))),
    crearEvolucion: (pacienteId, datos) => {
      const fecha = datos.fecha ?? hoyEnConsultorio(ahora());
      if (fecha > hoyEnConsultorio(ahora())) return Promise.reject(problema(422, 'Regla de negocio incumplida'));
      siguienteId += 1;
      const e: Evolucion = {
        id: siguienteId,
        fecha,
        nota: datos.nota.trim(),
        turnoId: datos.turnoId ?? null,
        autor: AUTOR,
        creadaEn: ahora().toISOString(),
        editadaEn: null,
        editable: true,
      };
      ficha(pacienteId).evoluciones.push(e);
      return Promise.resolve(e);
    },
    editarEvolucion: (pacienteId, evolucionId, datos) => {
      const f = ficha(pacienteId);
      const i = f.evoluciones.findIndex((e) => e.id === evolucionId);
      const actual = f.evoluciones[i];
      if (actual === undefined) return Promise.reject(problema(404, 'Recurso no encontrado'));
      if (!conEditable(actual).editable) return Promise.reject(problema(422, 'Regla de negocio incumplida'));
      const editada: Evolucion = {
        ...actual,
        nota: datos.nota.trim(),
        fecha: datos.fecha ?? actual.fecha,
        editadaEn: ahora().toISOString(),
      };
      f.evoluciones[i] = editada;
      return Promise.resolve(conEditable(editada));
    },
  };
}
