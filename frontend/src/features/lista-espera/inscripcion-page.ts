import { partesEnZona } from '../config/zoned-time';
import type { PrestacionPublica } from '../publico/publico-api';
import { validarDatosPaciente } from '../publico/validators';
import type { InscripcionListaEspera, ListaEsperaApi, ResultadoInscripcion } from './lista-espera-api';

/** Mismo texto ante cualquier `202`: no revela si el paciente ya existía ni si ya estaba anotado (anti-enumeración). */
export const MENSAJE_INSCRIPCION =
  'Si los datos son correctos, vas a recibir un email para confirmar tu inscripción en la lista de espera.';
const MENSAJE_GENERICO = 'No pudimos enviar tu pedido. Intentá de nuevo en un momento.';
const MENSAJE_CARGA = 'No pudimos cargar las prestaciones. Intentá de nuevo en un momento.';
const MENSAJE_SIN_PRESTACION = 'Elegí una prestación.';
const MENSAJE_PRESTACION_NO_DISPONIBLE = 'La prestación elegida ya no está disponible. Elegí otra.';
/** Campo del selector en los errores del servidor (`InscripcionListaEsperaRequest.prestacionId`). */
const CAMPO_PRESTACION = 'prestacionId';
const MENSAJE_REVISAR = 'Revisá los datos marcados.';
const OBLIGATORIO = 'Es obligatorio.';

const CAMPOS = [
  ['nombre', 'Nombre', 'given-name', 'text'],
  ['apellido', 'Apellido', 'family-name', 'text'],
  ['dni', 'DNI', 'off', 'text'],
  ['email', 'Email', 'email', 'email'],
  ['telefono', 'Teléfono', 'tel', 'tel'],
  ['fechaDesde', 'Desde', 'off', 'date'],
  ['fechaHasta', 'Hasta', 'off', 'date'],
] as const;

type NombreCampo = (typeof CAMPOS)[number][0];

function mensajeLimite(segundos: number | undefined): string {
  if (segundos === undefined) return 'Superaste el límite de solicitudes. Probá de nuevo en unos minutos.';
  const minutos = Math.max(1, Math.ceil(segundos / 60));
  return `Superaste el límite de solicitudes. Probá de nuevo en ${minutos} ${minutos === 1 ? 'minuto' : 'minutos'}.`;
}

