import { ErrorApi } from '../models';

export type TipoError = 'red' | 'ip-bloqueada' | 'sesion' | 'permiso' | 'validacion' | 'servidor' | 'otro';

/** Error normalizado: todos los servicios lanzan esto, nunca el HttpErrorResponse crudo. */
export class ApiError extends Error {
  constructor(
    mensaje: string,
    public readonly estado: number,
    public readonly tipo: TipoError = 'otro',
    public readonly detalle: Record<string, string> | null = null,
    public readonly hasta: string | null = null,
    /** Código estable del backend (NO_ENCONTRADO, VALIDACION, SESION_INVALIDA, …). */
    public readonly codigo: string | null = null,
    /** Solo en errores 500: código para buscar el error en los logs del servidor. */
    public readonly referencia: string | null = null,
  ) {
    super(mensaje);
    this.name = 'ApiError';
  }
}

export function mensajeDe(e: unknown, porDefecto = 'Ocurrió un error inesperado. Inténtalo de nuevo.'): string {
  return e instanceof ApiError ? e.message : porDefecto;
}

/** El backend responde sin tildes; aquí se muestran mensajes claros al usuario. */
const TRADUCCIONES: Record<string, string> = {
  'Credenciales invalidas': 'Correo o contraseña incorrectos.',
  'Cuenta bloqueada temporalmente por intentos fallidos':
    'Tu cuenta está bloqueada temporalmente por demasiados intentos fallidos. Inténtalo de nuevo en 15 minutos.',
  'El correo ya esta registrado': 'Este correo ya está registrado. Prueba iniciando sesión.',
  'Codigo 2FA incorrecto': 'El código es incorrecto. Revisa tu app autenticadora e inténtalo de nuevo.',
  'Codigo incorrecto': 'El código es incorrecto. Revisa tu app autenticadora e inténtalo de nuevo.',
  'Token temporal invalido o expirado. Inicia sesion de nuevo': 'El tiempo para ingresar el código venció. Inicia sesión de nuevo.',
  'El usuario no tiene 2FA activo': 'Este usuario no tiene la verificación en dos pasos activa.',
  'El 2FA ya esta activo. Desactivalo primero': 'La verificación en dos pasos ya está activa.',
  'El 2FA no esta activo': 'La verificación en dos pasos no está activa.',
  'Primero llama a /api/auth/2fa/configurar': 'Primero genera el código QR para configurar la verificación.',
  'No hay cupos disponibles': 'Ya no quedan cupos en este curso.',
  'Ya tienes una inscripcion activa en este curso': 'Ya tienes una inscripción activa en este curso.',
  'El curso ya inicio': 'Este curso ya inició y no admite nuevas inscripciones.',
  'El curso no esta disponible': 'Este curso no está disponible.',
  'Codigo de curso repetido': 'Ya existe un curso con ese código.',
  'Curso no encontrado': 'No encontramos este curso.',
  'Inscripcion no encontrada': 'No encontramos esta inscripción.',
  'Pago no encontrado': 'Todavía no hay un pago registrado para esta inscripción.',
  'Comprobante no encontrado': 'No encontramos este comprobante.',
  'PDF no encontrado': 'El PDF de este comprobante no está disponible.',
  'La inscripcion no esta pendiente de pago': 'Esta inscripción ya no está pendiente de pago.',
  'Solo se puede cancelar una inscripcion pendiente de pago': 'Solo puedes cancelar inscripciones pendientes de pago.',
  'El servicio de pagos no esta disponible, intenta en unos minutos':
    'El servicio de pagos no está disponible. Inténtalo de nuevo en unos minutos.',
  'La simulacion solo esta disponible con MP_MODO=mock': 'La simulación de pagos solo funciona en modo de pruebas.',
  'Acceso denegado': 'No tienes permiso para realizar esta acción.',
  'Datos invalidos': 'Revisa los datos ingresados.',
  'El registro ya existe o viola una restriccion': 'Ese registro ya existe o no cumple las reglas del sistema.',
  'Error interno': 'Ocurrió un error en el servidor. Inténtalo de nuevo en unos minutos.',
  // Mensajes genéricos del manejo global de errores (ManejadorErrores / ManejadorErroresGateway)
  'La solicitud no es valida': 'La solicitud no es válida. Revisa los datos e inténtalo de nuevo.',
  'Error en la solicitud': 'No se pudo procesar la solicitud.',
  'JSON invalido o incompleto': 'Faltan datos o tienen un formato incorrecto.',
  'Debes iniciar sesion para continuar': 'Debes iniciar sesión para continuar.',
  'Tu sesion expiro o no es valida. Inicia sesion de nuevo': 'Tu sesión expiró. Ingresa de nuevo para continuar.',
  'El recurso solicitado no existe': 'No encontramos lo que buscas.',
  'La ruta solicitada no existe': 'No encontramos lo que buscas.',
  'Metodo HTTP no permitido para esta ruta': 'Esta acción no está disponible.',
  'Tipo de contenido no soportado. Envia JSON': 'No se pudo procesar la solicitud.',
  'El contenido enviado es demasiado grande': 'El archivo o los datos enviados son demasiado grandes.',
  'El registro fue modificado por otra operacion. Vuelve a intentarlo':
    'Alguien modificó este registro al mismo tiempo. Recarga la página y vuelve a intentarlo.',
  'Servicio no disponible temporalmente': 'El servicio no está disponible en este momento. Inténtalo de nuevo en unos minutos.',
  'El servicio tardo demasiado en responder. Intenta de nuevo': 'El servidor tardó demasiado en responder. Inténtalo de nuevo.',
  'Demasiadas peticiones. Intenta en unos minutos': 'Hiciste demasiadas solicitudes seguidas. Espera unos minutos.',
  'Clave interna invalida': 'No tienes permiso para realizar esta acción.',
};

