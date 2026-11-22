/**
 * SOLO DESARROLLO (design.md D10 de C-08): implementación en memoria del contrato provisional v0
 * de la agenda y de la API de pacientes de C-04. Se carga con import dinámico detrás de
 * `import.meta.env.DEV`, así que no entra en el build de producción. Datos 100 % ficticios.
 */
import type { Bloqueo, Horario, Prestacion } from '../features/config/agenda-config-api';
import type { Consultorio } from '../features/config/config-api';
import { localAInstante } from '../features/config/zoned-time';
import type {
  AgendaApi,
  Ausencia,
  CrearTurnoInput,
  EstadoTurno,
  Hueco,
  PrestacionResumen,
  Turno,
} from '../features/agenda/agenda-api';
import { fechaLocal, inicioSemana, sumarDias } from '../features/agenda/calendario';
import type { PacienteAltaRapida, PacienteResponse, PacientesApi } from '../features/agenda/pacientes-api';
import type { ApiError } from '../shared/http/client';

export interface AgendaMocksDeps {
  readonly listarPrestaciones: () => Promise<Prestacion[]>;
  readonly listarHorarios: () => Promise<Horario[]>;
  readonly listarBloqueos: () => Promise<Bloqueo[]>;
  readonly obtenerConsultorio: () => Promise<Consultorio>;
  readonly ahora?: () => Date;
}

export interface AgendaMocks {
  readonly api: AgendaApi;
  readonly pacientes: PacientesApi;
}

/** Estado interno mutable (el contador de ausencias cambia); hacia afuera se entregan copias. */
type PacienteMock = { -readonly [K in keyof PacienteResponse]: PacienteResponse[K] };

const MIN = 60_000;
const VENTANA_REVERSION_MS = 24 * 60 * MIN;
const ACTIVOS: ReadonlySet<EstadoTurno> = new Set(['PENDIENTE_CONFIRMACION', 'RESERVADO', 'CONFIRMADO']);
const CONFIRMABLES: ReadonlySet<EstadoTurno> = new Set(['RESERVADO', 'CONFIRMADO']);

function error(status: number, title: string, detail?: string, extensions: Record<string, unknown> = {}): ApiError {
  const base = { kind: 'http', status, title, fieldErrors: [], extensions } as const;
  return detail === undefined ? base : { ...base, detail };
}

const falla = (e: ApiError): Promise<never> => Promise.reject(e);
const copia = <T>(valor: T): Promise<T> => Promise.resolve(structuredClone(valor));
const normalizarDni = (dni: string): string => dni.replace(/[.\s]/g, '');
const seSuperponen = (a: Hueco, b: Hueco): boolean => Date.parse(a.inicio) < Date.parse(b.fin) && Date.parse(b.inicio) < Date.parse(a.fin);
const sumarMin = (instante: string, min: number): string => new Date(Date.parse(instante) + min * MIN).toISOString().replace(/\.\d{3}Z$/, 'Z');

function enLocal(fecha: string, hora: string): string {
  const instante = localAInstante(`${fecha}T${hora}`);
  if (instante === null) throw new RangeError(`Fecha u hora inválida: ${fecha} ${hora}`);
  return instante;
}

/** 1 = lunes … 7 = domingo, como `horario_trabajo.dia_semana`. */
function diaSemanaIso(fecha: string): number {
  const [a, m, d] = fecha.split('-').map(Number) as [number, number, number];
  const dia = new Date(Date.UTC(a, m - 1, d)).getUTCDay();
  return dia === 0 ? 7 : dia;
}

const horaAMin = (hora: string): number => {
  const [h, m] = hora.split(':').map(Number) as [number, number];
  return h * 60 + m;
};
const minAHora = (min: number): string => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;

