import type { EntradaListaEspera, EstadoEntrada, ListaEsperaApi } from './lista-espera-api';

const ETIQUETA_ESTADO: Readonly<Record<EstadoEntrada, string>> = {
  PENDIENTE_CONFIRMACION: 'Pendiente de confirmación',
  ACTIVA: 'Activa',
  CUBIERTA: 'Cubierta',
  CANCELADA: 'Cancelada',
  VENCIDA: 'Vencida',
};

const MENSAJE_CARGA = 'No pudimos cargar la lista de espera. Intentá de nuevo en un momento.';
const MENSAJE_RETIRO = 'No se pudo retirar la entrada. Actualizá la página e intentá de nuevo.';

/** `YYYY-MM-DD` → `DD/MM/YYYY` sin pasar por `Date` (son fechas locales, no instantes). */
function formatearFecha(fecha: string): string {
  const [anio, mes, dia] = fecha.split('-');
  return anio === undefined || mes === undefined || dia === undefined ? fecha : `${dia}/${mes}/${anio}`;
}

function retirable(estado: EstadoEntrada): boolean {
  return estado === 'PENDIENTE_CONFIRMACION' || estado === 'ACTIVA';
}

function celda(columna: string, texto: string, valor?: string): HTMLTableCellElement {
  const td = document.createElement('td');
  td.dataset['col'] = columna;
  td.textContent = texto;
  if (valor !== undefined) td.dataset['valor'] = valor;
  return td;
}

/**
 * Vista privada `/lista-espera` (C-10 D7): entradas del consultorio de la más reciente a la más antigua y retiro de
 * las pendientes o activas. Nunca muestra DNI, email ni teléfono (la API no los envía).
 */
export function createListaEsperaPage(
  api: ListaEsperaApi,
  confirmar: (mensaje: string) => boolean = (m) => window.confirm(m),
): (container: HTMLElement) => void {
  return (container) => {
    const titulo = document.createElement('h1');
    titulo.textContent = 'Lista de espera';
    const alerta = document.createElement('p');
    alerta.setAttribute('role', 'alert');
    const vacio = document.createElement('p');
    vacio.dataset['vacio'] = '';
    vacio.textContent = 'No hay personas en la lista de espera.';
    vacio.hidden = true;

    const tabla = document.createElement('table');
    const encabezado = document.createElement('thead');
    const filaEncabezado = document.createElement('tr');
    for (const texto of ['Paciente', 'Prestación', 'Rango de fechas', 'Estado', '']) {
      const th = document.createElement('th');
      th.textContent = texto;
      filaEncabezado.append(th);
    }
    encabezado.append(filaEncabezado);
    const cuerpo = document.createElement('tbody');
    tabla.append(encabezado, cuerpo);
    tabla.hidden = true;

    container.replaceChildren(titulo, alerta, vacio, tabla);
    container.dataset['estadoCarga'] = 'cargando';

    function crearFila(entrada: EntradaListaEspera): HTMLTableRowElement {
      const tr = document.createElement('tr');
      tr.dataset['id'] = String(entrada.id);
      const acciones = document.createElement('td');
      acciones.dataset['col'] = 'acciones';
      tr.append(
        celda('paciente', entrada.paciente),
        celda('prestacion', entrada.prestacion),
        celda('rango', `${formatearFecha(entrada.fechaDesde)} al ${formatearFecha(entrada.fechaHasta)}`),
        celda('estado', ETIQUETA_ESTADO[entrada.estado], entrada.estado),
        acciones,
      );
      if (retirable(entrada.estado)) {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.dataset['accion'] = 'retirar';
        boton.textContent = 'Retirar';
        boton.addEventListener('click', () => retirar(entrada, tr, boton));
        acciones.append(boton);
      }
      return tr;
    }

    function retirar(entrada: EntradaListaEspera, fila: HTMLTableRowElement, boton: HTMLButtonElement): void {
      if (!confirmar('¿Retirar a esta persona de la lista de espera?')) return;
      boton.disabled = true;
      alerta.textContent = '';
      api
        .retirar(entrada.id)
        .then(() => {
          fila.replaceWith(crearFila({ ...entrada, estado: 'CANCELADA' }));
        })
        .catch(() => {
          alerta.textContent = MENSAJE_RETIRO;
          boton.disabled = false;
        });
    }

    api
      .listar()
      .then((entradas) => {
        cuerpo.replaceChildren(...entradas.map(crearFila));
        tabla.hidden = entradas.length === 0;
        vacio.hidden = entradas.length > 0;
      })
      .catch(() => {
        alerta.textContent = MENSAJE_CARGA;
      })
      .finally(() => {
        container.dataset['estadoCarga'] = 'listo';
      });
  };
}
