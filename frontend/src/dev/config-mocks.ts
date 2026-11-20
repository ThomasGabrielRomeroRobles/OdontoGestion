/**
 * SOLO DESARROLLO (design.md D9): APIs en memoria para usar la configuración en el navegador
 * antes de que existan C-02 y el backend de C-06. Se carga con import dinámico detrás de
 * `import.meta.env.DEV`, así que no entra en el build de producción. Datos 100 % ficticios.
 */
import type { SessionUser } from '../features/auth/auth-api';
import type { AgendaConfigApi, Bloqueo, Horario, Prestacion } from '../features/config/agenda-config-api';
import type { ConfigApi, Consultorio, Perfil } from '../features/config/config-api';
import { buscarSolapamiento, validarDuracion, validarRangoHorario } from '../features/config/validators';
import type { ApiError } from '../shared/http/client';

/** Contraseña del odontólogo ficticio en el modo mock (no es una credencial real). */
const PASSWORD_DEMO = 'demo';

function error422(field: string, message: string): ApiError {
  return { kind: 'http', status: 422, title: 'Datos inválidos', fieldErrors: [{ field, message }] };
}

function error404(): ApiError {
  return { kind: 'http', status: 404, title: 'Recurso inexistente', fieldErrors: [] };
}

export interface MocksDesarrollo {
  readonly auth: {
    readonly me: () => Promise<SessionUser | null>;
    readonly login: (email: string, password: string) => Promise<SessionUser>;
    readonly logout: () => Promise<void>;
  };
  readonly config: ConfigApi;
  readonly agenda: AgendaConfigApi;
}