/** Mensajes del backend que incluyen datos variables (nombre del parámetro, servicio, etc.). */
const PATRONES: [RegExp, (m: RegExpMatchArray) => string][] = [
  [/^El valor '(.*)' no es valido para el parametro '(.+)'$/, (m) => `El valor "${m[1]}" no es válido para ${m[2]}.`],
  [/^Falta el parametro obligatorio '(.+)'$/, (m) => `Falta un dato obligatorio: ${m[1]}.`],
  [/^El metodo \w+ no esta permitido en esta ruta$/, () => 'Esta acción no está disponible.'],
  [
    /^El servicio de (.+) no esta disponible en este momento\. El resto de la plataforma sigue funcionando\.$/,
    (m) => `El servicio de ${m[1]} no está disponible en este momento. El resto de la plataforma sigue funcionando; inténtalo de nuevo en unos minutos.`,
  ],
];

/** Textos por defecto de Spring en inglés: nunca se muestran al usuario. */
const TEXTOS_TECNICOS = /^(Bad Request|Unauthorized|Forbidden|Not Found|Method Not Allowed|Conflict|Internal Server Error|Service Unavailable|Gateway Timeout|Http failure)/i;

export function traducir(mensaje: string | undefined | null, porDefecto: string): string {
  if (!mensaje || TEXTOS_TECNICOS.test(mensaje)) return porDefecto;
  const directo = TRADUCCIONES[mensaje];
  if (directo) return directo;
  for (const [re, f] of PATRONES) {
    const m = mensaje.match(re);
    if (m) return f(m);
  }
  return mensaje;
}

const MSG_SIN_CONEXION =
  'No pudimos conectar con el servidor. Si es el primer ingreso del día, puede tardar hasta un minuto en iniciar: espera unos segundos e inténtalo de nuevo.';

/** Convierte (status, cuerpo JSON del backend) en un ApiError con mensaje para personas. */
export function construirError(estado: number, c: ErrorApi | null): ApiError {
  const msg = c?.error;
  const codigo = c?.codigo ?? null;
  const detalle = c?.detalle ?? null;
  const error = (texto: string, tipo: TipoError = 'otro', extra: { hasta?: string | null; referencia?: string | null } = {}) =>
    new ApiError(texto, estado, tipo, detalle, extra.hasta ?? null, codigo, extra.referencia ?? null);

  switch (estado) {
    case 0:
      return error(MSG_SIN_CONEXION, 'red');
    case 400:
    case 422:
      return error(traducir(msg, 'La solicitud no es válida. Revisa los datos ingresados.'), 'validacion');
    case 401:
      return error(traducir(msg, 'Tu sesión no es válida. Ingresa de nuevo.'), 'sesion');
    case 403:
      if (msg === 'IP bloqueada' || codigo === 'IP_BLOQUEADA') {
        return error('Tu conexión está bloqueada temporalmente por seguridad.', 'ip-bloqueada', { hasta: c?.hasta ?? null });
      }
      return error(traducir(msg, 'No tienes permiso para realizar esta acción.'), 'permiso');
    case 404:
      return error(traducir(msg, 'No encontramos lo que buscas.'));
    case 405:
      return error('Esta acción no está disponible.');
    case 409:
      return error(traducir(msg, 'No se pudo completar la operación por un conflicto con los datos actuales.'));
    case 413:
      return error('El archivo o los datos enviados son demasiado grandes.', 'validacion');
    case 429:
      return error('Hiciste demasiadas solicitudes seguidas. Tu conexión quedó bloqueada unos minutos.', 'ip-bloqueada', {
        hasta: c?.hasta ?? null,
      });
    case 502:
      return error(MSG_SIN_CONEXION, 'servidor');
    case 503:
      // El gateway indica qué servicio falló (circuit breaker); el resto de la plataforma sigue funcionando.
      return error(traducir(msg, 'El servicio no está disponible en este momento. Inténtalo de nuevo en unos minutos.'), 'servidor');
    case 504:
      return error('El servidor tardó demasiado en responder. Inténtalo de nuevo en unos segundos.', 'servidor');
    default: {
      if (estado >= 500) {
        const ref = c?.referencia ?? null;
        const base = 'Ocurrió un error inesperado en el servidor. Inténtalo de nuevo en unos minutos.';
        return error(ref ? `${base} Si el problema continúa, comunícate con soporte e indica el código ${ref}.` : base, 'servidor', {
          referencia: ref,
        });
      }
      return error(traducir(msg, 'Ocurrió un error inesperado.'));
    }
  }
}
