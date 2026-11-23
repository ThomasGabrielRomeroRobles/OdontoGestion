import { formatearInstante } from '../config/zoned-time';
import type { HuecoLibre, PublicoApi, ResultadoSolicitud, SolicitudTurno } from './publico-api';
import { validarDatosPaciente } from './validators';

/** Mismo texto ante cualquier `202`: no revela si el paciente ya existía (RN-anti-enumeración). */
export const MENSAJE_ACEPTADA = 'Si los datos son correctos, vas a recibir un email para confirmar el turno.';
const MENSAJE_CONFLICTO = 'Ese horario ya no está disponible. Elegí otro.';
const MENSAJE_HORARIO_INVALIDO = 'El horario elegido no es válido. Elegí otro de la lista.';
const MENSAJE_GENERICO = 'No pudimos enviar tu solicitud. Intentá de nuevo en un momento.';
const MENSAJE_CARGA = 'No pudimos cargar la información. Intentá de nuevo en un momento.';
const MENSAJE_SIN_HORARIO = 'Elegí un horario.';

const CAMPOS = [
  ['nombre', 'Nombre', 'given-name', 'text'],
  ['apellido', 'Apellido', 'family-name', 'text'],
  ['dni', 'DNI', 'off', 'text'],
  ['email', 'Email', 'email', 'email'],
  ['telefono', 'Teléfono', 'tel', 'tel'],
] as const;

type NombreCampo = (typeof CAMPOS)[number][0];

function mensajeLimite(segundos: number | undefined): string {
  if (segundos === undefined) return 'Superaste el límite de solicitudes. Probá de nuevo en unos minutos.';
  const minutos = Math.max(1, Math.ceil(segundos / 60));
  return `Superaste el límite de solicitudes. Probá de nuevo en ${minutos} ${minutos === 1 ? 'minuto' : 'minutos'}.`;
}

function crearBotonHueco(hueco: HuecoLibre, atributo: 'hueco' | 'alternativa'): HTMLButtonElement {
  const boton = document.createElement('button');
  boton.type = 'button';
  boton.dataset[atributo] = hueco.inicio;
  boton.textContent = formatearInstante(hueco.inicio);
  return boton;
}

/**
 * Pantalla pública `/publico` (C-09): elegir prestación y horario libre y pedir el turno sin sesión. Solo usa
 * `/api/v1/publico/**` (a través de `api`). Nunca muestra datos de pacientes ni distingue si el DNI ya existía.
 */
