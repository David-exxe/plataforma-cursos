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
};

export function traducir(mensaje: string | undefined | null, porDefecto: string): string {
  if (!mensaje) return porDefecto;
  return TRADUCCIONES[mensaje] ?? mensaje;
}

/** Convierte (status, cuerpo JSON del backend) en un ApiError con mensaje para personas. */
export function construirError(estado: number, c: ErrorApi | null): ApiError {
  const msg = c?.error;
  switch (estado) {
    case 0:
      return new ApiError('No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.', 0, 'red');
    case 400:
      return new ApiError(traducir(msg, 'La solicitud no es válida.'), 400, 'validacion', c?.detalle ?? null);
    case 401:
      return new ApiError(traducir(msg, 'Tu sesión no es válida. Ingresa de nuevo.'), 401, 'sesion');
    case 403:
      if (msg === 'IP bloqueada') {
        return new ApiError('Tu conexión está bloqueada temporalmente por seguridad.', 403, 'ip-bloqueada', null, c?.hasta ?? null);
      }
      return new ApiError(traducir(msg, 'No tienes permiso para realizar esta acción.'), 403, 'permiso');
    case 404:
      return new ApiError(traducir(msg, 'No encontramos lo que buscas.'), 404);
    case 409:
      return new ApiError(traducir(msg, 'No se pudo completar la operación por un conflicto.'), 409);
    case 429:
      return new ApiError('Hiciste demasiadas solicitudes seguidas. Tu conexión quedó bloqueada unos minutos.', 429, 'ip-bloqueada');
    case 502:
    case 503:
    case 504:
      return new ApiError('El servicio no está disponible en este momento. Inténtalo de nuevo en unos minutos.', estado, 'servidor');
    default:
      return new ApiError(
        traducir(msg, estado >= 500 ? 'Ocurrió un error en el servidor. Inténtalo de nuevo.' : 'Ocurrió un error inesperado.'),
        estado,
        estado >= 500 ? 'servidor' : 'otro',
        c?.detalle ?? null,
      );
  }
}