export function crearMocksDesarrollo(): MocksDesarrollo {
  let sesion: SessionUser | null = null;
  let password = PASSWORD_DEMO;
  let siguienteId = 100;
  const nuevoId = (): number => (siguienteId += 1);

  let consultorio: Consultorio = {
    nombre: 'Consultorio Demo',
    direccion: 'Calle Falsa 123',
    telefono: '011 5555-0000',
    emailContacto: 'contacto@consultorio.example',
    zonaHoraria: 'America/Argentina/Buenos_Aires',
    granularidadMin: 15,
    anticipacionMinReservaMin: 120,
    limiteCancelacionHoras: 24,
    recordatorioHorasAntes: 24,
    vencimientoAvisoMin: 60,
    vencimientoPendienteMin: 30,
  };
  let perfil: Perfil = { email: 'odontologo@consultorio.example', nombre: 'Ana', apellido: 'Pérez', matricula: 'MN 0000' };

  let prestaciones: Prestacion[] = (
    [
      ['Consulta', 30],
      ['Limpieza', 45],
      ['Obturación', 60],
      ['Endodoncia', 90],
      ['Extracción', 45],
      ['Control', 15],
    ] as const
  ).map(([nombre, duracionMin]) => ({ id: nuevoId(), nombre, duracionMin, activa: true }));

  let horarios: Horario[] = ([1, 2, 3, 4, 5] as const).flatMap((diaSemana) => [
    { id: nuevoId(), diaSemana, horaDesde: '09:00', horaHasta: '13:00' },
    { id: nuevoId(), diaSemana, horaDesde: '14:00', horaHasta: '18:00' },
  ]);
  let bloqueos: Bloqueo[] = [];

  const ok = <T>(valor: T): Promise<T> => Promise.resolve(structuredClone(valor));
  const falla = (error: ApiError): Promise<never> => Promise.reject(error);

  function guardarPrestacion(id: number | null, nombre: string, duracionMin: number): Promise<Prestacion> {
    const duracion = validarDuracion(String(duracionMin), consultorio.granularidadMin);
    if (!duracion.ok) return falla(error422('duracionMin', duracion.error));
    const repetida = prestaciones.some((p) => p.id !== id && p.nombre.toLowerCase() === nombre.toLowerCase());
    if (repetida) return falla(error422('nombre', 'Ya existe una prestación con ese nombre.'));
    const previa = prestaciones.find((p) => p.id === id);
    if (id !== null && previa === undefined) return falla(error404());
    const guardada: Prestacion = { id: id ?? nuevoId(), nombre, duracionMin, activa: previa?.activa ?? true };
    prestaciones = [...prestaciones.filter((p) => p.id !== guardada.id), guardada];
    return ok(guardada);
  }

  function cambiarEstado(id: number, activa: boolean): Promise<Prestacion> {
    const previa = prestaciones.find((p) => p.id === id);
    if (previa === undefined) return falla(error404());
    return guardarPrestacionDirecta({ ...previa, activa });
  }

  function guardarPrestacionDirecta(p: Prestacion): Promise<Prestacion> {
    prestaciones = [...prestaciones.filter((x) => x.id !== p.id), p];
    return ok(p);
  }

  function guardarHorario(id: number | null, datos: Omit<Horario, 'id'>): Promise<Horario> {
    const rango = validarRangoHorario(datos.horaDesde, datos.horaHasta);
    if (!rango.ok) return falla(error422(rango.campo, rango.error));
    if (buscarSolapamiento(datos, horarios, id ?? undefined) !== null) {
      return falla(error422('horaDesde', 'Se superpone con otra franja del mismo día.'));
    }
    if (id !== null && !horarios.some((h) => h.id === id)) return falla(error404());
    const guardado: Horario = { id: id ?? nuevoId(), ...datos };
    horarios = [...horarios.filter((h) => h.id !== guardado.id), guardado];
    return ok(guardado);
  }

  function guardarBloqueo(id: number | null, datos: Omit<Bloqueo, 'id'>): Promise<Bloqueo> {
    if (Date.parse(datos.hasta) <= Date.parse(datos.desde)) return falla(error422('hasta', 'Debe ser posterior a desde.'));
    if (id !== null && !bloqueos.some((b) => b.id === id)) return falla(error404());
    const guardado: Bloqueo = { id: id ?? nuevoId(), ...datos };
    bloqueos = [...bloqueos.filter((b) => b.id !== guardado.id), guardado];
    return ok(guardado);
  }

  return {
    auth: {
      me: () => ok(sesion),
      login: (email, pass) => {
        if (pass !== password) return falla({ kind: 'http', status: 401, title: 'No autenticado', fieldErrors: [] });
        sesion = { email };
        return ok(sesion);
      },
      logout: () => {
        sesion = null;
        return Promise.resolve();
      },
    },
    config: {
      obtenerConsultorio: () => ok(consultorio),
      actualizarConsultorio: (input) => {
        consultorio = { ...consultorio, ...input };
        return ok(consultorio);
      },
      obtenerPerfil: () => ok(perfil),
      actualizarPerfil: (input) => {
        perfil = { ...perfil, ...input };
        return ok(perfil);
      },
      cambiarPassword: ({ passwordActual, passwordNueva }) => {
        if (passwordActual !== password) return falla(error422('passwordActual', 'La contraseña actual es incorrecta.'));
        password = passwordNueva;
        return Promise.resolve();
      },
    },
    agenda: {
      listarPrestaciones: () => ok(prestaciones),
      crearPrestacion: ({ nombre, duracionMin }) => guardarPrestacion(null, nombre, duracionMin),
      actualizarPrestacion: (id, { nombre, duracionMin }) => guardarPrestacion(id, nombre, duracionMin),
      desactivarPrestacion: (id) => cambiarEstado(id, false),
      activarPrestacion: (id) => cambiarEstado(id, true),
      listarHorarios: () => ok(horarios),
      crearHorario: (input) => guardarHorario(null, input),
      actualizarHorario: (id, input) => guardarHorario(id, input),
      eliminarHorario: (id) => {
        if (!horarios.some((h) => h.id === id)) return falla(error404());
        horarios = horarios.filter((h) => h.id !== id);
        return Promise.resolve();
      },
      listarBloqueos: () => ok(bloqueos.filter((b) => Date.parse(b.hasta) > Date.now())),
      crearBloqueo: (input) => guardarBloqueo(null, input),
      actualizarBloqueo: (id, input) => guardarBloqueo(id, input),
      eliminarBloqueo: (id) => {
        if (!bloqueos.some((b) => b.id === id)) return falla(error404());
        bloqueos = bloqueos.filter((b) => b.id !== id);
        return Promise.resolve();
      },
    },
  };
}
