/**
 * Conversión entre hora local del consultorio e instantes UTC sin librerías (design.md D7).
 * El offset se obtiene con Intl para cada instante, así no depende de la zona del navegador
 * ni asume que la zona no tenga horario de verano.
 */
export const ZONA_CONSULTORIO = 'America/Argentina/Buenos_Aires';

const LOCAL = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/;

interface Partes {
  readonly year: number;
  readonly month: number;
  readonly day: number;
  readonly hour: number;
  readonly minute: number;
}

const formateador = new Intl.DateTimeFormat('en-US', {
  timeZone: ZONA_CONSULTORIO,
  hourCycle: 'h23',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

export function partesEnZona(ms: number): Partes & { readonly second: number } {
  const valores: Record<string, number> = {};
  for (const parte of formateador.formatToParts(new Date(ms))) {
    if (parte.type !== 'literal') valores[parte.type] = Number(parte.value);
  }
  return {
    year: valores['year'] ?? 0,
    month: valores['month'] ?? 0,
    day: valores['day'] ?? 0,
    hour: valores['hour'] ?? 0,
    minute: valores['minute'] ?? 0,
    second: valores['second'] ?? 0,
  };
}

/** Offset (ms) de la zona del consultorio en el instante dado: local - UTC. */
function offsetEn(ms: number): number {
  const p = partesEnZona(ms);
  const comoUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return comoUtc - (ms - (ms % 1000));
}

const pad = (n: number): string => String(n).padStart(2, '0');

function sinMilisegundos(iso: string): string {
  return iso.replace(/\.\d{3}Z$/, 'Z');
}

/** `yyyy-MM-ddTHH:mm` en hora del consultorio → instante ISO UTC (`...Z`), o `null` si es inválido. */
export function localAInstante(valorLocal: string): string | null {
  const m = LOCAL.exec(valorLocal);
  if (m === null) return null;
  const [year, month, day, hour, minute] = m.slice(1).map(Number) as [number, number, number, number, number];
  const comoUtc = Date.UTC(year, month - 1, day, hour, minute);
  const verificacion = new Date(comoUtc);
  if (verificacion.getUTCMonth() !== month - 1 || verificacion.getUTCDate() !== day || hour > 23 || minute > 59) {
    return null;
  }
  // Dos pasadas: el offset correcto es el del instante resultante, no el del valor ingenuo.
  let instante = comoUtc - offsetEn(comoUtc);
  instante = comoUtc - offsetEn(instante);
  return sinMilisegundos(new Date(instante).toISOString());
}

/** Instante ISO → valor para `<input type="datetime-local">` en hora del consultorio. */
export function instanteAInputLocal(instante: string): string {
  const p = partesEnZona(Date.parse(instante));
  return `${p.year}-${pad(p.month)}-${pad(p.day)}T${pad(p.hour)}:${pad(p.minute)}`;
}

/** Instante ISO → `dd/MM/yyyy HH:mm` en hora del consultorio. */
export function formatearInstante(instante: string): string {
  const p = partesEnZona(Date.parse(instante));
  return `${pad(p.day)}/${pad(p.month)}/${p.year} ${pad(p.hour)}:${pad(p.minute)}`;
}
