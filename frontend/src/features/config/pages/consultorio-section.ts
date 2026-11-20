import type { ConfigApi, Consultorio, ConsultorioEditable, ParametrosConsultorio } from '../config-api';
import { toFormErrors } from '../form-errors';
import { validarParametro } from '../validators';
import type { SectionRenderer } from './config-page';
import {
  conBotonOcupado,
  crearAlerta,
  crearBoton,
  crearCampo,
  crearEstado,
  limpiarErrores,
  marcarError,
  mostrarErrorApi,
  type Campo,
} from './form-kit';

export type ConsultorioSectionDeps = Pick<ConfigApi, 'obtenerConsultorio' | 'actualizarConsultorio'>;

type CampoTexto = 'nombre' | 'direccion' | 'telefono' | 'emailContacto';
type CampoParametro = keyof ParametrosConsultorio;

const TEXTOS: readonly [CampoTexto, string, string][] = [
  ['nombre', 'Nombre del consultorio', 'text'],
  ['direccion', 'Dirección', 'text'],
  ['telefono', 'Teléfono', 'tel'],
  ['emailContacto', 'Email de contacto', 'email'],
];

const PARAMETROS: readonly [CampoParametro, string][] = [
  ['granularidadMin', 'Granularidad de la agenda (min)'],
  ['anticipacionMinReservaMin', 'Anticipación mínima para reservar (min)'],
  ['limiteCancelacionHoras', 'Límite para cancelar (horas antes)'],
  ['recordatorioHorasAntes', 'Recordatorio (horas antes del turno)'],
  ['vencimientoAvisoMin', 'Vencimiento de avisos de hueco (min)'],
  ['vencimientoPendienteMin', 'Vencimiento de solicitudes pendientes (min)'],
];

export function createConsultorioSection(deps: ConsultorioSectionDeps): SectionRenderer {
  return (container) => {
    const form = document.createElement('form');
    form.noValidate = true;

    const campos: Record<string, Campo> = {};
    const datos = document.createElement('fieldset');
    const leyendaDatos = document.createElement('legend');
    leyendaDatos.textContent = 'Datos del consultorio';
    datos.append(leyendaDatos);
    for (const [name, etiqueta, type] of TEXTOS) {
      const campo = crearCampo(name, etiqueta, { type });
      campos[name] = campo;
      datos.append(campo.wrapper);
    }
    const zona = document.createElement('p');
    datos.append(zona);

    const parametros = document.createElement('fieldset');
    const leyendaParametros = document.createElement('legend');
    leyendaParametros.textContent = 'Parámetros';
    const aviso = document.createElement('p');
    aviso.textContent = 'Los cambios de parámetros aplican a operaciones nuevas y no a turnos ya creados.';
    parametros.append(leyendaParametros, aviso);
    for (const [name, etiqueta] of PARAMETROS) {
      const campo = crearCampo(name, etiqueta, { type: 'number', min: '1', step: '1' });
      campos[name] = campo;
      parametros.append(campo.wrapper);
    }

    const alerta = crearAlerta();
    const estado = crearEstado();
    const guardar = crearBoton('Guardar', 'submit');
    guardar.disabled = true;
    form.append(datos, parametros, alerta, estado, guardar);
    container.replaceChildren(form);

    const valor = (name: string): string => campos[name]?.input.value ?? '';

    function cargar(consultorio: Consultorio): void {
      for (const [name] of TEXTOS) {
        const campo = campos[name];
        if (campo !== undefined) campo.input.value = consultorio[name];
      }
      for (const [name] of PARAMETROS) {
        const campo = campos[name];
        if (campo !== undefined) campo.input.value = String(consultorio[name]);
      }
      zona.textContent = `Zona horaria: ${consultorio.zonaHoraria}`;
      guardar.disabled = false;
    }

    function leerFormulario(): ConsultorioEditable | null {
      let valido = true;
      const nombre = valor('nombre').trim();
      if (nombre === '') {
        const campo = campos['nombre'];
        if (campo !== undefined) marcarError(campo, 'El nombre es obligatorio.');
        valido = false;
      }
      const numeros: Partial<Record<CampoParametro, number>> = {};
      for (const [name] of PARAMETROS) {
        const resultado = validarParametro(valor(name));
        const campo = campos[name];
        if (resultado.ok) numeros[name] = resultado.value;
        else {
          if (campo !== undefined) marcarError(campo, resultado.error);
          valido = false;
        }
      }
      if (!valido) return null;
      return {
        nombre,
        direccion: valor('direccion').trim(),
        telefono: valor('telefono').trim(),
        emailContacto: valor('emailContacto').trim(),
        granularidadMin: numeros.granularidadMin ?? 0,
        anticipacionMinReservaMin: numeros.anticipacionMinReservaMin ?? 0,
        limiteCancelacionHoras: numeros.limiteCancelacionHoras ?? 0,
        recordatorioHorasAntes: numeros.recordatorioHorasAntes ?? 0,
        vencimientoAvisoMin: numeros.vencimientoAvisoMin ?? 0,
        vencimientoPendienteMin: numeros.vencimientoPendienteMin ?? 0,
      };
    }

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      limpiarErrores(Object.values(campos), alerta);
      estado.textContent = '';
      const datosForm = leerFormulario();
      if (datosForm === null) return;
      void conBotonOcupado(guardar, () => deps.actualizarConsultorio(datosForm))
        .then((actualizado) => {
          cargar(actualizado);
          estado.textContent = 'Cambios guardados.';
        })
        .catch((error: unknown) => mostrarErrorApi(error, campos, alerta));
    });

    deps
      .obtenerConsultorio()
      .then(cargar)
      .catch((error: unknown) => {
        alerta.textContent = toFormErrors(error).general ?? 'No se pudieron cargar los datos.';
      });
  };
}