/** `YYYY-MM-DD` de hoy en el calendario del consultorio (como `app.timezone` en el backend), no del navegador. */
export function hoyEnConsultorio(ahora: Date): string {
  const { year, month, day } = partesEnZona(ahora.getTime());
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

/** Errores de las fechas (formato `YYYY-MM-DD` que entrega `<input type="date">`); mismos textos que el backend. */
function validarFechas(desde: string, hasta: string, hoy: string): Readonly<Record<string, string>> {
  const errores: Record<string, string> = {};
  if (desde === '') errores['fechaDesde'] = OBLIGATORIO;
  if (hasta === '') errores['fechaHasta'] = OBLIGATORIO;
  if (desde !== '' && hasta !== '' && hasta < desde) errores['fechaHasta'] = 'No puede ser anterior a la fecha desde.';
  else if (hasta !== '' && hasta < hoy) errores['fechaHasta'] = 'No puede ser anterior a hoy.';
  return errores;
}

/**
 * Pantalla pública `/publico/lista-espera` (C-10): anotarse por prestación y rango de fechas sin cuenta. Nunca
 * distingue si el DNI ya existía ni si ya había una inscripción (anti-enumeración).
 */
export function createInscripcionPage(
  api: ListaEsperaApi,
  listarPrestaciones: () => Promise<readonly PrestacionPublica[]>,
  ahora: () => Date = () => new Date(),
): (container: HTMLElement) => void {
  return (container) => {
    const hoy = hoyEnConsultorio(ahora());
    let enviando = false;
    let limitado = false;
    let temporizador: ReturnType<typeof setTimeout> | undefined;

    const titulo = document.createElement('h1');
    titulo.textContent = 'Anotate en la lista de espera';
    const estado = document.createElement('p');
    estado.setAttribute('role', 'status');
    const alerta = document.createElement('p');
    alerta.setAttribute('role', 'alert');

    const selectPrestacion = document.createElement('select');
    selectPrestacion.name = 'prestacion';
    const vacia = document.createElement('option');
    vacia.value = '';
    vacia.textContent = 'Elegí una prestación';
    selectPrestacion.append(vacia);
    const etiquetaPrestacion = document.createElement('label');
    etiquetaPrestacion.append('Prestación ', selectPrestacion);
    const errorPrestacion = document.createElement('span');
    errorPrestacion.dataset['error'] = CAMPO_PRESTACION;
    const filaPrestacion = document.createElement('div');
    filaPrestacion.append(etiquetaPrestacion, errorPrestacion);

    const form = document.createElement('form');
    form.noValidate = true;
    const inputs = new Map<NombreCampo, HTMLInputElement>();
    const errores = new Map<NombreCampo, HTMLElement>();
    const filas: HTMLElement[] = [];
    for (const [nombre, etiqueta, autocompletar, tipo] of CAMPOS) {
      const input = document.createElement('input');
      input.name = nombre;
      input.type = tipo;
      input.autocomplete = autocompletar;
      if (tipo === 'date') input.min = hoy;
      const error = document.createElement('span');
      error.dataset['error'] = nombre;
      const label = document.createElement('label');
      label.append(`${etiqueta} `, input);
      const fila = document.createElement('div');
      fila.append(label, error);
      inputs.set(nombre, input);
      errores.set(nombre, error);
      filas.push(fila);
    }

    // Señuelo (honeypot): fuera de pantalla, sin tabulación, oculto a tecnologías de asistencia y sin autocompletado.
    const senuelo = document.createElement('input');
    senuelo.type = 'text';
    senuelo.name = 'sitioWeb';
    senuelo.tabIndex = -1;
    senuelo.setAttribute('aria-hidden', 'true');
    senuelo.autocomplete = 'off';
    const envoltorioSenuelo = document.createElement('div');
    envoltorioSenuelo.setAttribute('aria-hidden', 'true');
    envoltorioSenuelo.style.position = 'absolute';
    envoltorioSenuelo.style.left = '-10000px';
    envoltorioSenuelo.style.width = '1px';
    envoltorioSenuelo.style.height = '1px';
    envoltorioSenuelo.style.overflow = 'hidden';
    envoltorioSenuelo.append(senuelo);

    const enviar = document.createElement('button');
    enviar.type = 'submit';
    enviar.textContent = 'Anotarme';

    const volver = document.createElement('a');
    volver.href = '/publico';
    volver.dataset['link'] = '';
    volver.textContent = 'Volver a la solicitud de turnos';

    form.append(filaPrestacion, ...filas, envoltorioSenuelo, enviar);
    container.replaceChildren(titulo, estado, alerta, form, volver);
    container.dataset['estadoCarga'] = 'cargando';

    function limpiarErrores(): void {
      for (const [nombre, error] of errores) {
        error.textContent = '';
        inputs.get(nombre)?.removeAttribute('aria-invalid');
      }
      errorPrestacion.textContent = '';
      selectPrestacion.removeAttribute('aria-invalid');
    }

    function marcarError(nombre: string, mensaje: string): void {
      if (nombre === CAMPO_PRESTACION) {
        errorPrestacion.textContent = mensaje;
        selectPrestacion.setAttribute('aria-invalid', 'true');
        return;
      }
      const campo = CAMPOS.find(([n]) => n === nombre);
      if (campo === undefined) return;
      const [clave] = campo;
      const error = errores.get(clave);
      if (error !== undefined) error.textContent = mensaje;
      inputs.get(clave)?.setAttribute('aria-invalid', 'true');
    }

    function actualizarVista(): void {
      enviar.disabled = enviando || limitado;
    }

    function aplicarResultado(resultado: ResultadoInscripcion): void {
      switch (resultado.tipo) {
        case 'aceptada':
          for (const input of inputs.values()) input.value = '';
          senuelo.value = '';
          selectPrestacion.value = '';
          estado.textContent = MENSAJE_INSCRIPCION;
          break;
        case 'limite':
          limitado = true;
          alerta.textContent = mensajeLimite(resultado.retryAfterSeconds);
          if (resultado.retryAfterSeconds !== undefined) {
            temporizador = setTimeout(() => {
              limitado = false;
              actualizarVista();
            }, resultado.retryAfterSeconds * 1000);
          }
          break;
        case 'invalida':
          for (const { field, message } of resultado.fieldErrors) marcarError(field, message);
          alerta.textContent = MENSAJE_REVISAR;
          break;
        case 'prestacionNoDisponible':
          marcarError(CAMPO_PRESTACION, MENSAJE_PRESTACION_NO_DISPONIBLE);
          alerta.textContent = MENSAJE_REVISAR;
          cargarPrestaciones();
          break;
      }
    }

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      if (enviando || limitado) return;
      estado.textContent = '';
      alerta.textContent = '';
      limpiarErrores();
      const valor = (nombre: NombreCampo): string => inputs.get(nombre)?.value.trim() ?? '';
      const datos = {
        nombre: valor('nombre'),
        apellido: valor('apellido'),
        dni: valor('dni'),
        email: valor('email'),
        telefono: valor('telefono'),
      };
      const invalidos = { ...validarDatosPaciente(datos), ...validarFechas(valor('fechaDesde'), valor('fechaHasta'), hoyEnConsultorio(ahora())) };
      for (const [campo, mensaje] of Object.entries(invalidos)) marcarError(campo, mensaje);
      if (Object.keys(invalidos).length > 0) {
        alerta.textContent = MENSAJE_REVISAR;
        return;
      }
      if (selectPrestacion.value === '') {
        marcarError(CAMPO_PRESTACION, MENSAJE_SIN_PRESTACION);
        alerta.textContent = MENSAJE_SIN_PRESTACION;
        return;
      }
      const inscripcion: InscripcionListaEspera = {
        prestacionId: Number(selectPrestacion.value),
        fechaDesde: valor('fechaDesde'),
        fechaHasta: valor('fechaHasta'),
        ...datos,
        sitioWeb: senuelo.value,
      };

      enviando = true;
      actualizarVista();
      api
        .inscribir(inscripcion)
        .then(aplicarResultado)
        .catch(() => {
          alerta.textContent = MENSAJE_GENERICO;
        })
        .finally(() => {
          enviando = false;
          actualizarVista();
        });
    });

    window.addEventListener(
      'popstate',
      () => {
        if (temporizador !== undefined) clearTimeout(temporizador);
      },
      { once: true },
    );

    /** Carga (o recarga, tras un `404`) las prestaciones: deja solo la opción vacía y queda sin elección. */
    function cargarPrestaciones(): void {
      container.dataset['estadoCarga'] = 'cargando';
      listarPrestaciones()
        .then((prestaciones) => {
          selectPrestacion.replaceChildren(vacia);
          selectPrestacion.value = '';
          selectPrestacion.append(
            ...prestaciones.map((p) => {
              const option = document.createElement('option');
              option.value = String(p.id);
              option.textContent = `${p.nombre} (${p.duracionMin} min)`;
              return option;
            }),
          );
        })
        .catch(() => {
          alerta.textContent = MENSAJE_CARGA;
        })
        .finally(() => {
          container.dataset['estadoCarga'] = 'listo';
        });
    }

    cargarPrestaciones();
    actualizarVista();
  };
}
