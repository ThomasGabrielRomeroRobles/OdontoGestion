/**
 * SOLO DESARROLLO: API pública en memoria para trabajar `/publico` sin backend (se carga con import dinámico
 * detrás de `import.meta.env.DEV`). Datos 100 % ficticios. El DNI `99.999.999` fuerza un `409` con alternativas.
 */
import type { HuecoLibre, PublicoApi } from '../features/publico/publico-api';
import { MENSAJE_ACEPTADA } from '../features/publico/publico-page';

const DNI_CONFLICTO = '99999999';
const MEDIA_HORA_MS = 30 * 60 * 1000;

/** Seis huecos de media hora desde las 09:00 de Buenos Aires (12:00Z) del día pedido. */
function huecosDelDia(fecha: string): HuecoLibre[] {
  const base = Date.parse(`${fecha}T12:00:00Z`);
  if (Number.isNaN(base)) return [];
  return Array.from({ length: 6 }, (_, i) => ({
    inicio: new Date(base + i * MEDIA_HORA_MS).toISOString().replace('.000Z', 'Z'),
    fin: new Date(base + (i + 1) * MEDIA_HORA_MS).toISOString().replace('.000Z', 'Z'),
  }));
}

export function crearPublicoApiMock(): PublicoApi {
  return {
    listarPrestaciones: () =>
      Promise.resolve([
        { id: 1, nombre: 'Consulta', duracionMin: 30 },
        { id: 2, nombre: 'Limpieza', duracionMin: 45 },
      ]),
    consultarDisponibilidad: (_prestacionId, fecha) => Promise.resolve(huecosDelDia(fecha)),
    solicitarTurno: (solicitud) => {
      if (solicitud.dni.replace(/\./g, '') === DNI_CONFLICTO) {
        return Promise.resolve({ tipo: 'conflicto', alternativas: huecosDelDia(solicitud.inicio.slice(0, 10)).slice(4) });
      }
      return Promise.resolve({ tipo: 'aceptada', mensaje: MENSAJE_ACEPTADA });
    },
  };
}
