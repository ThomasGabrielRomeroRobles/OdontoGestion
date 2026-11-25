/**
 * SOLO DESARROLLO: API de lista de espera en memoria. Avisos de demostración: `aviso` (se acepta), `aviso-ocupado`
 * (409 al aceptar) y `aviso-vencido` (410 al aceptar); cualquier otro token responde como un `410`. La inscripción
 * rechaza como el backend: rango invertido o fecha hasta pasada (`invalida` en `fechaHasta`) y prestación que no está
 * entre las del mock público (`prestacionNoDisponible`, el `404`). Datos 100 % ficticios.
 */
import type {
  EntradaListaEspera,
  InscripcionListaEspera,
  ListaEsperaApi,
  ResultadoInscripcion,
  ResumenAviso,
} from '../features/lista-espera/lista-espera-api';
import { hoyEnConsultorio, MENSAJE_INSCRIPCION } from '../features/lista-espera/inscripcion-page';

export interface ListaEsperaMockOpciones {
  /** Prestaciones que acepta la inscripción; por defecto, las de `crearPublicoApiMock` (1 y 2). */
  readonly prestacionIds?: readonly number[];
  readonly ahora?: () => Date;
}

const HUECO: Omit<ResumenAviso, 'disponible'> = {
  consultorio: 'Consultorio Demo',
  prestacion: 'Consulta',
  inicio: '2026-11-23T13:00:00Z',
  fin: '2026-11-23T13:30:00Z',
  venceEn: '2026-11-23T12:00:00Z',
};

function validarInscripcion(i: InscripcionListaEspera, prestacionIds: readonly number[], hoy: string): ResultadoInscripcion {
  if (i.fechaHasta < i.fechaDesde) {
    return { tipo: 'invalida', fieldErrors: [{ field: 'fechaHasta', message: 'No puede ser anterior a la fecha desde.' }] };
  }
  if (i.fechaHasta < hoy) {
    return { tipo: 'invalida', fieldErrors: [{ field: 'fechaHasta', message: 'No puede ser anterior a hoy.' }] };
  }
  if (!prestacionIds.includes(i.prestacionId)) return { tipo: 'prestacionNoDisponible' };
  return { tipo: 'aceptada', mensaje: MENSAJE_INSCRIPCION };
}

export function crearListaEsperaApiMock(opciones: ListaEsperaMockOpciones = {}): ListaEsperaApi {
  const prestacionIds = opciones.prestacionIds ?? [1, 2];
  const ahora = opciones.ahora ?? ((): Date => new Date());
  let entradas: readonly EntradaListaEspera[] = [
    { id: 3, paciente: 'Quispe, Zoilo', prestacion: 'Consulta', fechaDesde: '2026-11-23', fechaHasta: '2026-12-02', estado: 'ACTIVA', creadoEn: '2026-11-21T12:00:00Z' },
    { id: 2, paciente: 'Ficticia, Ana', prestacion: 'Limpieza', fechaDesde: '2026-11-24', fechaHasta: '2026-11-30', estado: 'PENDIENTE_CONFIRMACION', creadoEn: '2026-11-20T12:00:00Z' },
    { id: 1, paciente: 'Demo, Beto', prestacion: 'Consulta', fechaDesde: '2026-11-10', fechaHasta: '2026-11-15', estado: 'CUBIERTA', creadoEn: '2026-11-09T12:00:00Z' },
  ];
  return {
    inscribir: (inscripcion) => Promise.resolve(validarInscripcion(inscripcion, prestacionIds, hoyEnConsultorio(ahora()))),
    obtenerAviso: (token) => {
      if (token === 'aviso' || token === 'aviso-vencido') return Promise.resolve({ tipo: 'resumen', resumen: { ...HUECO, disponible: true } });
      if (token === 'aviso-ocupado') return Promise.resolve({ tipo: 'resumen', resumen: { ...HUECO, disponible: false } });
      return Promise.resolve({ tipo: 'noDisponible' });
    },
    aceptarAviso: (token) => {
      if (token === 'aviso') {
        return Promise.resolve({ tipo: 'reservado', turno: { prestacion: HUECO.prestacion, inicio: HUECO.inicio, fin: HUECO.fin, estado: 'RESERVADO' } });
      }
      return Promise.resolve(token === 'aviso-ocupado' ? { tipo: 'noDisponible' } : { tipo: 'vencido' });
    },
    listar: () => Promise.resolve(entradas),
    retirar: (id) => {
      entradas = entradas.map((e) => (e.id === id ? { ...e, estado: 'CANCELADA' } : e));
      return Promise.resolve();
    },
  };
}
