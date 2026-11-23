import type { EntradaListaEspera, InscripcionListaEspera, ResumenAviso } from './lista-espera-api';

/** Fixtures tipadas desde el contrato de C-10 (`design.md` D2/D5/D7). Datos 100 % ficticios. */
export const inscripcionValida: InscripcionListaEspera = {
  prestacionId: 3,
  fechaDesde: '2026-11-23',
  fechaHasta: '2026-12-02',
  nombre: 'Ana',
  apellido: 'Ficticia',
  dni: '30.123.456',
  email: 'ana@ejemplo.test',
  telefono: '+54 11 5555-0000',
  sitioWeb: '',
};

export const resumenAviso: ResumenAviso = {
  consultorio: 'Consultorio Ficticio',
  prestacion: 'Consulta',
  /** 10:00 del lunes 23/11/2026 en Buenos Aires (UTC-3). */
  inicio: '2026-11-23T13:00:00Z',
  fin: '2026-11-23T13:30:00Z',
  venceEn: '2026-11-23T12:00:00Z',
  disponible: true,
};

export const entradaActiva: EntradaListaEspera = {
  id: 11,
  paciente: 'Ficticia, Ana',
  prestacion: 'Consulta',
  fechaDesde: '2026-11-23',
  fechaHasta: '2026-12-02',
  estado: 'ACTIVA',
  creadoEn: '2026-11-20T12:00:00Z',
};

export const entradaCubierta: EntradaListaEspera = { ...entradaActiva, id: 10, estado: 'CUBIERTA', paciente: 'Quispe, Zoilo' };
export const entradaPendiente: EntradaListaEspera = { ...entradaActiva, id: 12, estado: 'PENDIENTE_CONFIRMACION' };

const TIPO = 'https://odontogestion.example/problemas';

export const problema410 = { type: `${TIPO}/token-invalido`, title: 'Enlace no disponible', status: 410, detail: 'El enlace no es válido o ya fue utilizado.' };
export const problema409 = { type: `${TIPO}/aviso-no-disponible`, title: 'Conflicto', status: 409, detail: 'El horario ya no está disponible.' };