export function createPublicoPage(api: PublicoApi): (container: HTMLElement) => void {
  return (container) => {
    let prestacionId: number | null = null;
    let fecha = '';
    let huecoElegido: string | null = null;
    let enviando = false;
    let limitado = false;
    let consulta = 0;
    let temporizador: ReturnType<typeof setTimeout> | undefined;

    const titulo = document.createElement('h1');
    titulo.textContent = 'Solicitá tu turno';

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

    const inputFecha = document.createElement('input');
    inputFecha.type = 'date';
    inputFecha.name = 'fecha';
    const etiquetaFecha = document.createElement('label');
    etiquetaFecha.append('Día ', inputFecha);

    const listaHuecos = document.createElement('div');
    listaHuecos.dataset['huecos'] = '';
    const sinHuecos = document.createElement('p');
    sinHuecos.dataset['sinHuecos'] = '';
    sinHuecos.textContent = 'Sin horarios disponibles ese día. Probá con otro día.';
    sinHuecos.hidden = true;
    const alternativasEl = document.createElement('div');
    alternativasEl.dataset['alternativas'] = '';
    alternativasEl.hidden = true;

    const form = document.createElement('form');
    form.noValidate = true;
    form.hidden = true;
    const elegido = document.createElement('p');
    elegido.dataset['huecoElegido'] = '';

    const inputs = new Map<NombreCampo, HTMLInputElement>();
    const errores = new Map<NombreCampo, HTMLElement>();
    const filas: HTMLElement[] = [];
    for (const [nombre, etiqueta, autocompletar, tipo] of CAMPOS) {
      const input = document.createElement('input');
      input.name = nombre;
      input.type = tipo;
      input.autocomplete = autocompletar;
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
    enviar.textContent = 'Solicitar turno';

    form.append(elegido, ...filas, envoltorioSenuelo, enviar);
    container.replaceChildren(
      titulo,
      estado,
      alerta,
      etiquetaPrestacion,
      etiquetaFecha,
      listaHuecos,
      sinHuecos,
      alternativasEl,
      form,
    );
    container.dataset['estadoCarga'] = 'cargando';

    function limpiarErrores(): void {
      for (const [nombre, error] of errores) {
        error.textContent = '';
        inputs.get(nombre)?.removeAttribute('aria-invalid');
      }
    }

    function marcarError(nombre: string, mensaje: string): void {
      const campo = CAMPOS.find(([n]) => n === nombre);
      if (campo === undefined) return;
      const [clave] = campo;
      const error = errores.get(clave);
      if (error !== undefined) error.textContent = mensaje;
      inputs.get(clave)?.setAttribute('aria-invalid', 'true');
    }

    function actualizarVista(): void {
      elegido.textContent =
        huecoElegido === null ? MENSAJE_SIN_HORARIO : `Horario elegido: ${formatearInstante(huecoElegido)}`;
      form.hidden = huecoElegido === null && alternativasEl.hidden;
      enviar.disabled = enviando || limitado;
    }

    function elegirHorario(inicio: string): void {
      huecoElegido = inicio;
      alternativasEl.replaceChildren();
      alternativasEl.hidden = true;
      alerta.textContent = '';
      actualizarVista();
    }

    function mostrarAlternativas(alternativas: readonly HuecoLibre[]): void {
      alternativasEl.replaceChildren(
        ...alternativas.map((hueco) => {
          const boton = crearBotonHueco(hueco, 'alternativa');
          boton.addEventListener('click', () => elegirHorario(hueco.inicio));
          return boton;
        }),
      );
      alternativasEl.hidden = alternativas.length === 0;
    }

    function cargarHuecos(): void {
      if (prestacionId === null || fecha === '') return;
      consulta += 1;
      const esta = consulta;
      container.dataset['estadoHuecos'] = 'cargando';
      sinHuecos.hidden = true;
      api
        .consultarDisponibilidad(prestacionId, fecha)
        .then((huecos) => {
          if (esta !== consulta) return;
          listaHuecos.replaceChildren(
            ...huecos.map((hueco) => {
              const boton = crearBotonHueco(hueco, 'hueco');
              boton.addEventListener('click', () => elegirHorario(hueco.inicio));
              return boton;
            }),
          );
          sinHuecos.hidden = huecos.length > 0;
          if (huecos.length === 0) huecoElegido = null;
          actualizarVista();
        })
        .catch(() => {
          if (esta === consulta) alerta.textContent = MENSAJE_CARGA;
        })
        .finally(() => {
          if (esta === consulta) container.dataset['estadoHuecos'] = 'listo';
        });
    }

    selectPrestacion.addEventListener('change', () => {
      prestacionId = selectPrestacion.value === '' ? null : Number(selectPrestacion.value);
      huecoElegido = null;
      mostrarAlternativas([]);
      actualizarVista();
      cargarHuecos();
    });
    inputFecha.addEventListener('change', () => {
      fecha = inputFecha.value;
      huecoElegido = null;
      mostrarAlternativas([]);
      actualizarVista();
      cargarHuecos();
    });

    function limpiarFormulario(): void {
      for (const input of inputs.values()) input.value = '';
      senuelo.value = '';
      huecoElegido = null;
      listaHuecos.replaceChildren();
      mostrarAlternativas([]);
    }

    function aplicarResultado(resultado: ResultadoSolicitud): void {
      switch (resultado.tipo) {
        case 'aceptada':
          limpiarFormulario();
          estado.textContent = MENSAJE_ACEPTADA;
          break;
        case 'conflicto':
          huecoElegido = null;
          alerta.textContent = MENSAJE_CONFLICTO;
          mostrarAlternativas(resultado.alternativas);
          if (resultado.alternativas.length === 0) cargarHuecos();
          break;
        case 'horarioInvalido':
          huecoElegido = null;
          alerta.textContent = MENSAJE_HORARIO_INVALIDO;
          cargarHuecos();
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
          alerta.textContent = 'Revisá los datos marcados.';
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
      const invalidos = validarDatosPaciente(datos);
      for (const [campo, mensaje] of Object.entries(invalidos)) marcarError(campo, mensaje);
      if (Object.keys(invalidos).length > 0) return;
      if (prestacionId === null || huecoElegido === null) {
        alerta.textContent = MENSAJE_SIN_HORARIO;
        return;
      }
      const solicitud: SolicitudTurno = { prestacionId, inicio: huecoElegido, ...datos, sitioWeb: senuelo.value };

      enviando = true;
      actualizarVista();
      api
        .solicitarTurno(solicitud)
        .then(aplicarResultado)
        .catch(() => {
          alerta.textContent = MENSAJE_GENERICO;
        })
        .finally(() => {
          enviando = false;
          actualizarVista();
        });
    });

    function liberarTemporizador(): void {
      if (temporizador !== undefined) clearTimeout(temporizador);
    }
    window.addEventListener('popstate', liberarTemporizador, { once: true });

    api
      .listarPrestaciones()
      .then((prestaciones) => {
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
    actualizarVista();
  };
}
