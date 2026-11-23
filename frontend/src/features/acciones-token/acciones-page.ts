import { formatearInstante } from '../config/zoned-time';
import type { Accion, AccionesApi, EstadoTurno, ResumenAccion } from './acciones-api';

const ETIQUETA_BOTON: Readonly<Record<Accion, string>> = {
  CONFIRMAR_SOLICITUD: 'Confirmar solicitud',
  CONFIRMAR_TURNO: 'Confirmar asistencia',
  CANCELAR_TURNO: 'Cancelar turno',
};

const TITULO: Readonly<Record<Accion, string>> = {
  CONFIRMAR_SOLICITUD: 'Confirmá tu solicitud de turno',
  CONFIRMAR_TURNO: 'Confirmá tu asistencia',
  CANCELAR_TURNO: 'Cancelá tu turno',
};

const RESULTADO: Readonly<Record<Accion, string>> = {
  CONFIRMAR_SOLICITUD: 'Tu solicitud fue confirmada: el turno quedó reservado.',
  CONFIRMAR_TURNO: 'Asistencia confirmada. ¡Te esperamos!',
  CANCELAR_TURNO: 'El turno fue cancelado.',
};

const ETIQUETA_ESTADO: Readonly<Record<EstadoTurno, string>> = {
  PENDIENTE_CONFIRMACION: 'Pendiente de confirmación',
  RESERVADO: 'Reservado',
  CONFIRMADO: 'Confirmado',
  CANCELADO: 'Cancelado',
  AUSENTE: 'Ausente',
  ATENDIDO: 'Atendido',
  EXPIRADO: 'Expirado',
};

const MENSAJE_YA_NO_ADMITE = 'Este turno ya no admite esta acción.';
const MENSAJE_GENERICO = 'No pudimos completar la operación. Intentá de nuevo en un momento.';

/** Mismo texto para cualquier `410`: no revela si el enlace venció, ya se usó o nunca existió. */
const MENSAJE_ENLACE =
  'El enlace no es válido, venció o ya fue utilizado. Si era una solicitud de turno, el horario se liberó.';

function explicacionNoDisponible(resumen: ResumenAccion): string {
  if (resumen.motivo === 'FUERA_DE_LIMITE') {
    const limite = resumen.limiteCancelacion === null ? '' : ` (el límite era el ${formatearInstante(resumen.limiteCancelacion)})`;
    return `Ya no se puede cancelar este turno por este medio${limite}. Comunicate con el consultorio.`;
  }
  return MENSAJE_YA_NO_ADMITE;
}

function fila(clave: string, etiqueta: string, valor: string): [HTMLElement, HTMLElement] {
  const dt = document.createElement('dt');
  dt.textContent = etiqueta;
  const dd = document.createElement('dd');
  dd.dataset['campo'] = clave;
  dd.textContent = valor;
  return [dt, dd];
}

/**
 * Pantalla pública `/acciones/:token` (C-09): muestra el resumen y ejecuta la acción solo al pulsar el botón.
 * El token vive solo en la closure: nunca se escribe en el DOM, en logs ni en mensajes de error.
 */
export function createAccionesPage(api: AccionesApi): (container: HTMLElement, token: string) => void {
  return (container, token) => {
    let ejecutando = false;

    const titulo = document.createElement('h1');
    const estado = document.createElement('p');
    estado.setAttribute('role', 'status');
    const alerta = document.createElement('p');
    alerta.setAttribute('role', 'alert');
    const cuerpo = document.createElement('div');
    container.replaceChildren(titulo, estado, alerta, cuerpo);
    container.dataset['estadoCarga'] = 'cargando';
    titulo.textContent = 'Tu turno';

    function mostrarEnlaceNoDisponible(): void {
      titulo.textContent = 'Enlace no disponible';
      estado.textContent = '';
      alerta.textContent = '';
      const mensaje = document.createElement('p');
      mensaje.textContent = MENSAJE_ENLACE;
      const pedir = document.createElement('p');
      pedir.append('Podés pedir otro turno desde ');
      const enlace = document.createElement('a');
      enlace.href = '/publico';
      enlace.dataset['link'] = '';
      enlace.textContent = 'la página de solicitud de turnos';
      pedir.append(enlace, '.');
      cuerpo.replaceChildren(mensaje, pedir);
    }

    function mostrarResumen(resumen: ResumenAccion, resultado?: string): void {
      titulo.textContent = TITULO[resumen.accion];
      estado.textContent = resultado ?? '';
      const lista = document.createElement('dl');
      lista.append(
        ...fila('consultorio', 'Consultorio', resumen.turno.consultorio),
        ...fila('prestacion', 'Prestación', resumen.turno.prestacion),
        ...fila('fecha', 'Fecha y hora', formatearInstante(resumen.turno.inicio)),
        ...fila('estado', 'Estado', ETIQUETA_ESTADO[resumen.turno.estado]),
      );
      cuerpo.replaceChildren(lista);
      if (resumen.disponible) {
        cuerpo.append(crearBoton(resumen));
      } else if (resultado === undefined) {
        const explicacion = document.createElement('p');
        explicacion.dataset['noDisponible'] = '';
        explicacion.textContent = explicacionNoDisponible(resumen);
        cuerpo.append(explicacion);
      }
    }

    function crearBoton(resumen: ResumenAccion): HTMLButtonElement {
      const boton = document.createElement('button');
      boton.type = 'button';
      boton.dataset['accion'] = resumen.accion;
      boton.textContent = ETIQUETA_BOTON[resumen.accion];
      boton.addEventListener('click', () => ejecutar(resumen.accion, boton));
      return boton;
    }

    function cargarResumen(aviso?: string): Promise<void> {
      return api
        .obtenerResumen(token)
        .then((resultado) => {
          if (resultado.tipo === 'noDisponible') {
            mostrarEnlaceNoDisponible();
            return;
          }
          mostrarResumen(resultado.resumen);
          alerta.textContent = aviso ?? '';
        })
        .catch(() => {
          alerta.textContent = MENSAJE_GENERICO;
          cuerpo.replaceChildren();
        });
    }

    function ejecutar(accion: Accion, boton: HTMLButtonElement): void {
      if (ejecutando) return;
      ejecutando = true;
      boton.disabled = true;
      alerta.textContent = '';
      api
        .ejecutar(token, accion)
        .then((resultado) => {
          switch (resultado.tipo) {
            case 'ejecutada':
              mostrarResumen(resultado.resumen, RESULTADO[accion]);
              return undefined;
            case 'noDisponible':
              mostrarEnlaceNoDisponible();
              return undefined;
            case 'estadoCambiado':
              return cargarResumen(MENSAJE_YA_NO_ADMITE);
          }
        })
        .catch(() => {
          alerta.textContent = MENSAJE_GENERICO;
          boton.disabled = false;
        })
        .finally(() => {
          ejecutando = false;
        });
    }

    void cargarResumen().finally(() => {
      container.dataset['estadoCarga'] = 'listo';
    });
  };
}