export function crearAgendaMocks(deps: AgendaMocksDeps): AgendaMocks {
  const ahora = deps.ahora ?? ((): Date => new Date());
  let siguienteId = 500;
  const nuevoId = (): number => (siguienteId += 1);

  const pacientes: PacienteMock[] = (
    [
      ['Lucía', 'Gómez', '30111222', '+54 11 5555-0101'],
      ['Martín', 'Sosa', '28444555', '+54 11 5555-0102'],
      ['Carla', 'Méndez', '35666777', '+54 11 5555-0103'],
      ['Julián', 'Paz', '41222333', '+54 11 5555-0104'],
    ] as const
  ).map(([nombre, apellido, dni, telefono]) => ({
    id: nuevoId(),
    nombre,
    apellido,
    dni,
    fechaNacimiento: null,
    telefono,
    email: `${nombre}.${apellido}@ejemplo.test`.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, ''),
    direccion: null,
    cantidadAusencias: 0,
    activo: true,
  }));

  let turnos: Turno[] = [];
  const ausencias = new Map<number, Ausencia[]>();
  const estadoPrevio = new Map<number, EstadoTurno>();

  const resumenPaciente = (p: PacienteMock): Turno['paciente'] => ({
    id: p.id,
    nombre: p.nombre,
    apellido: p.apellido,
    dni: p.dni,
    cantidadAusencias: p.cantidadAusencias,
  });

  function registrarAusencia(paciente: PacienteMock, ausencia: Ausencia): void {
    ausencias.set(paciente.id, [...(ausencias.get(paciente.id) ?? []), ausencia]);
    paciente.cantidadAusencias += 1;
  }

  // ---- Seed relativo a la semana actual (se arma al primer uso, cuando hay prestaciones) ----
  const listo: Promise<Prestacion[]> = deps.listarPrestaciones().then((prestaciones) => {
    const p = (nombre: string): PrestacionResumen => {
      const encontrada = prestaciones.find((x) => x.nombre === nombre) ?? prestaciones[0];
      if (encontrada === undefined) throw new Error('Los mocks necesitan al menos una prestación.');
      return { id: encontrada.id, nombre: encontrada.nombre, duracionMin: encontrada.duracionMin };
    };
    const hoy = fechaLocal(ahora());
    const lunes = inicioSemana(hoy);
    const [a, b, c, d] = pacientes as [PacienteMock, PacienteMock, PacienteMock, PacienteMock];
    const semilla: [string, string, string, PacienteMock, EstadoTurno, Partial<Turno>][] = [
      [lunes, '09:00', 'Consulta', a, 'ATENDIDO', {}],
      [lunes, '10:00', 'Limpieza', b, 'AUSENTE', { ausenteMarcadoEn: enLocal(lunes, '10:20') }],
      [sumarDias(lunes, 1), '09:00', 'Control', c, 'CONFIRMADO', {}],
      [sumarDias(lunes, 1), '11:00', 'Obturación', a, 'RESERVADO', {}],
      [sumarDias(lunes, 2), '14:00', 'Consulta', d, 'PENDIENTE_CONFIRMACION', { origen: 'PUBLICA' }],
      [sumarDias(lunes, 2), '16:00', 'Extracción', c, 'CANCELADO', { motivoCancelacion: 'Viaje', canceladoPor: 'PACIENTE' }],
      [sumarDias(lunes, 3), '09:30', 'Limpieza', d, 'EXPIRADO', { origen: 'PUBLICA', canceladoPor: 'SISTEMA' }],
      [sumarDias(lunes, 4), '10:00', 'Endodoncia', b, 'RESERVADO', {}],
      [hoy, '15:00', 'Consulta', a, 'RESERVADO', {}],
      [hoy, '17:00', 'Limpieza', c, 'CONFIRMADO', {}],
    ];
    for (const [fecha, hora, prestacion, paciente, estado, extra] of semilla) {
      const pr = p(prestacion);
      const inicio = enLocal(fecha, hora);
      const turno: Turno = {
        id: nuevoId(),
        paciente: resumenPaciente(paciente),
        prestacion: pr,
        inicio,
        fin: sumarMin(inicio, pr.duracionMin),
        estado,
        origen: 'ODONTOLOGO',
        pendienteHasta: estado === 'PENDIENTE_CONFIRMACION' ? sumarMin(inicio, -60) : null,
        motivoCancelacion: null,
        canceladoPor: null,
        ausenteMarcadoEn: null,
        ...extra,
      };
      turnos.push(turno);
      if (estado === 'AUSENTE') {
        estadoPrevio.set(turno.id, 'RESERVADO');
        registrarAusencia(paciente, { turnoId: turno.id, inicio, prestacionNombre: pr.nombre });
      }
    }
    // Relativos a "ahora", para que el demo siempre tenga uno marcable como ausente y uno revertible.
    const cuarto = 15 * MIN;
    const base = Math.floor(ahora().getTime() / cuarto) * cuarto;
    const consulta = p('Consulta');
    const relativos: [number, PacienteMock, EstadoTurno, number | null][] = [
      [base - 30 * MIN, d, 'RESERVADO', null],
      [base - 3 * 60 * MIN, c, 'AUSENTE', base - 2 * 60 * MIN],
    ];
    for (const [ms, paciente, estado, marcado] of relativos) {
      const inicio = new Date(ms).toISOString().replace(/\.\d{3}Z$/, 'Z');
      const hueco = { inicio, fin: sumarMin(inicio, consulta.duracionMin) };
      if (turnos.some((t) => ACTIVOS.has(t.estado) && seSuperponen(t, hueco))) continue;
      const turno: Turno = {
        id: nuevoId(),
        paciente: resumenPaciente(paciente),
        prestacion: consulta,
        ...hueco,
        estado,
        origen: 'ODONTOLOGO',
        pendienteHasta: null,
        motivoCancelacion: null,
        canceladoPor: null,
        ausenteMarcadoEn: marcado === null ? null : new Date(marcado).toISOString(),
      };
      turnos.push(turno);
      if (estado === 'AUSENTE') {
        estadoPrevio.set(turno.id, 'CONFIRMADO');
        registrarAusencia(paciente, { turnoId: turno.id, inicio, prestacionNombre: consulta.nombre });
      }
    }
    // Una ausencia histórica para mostrar el indicador "N aus." con más de una.
    registrarAusencia(b, { turnoId: nuevoId(), inicio: enLocal(sumarDias(lunes, -40), '11:30'), prestacionNombre: 'Consulta' });
    return prestaciones;
  });

  /** El turno con la cantidad de ausencias vigente del paciente. */
  function vista(turno: Turno): Turno {
    const paciente = pacientes.find((p) => p.id === turno.paciente.id);
    return paciente === undefined ? turno : { ...turno, paciente: resumenPaciente(paciente) };
  }

  function guardar(turno: Turno): Promise<Turno> {
    turnos = [...turnos.filter((t) => t.id !== turno.id), turno];
    return copia(vista(turno));
  }

  async function buscarTurno(id: number): Promise<Turno> {
    await listo;
    const turno = turnos.find((t) => t.id === id);
    if (turno === undefined) throw error(404, 'Turno inexistente');
    return turno;
  }

  const ocupados = (excluir?: number): Turno[] => turnos.filter((t) => ACTIVOS.has(t.estado) && t.id !== excluir);

  async function huecosDelDia(prestacion: PrestacionResumen, fecha: string, excluir?: number): Promise<Hueco[]> {
    const [horarios, bloqueos, consultorio] = await Promise.all([
      deps.listarHorarios(),
      deps.listarBloqueos(),
      deps.obtenerConsultorio(),
    ]);
    const paso = consultorio.parametros.granularidadMin;
    const dia = diaSemanaIso(fecha);
    const tomados = ocupados(excluir);
    const huecos: Hueco[] = [];
    for (const h of horarios.filter((x) => x.diaSemana === dia).sort((x, y) => x.horaDesde.localeCompare(y.horaDesde))) {
      for (let m = horaAMin(h.horaDesde); m + prestacion.duracionMin <= horaAMin(h.horaHasta); m += paso) {
        const inicio = enLocal(fecha, minAHora(m));
        const hueco = { inicio, fin: sumarMin(inicio, prestacion.duracionMin) };
        if (Date.parse(inicio) <= ahora().getTime()) continue;
        if (tomados.some((t) => seSuperponen(t, hueco))) continue;
        if (bloqueos.some((b) => seSuperponen({ inicio: b.desde, fin: b.hasta }, hueco))) continue;
        huecos.push(hueco);
      }
    }
    return huecos;
  }

  async function prestacionResumen(id: number): Promise<PrestacionResumen> {
    const prestaciones = await listo;
    const p = prestaciones.find((x) => x.id === id);
    if (p === undefined) throw error(404, 'Prestación inexistente');
    if (!p.activa) throw error(422, 'Regla de negocio incumplida', 'La prestación está inactiva');
    return { id: p.id, nombre: p.nombre, duracionMin: p.duracionMin };
  }

  /** Valida horario, bloqueos y superposición como lo haría C-07; 409 con las 3 alternativas más cercanas. */
  async function validarIntervalo(prestacion: PrestacionResumen, inicio: string, excluir?: number): Promise<Hueco> {
    if (Number.isNaN(Date.parse(inicio))) throw error(422, 'Datos inválidos', 'Inicio inválido');
    const hueco = { inicio, fin: sumarMin(inicio, prestacion.duracionMin) };
    if (Date.parse(inicio) <= ahora().getTime()) throw error(422, 'Regla de negocio incumplida', 'No se puede reservar en el pasado');
    const fecha = fechaLocal(inicio);
    if (ocupados(excluir).some((t) => seSuperponen(t, hueco))) {
      const libres = await huecosDelDia(prestacion, fecha, excluir);
      const cercanas = [...libres]
        .sort((x, y) => Math.abs(Date.parse(x.inicio) - Date.parse(inicio)) - Math.abs(Date.parse(y.inicio) - Date.parse(inicio)))
        .slice(0, 3)
        .sort((x, y) => Date.parse(x.inicio) - Date.parse(y.inicio));
      throw error(409, 'Conflicto de agenda', undefined, { alternativas: cercanas });
    }
    const libres = await huecosDelDia(prestacion, fecha, excluir);
    if (!libres.some((h) => h.inicio === inicio)) {
      throw error(422, 'Regla de negocio incumplida', 'Fuera del horario de atención o en un bloqueo');
    }
    return hueco;
  }

  const transicionInvalida = (): ApiError => error(422, 'Regla de negocio incumplida', 'Transición inválida');

  const api: AgendaApi = {
    async listarAgenda(desde, hasta) {
      await listo;
      const rango = { inicio: desde, fin: hasta };
      return copia(turnos.filter((t) => seSuperponen(t, rango)).sort((x, y) => Date.parse(x.inicio) - Date.parse(y.inicio)).map(vista));
    },
    async disponibilidad(prestacionId, fecha, excluirTurnoId) {
      const prestacion = await prestacionResumen(prestacionId);
      return copia(await huecosDelDia(prestacion, fecha, excluirTurnoId));
    },
    async turnosHoy() {
      await listo;
      const hoy = fechaLocal(ahora());
      return copia(turnos.filter((t) => fechaLocal(t.inicio) === hoy).sort((x, y) => Date.parse(x.inicio) - Date.parse(y.inicio)).map(vista));
    },
    async crearTurno({ pacienteId, prestacionId, inicio }: CrearTurnoInput) {
      await listo;
      const paciente = pacientes.find((p) => p.id === pacienteId);
      if (paciente === undefined) return falla(error(404, 'Paciente inexistente'));
      const prestacion = await prestacionResumen(prestacionId);
      const hueco = await validarIntervalo(prestacion, inicio);
      return guardar({
        id: nuevoId(),
        paciente: resumenPaciente(paciente),
        prestacion,
        ...hueco,
        estado: 'RESERVADO',
        origen: 'ODONTOLOGO',
        pendienteHasta: null,
        motivoCancelacion: null,
        canceladoPor: null,
        ausenteMarcadoEn: null,
      });
    },
    async moverTurno(id, inicio) {
      const turno = await buscarTurno(id);
      if (!CONFIRMABLES.has(turno.estado)) return falla(transicionInvalida());
      const hueco = await validarIntervalo(turno.prestacion, inicio, id);
      return guardar({ ...turno, ...hueco });
    },
    async cancelarTurno(id, motivo) {
      const turno = await buscarTurno(id);
      if (!ACTIVOS.has(turno.estado)) return falla(transicionInvalida());
      if (motivo !== null && motivo.length > 200) return falla(error(422, 'Datos inválidos', 'Máximo 200 caracteres.'));
      return guardar({ ...turno, estado: 'CANCELADO', motivoCancelacion: motivo, canceladoPor: 'ODONTOLOGO' });
    },
    async marcarAtendido(id) {
      const turno = await buscarTurno(id);
      if (!CONFIRMABLES.has(turno.estado)) return falla(transicionInvalida());
      return guardar({ ...turno, estado: 'ATENDIDO' });
    },
    async marcarAusente(id) {
      const turno = await buscarTurno(id);
      if (!CONFIRMABLES.has(turno.estado)) return falla(transicionInvalida());
      const { parametros } = await deps.obtenerConsultorio();
      if (ahora().getTime() < Date.parse(turno.inicio) + parametros.toleranciaAusenteMin * MIN) {
        return falla(error(422, 'Regla de negocio incumplida', 'Todavía no pasó la tolerancia'));
      }
      const paciente = pacientes.find((p) => p.id === turno.paciente.id);
      if (paciente !== undefined) registrarAusencia(paciente, { turnoId: id, inicio: turno.inicio, prestacionNombre: turno.prestacion.nombre });
      estadoPrevio.set(id, turno.estado);
      return guardar({ ...turno, estado: 'AUSENTE', ausenteMarcadoEn: ahora().toISOString() });
    },
    async revertirAusencia(id) {
      const turno = await buscarTurno(id);
      if (turno.estado !== 'AUSENTE' || turno.ausenteMarcadoEn === null) return falla(transicionInvalida());
      if (ahora().getTime() >= Date.parse(turno.ausenteMarcadoEn) + VENTANA_REVERSION_MS) {
        return falla(error(422, 'Regla de negocio incumplida', 'Pasaron más de 24 horas desde la marca'));
      }
      const paciente = pacientes.find((p) => p.id === turno.paciente.id);
      if (paciente !== undefined) {
        ausencias.set(paciente.id, (ausencias.get(paciente.id) ?? []).filter((a) => a.turnoId !== id));
        paciente.cantidadAusencias = Math.max(0, paciente.cantidadAusencias - 1);
      }
      return guardar({ ...turno, estado: estadoPrevio.get(id) ?? 'RESERVADO', ausenteMarcadoEn: null });
    },
    async ausenciasPaciente(pacienteId) {
      await listo;
      const paciente = pacientes.find((p) => p.id === pacienteId);
      if (paciente === undefined) return falla(error(404, 'Paciente inexistente'));
      const lista = [...(ausencias.get(pacienteId) ?? [])].sort((x, y) => Date.parse(y.inicio) - Date.parse(x.inicio));
      return copia({ cantidad: paciente.cantidadAusencias, ausencias: lista });
    },
  };

  const pacientesApi: PacientesApi = {
    async buscarPorDni(dni) {
      await listo;
      const buscado = normalizarDni(dni);
      return copia(pacientes.filter((p) => p.dni === buscado));
    },
    async crearPaciente(input: PacienteAltaRapida) {
      await listo;
      const dni = normalizarDni(input.dni);
      if (pacientes.some((p) => p.dni === dni)) return falla(error(409, 'DNI duplicado', 'Ya existe un paciente con ese DNI'));
      const nuevo: PacienteMock = {
        id: nuevoId(),
        nombre: input.nombre.trim(),
        apellido: input.apellido.trim(),
        dni,
        fechaNacimiento: null,
        telefono: input.telefono.trim(),
        email: input.email.trim().toLowerCase(),
        direccion: null,
        cantidadAusencias: 0,
        activo: true,
      };
      pacientes.push(nuevo);
      return copia(nuevo);
    },
  };

  return { api, pacientes: pacientesApi };
}
