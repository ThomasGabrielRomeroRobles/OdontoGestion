import type { HuecoLibre, PrestacionPublica, SolicitudTurno } from './publico-api';

/** Fixtures tipadas desde `contrato-c09.md` (C-09). Datos 100 % ficticios. */
export const consulta: PrestacionPublica = { id: 3, nombre: 'Consulta', duracionMin: 30 };
export const limpieza: PrestacionPublica = { id: 4, nombre: 'Limpieza', duracionMin: 45 };
export const prestaciones: readonly PrestacionPublica[] = [consulta, limpieza];

/** 10:00 del lunes 23/11/2026 en Buenos Aires (UTC-3). */
export const hueco10: HuecoLibre = { inicio: '2026-11-23T13:00:00Z', fin: '2026-11-23T13:30:00Z' };
export const hueco1015: HuecoLibre = { inicio: '2026-11-23T13:15:00Z', fin: '2026-11-23T13:45:00Z' };
export const huecos: readonly HuecoLibre[] = [hueco10, hueco1015];

export const alternativas: readonly HuecoLibre[] = [
  { inicio: '2026-11-23T13:30:00Z', fin: '2026-11-23T14:00:00Z' },
  { inicio: '2026-11-23T13:45:00Z', fin: '2026-11-23T14:15:00Z' },
];

export const solicitudValida: SolicitudTurno = {
  prestacionId: 3,
  inicio: '2026-11-23T13:00:00Z',
  nombre: 'Ana',
  apellido: 'Ficticia',
  dni: '30.123.456',
  email: 'ana@ejemplo.test',
  telefono: '+54 11 5555-0000',
  sitioWeb: '',
};

export const mensajeGenerico = 'Si los datos son correctos, vas a recibir un email para confirmar el turno.';

const TIPO = 'https://odontogestion.example/problemas';

/** Cuerpos `application/problem+json` del contrato. */
export const problema409 = {
  type: `${TIPO}/hueco-no-disponible`,
  title: 'Horario no disponible',
  status: 409,
  detail: 'El horario elegido ya no está disponible.',
  instance: '/api/v1/publico/solicitudes-turno',
  alternativas,
};

export const problema429 = {
  type: `${TIPO}/demasiadas-solicitudes`,
  title: 'Demasiadas solicitudes',
  status: 429,
  detail: 'Demasiadas solicitudes. Intente nuevamente más tarde.',
};

export const problema422Inicio = {
  type: `${TIPO}/regla-de-negocio`,
  title: 'Regla de negocio incumplida',
  status: 422,
  detail: 'La solicitud no cumple las reglas de negocio.',
  errors: [{ field: 'inicio', message: 'Debe quedar dentro de un horario de trabajo.' }],
};

export const problema400Dni = {
  title: 'Solicitud inválida',
  status: 400,
  detail: 'La solicitud contiene datos inválidos.',
  errors: [{ field: 'dni', message: 'Debe tener 7 u 8 dígitos.' }],
};
