import { formatearInstante } from '../config/zoned-time';
import type { ListaEsperaApi, ResumenAviso } from './lista-espera-api';

const MENSAJE_GENERICO = 'No pudimos completar la operación. Intentá de nuevo en un momento.';
const MENSAJE_NO_DISPONIBLE = 'Este horario ya no está disponible: otro paciente lo tomó o el consultorio lo ocupó.';
const MENSAJE_VENCIDO = 'Este aviso venció. Seguís en la lista de espera y te avisaremos si se libera otro horario.';
const MENSAJE_RESERVADO = 'Listo: el turno quedó reservado. Te enviamos un email de confirmación.';

/** Mismo texto para cualquier `410` al abrir: no revela si el enlace venció, ya se usó o nunca existió. */
const MENSAJE_ENLACE = 'El enlace no es válido, venció o ya fue utilizado.';

function fila(clave: string, etiqueta: string, valor: string): [HTMLElement, HTMLElement] {
  const dt = document.createElement('dt');
  dt.textContent = etiqueta;
  const dd = document.createElement('dd');
  dd.dataset['campo'] = clave;
  dd.textContent = valor;
  return [dt, dd];
}

/**
 * Pantalla pública `/avisos/:token` (C-10): muestra el hueco liberado y reserva el turno solo al pulsar el botón. El
 * token vive solo en la closure: nunca se escribe en el DOM, en logs ni en mensajes de error.
 */
export function createAvisoPage(api: ListaEsperaApi): (container: HTMLElement, token: string) => void {
  return (container, token) => {
    let aceptando = false;

    const titulo = document.createElement('h1');
    titulo.textContent = 'Se liberó un turno';
    const estado = document.createElement('p');
    estado.setAttribute('role', 'status');
    const alerta = document.createElement('p');
    alerta.setAttribute('role', 'alert');
    const cuerpo = document.createElement('div');
    container.replaceChildren(titulo, estado, alerta, cuerpo);
    container.dataset['estadoCarga'] = 'cargando';

    function mostrarResultado(clave: 'noDisponible' | 'vencido', mensaje: string): void {
      const p = document.createElement('p');
      p.dataset['resultado'] = clave;
      p.textContent = mensaje;
      cuerpo.append(p);
    }

    function mostrarHueco(resumen: ResumenAviso): HTMLElement {
      const lista = document.createElement('dl');
      lista.append(
        ...fila('consultorio', 'Consultorio', resumen.consultorio),
        ...fila('prestacion', 'Prestación', resumen.prestacion),
        ...fila('fecha', 'Fecha y hora', formatearInstante(resumen.inicio)),
        ...fila('vence', 'Podés aceptarlo hasta', formatearInstante(resumen.venceEn)),
      );
      cuerpo.replaceChildren(lista);
      return lista;
    }

    function mostrarEnlaceNoDisponible(): void {
      titulo.textContent = 'Enlace no disponible';
      estado.textContent = '';
      alerta.textContent = '';
      const mensaje = document.createElement('p');
      mensaje.textContent = MENSAJE_ENLACE;
      cuerpo.replaceChildren(mensaje);
    }

    function aceptar(boton: HTMLButtonElement): void {
      if (aceptando) return;
      aceptando = true;
      boton.disabled = true;
      alerta.textContent = '';
      api
        .aceptarAviso(token)
        .then((resultado) => {
          boton.remove();
          switch (resultado.tipo) {
            case 'reservado':
              estado.textContent = MENSAJE_RESERVADO;
              return;
            case 'noDisponible':
              mostrarResultado('noDisponible', MENSAJE_NO_DISPONIBLE);
              return;
            case 'vencido':
              mostrarResultado('vencido', MENSAJE_VENCIDO);
              return;
          }
        })
        .catch(() => {
          alerta.textContent = MENSAJE_GENERICO;
          boton.disabled = false;
        })
        .finally(() => {
          aceptando = false;
        });
    }

    api
      .obtenerAviso(token)
      .then((resultado) => {
        if (resultado.tipo === 'noDisponible') {
          mostrarEnlaceNoDisponible();
          return;
        }
        mostrarHueco(resultado.resumen);
        if (!resultado.resumen.disponible) {
          mostrarResultado('noDisponible', MENSAJE_NO_DISPONIBLE);
          return;
        }
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.dataset['accion'] = 'aceptar';
        boton.textContent = 'Quiero este turno';
        boton.addEventListener('click', () => aceptar(boton));
        cuerpo.append(boton);
      })
      .catch(() => {
        alerta.textContent = MENSAJE_GENERICO;
        cuerpo.replaceChildren();
      })
      .finally(() => {
        container.dataset['estadoCarga'] = 'listo';
      });
  };
}
