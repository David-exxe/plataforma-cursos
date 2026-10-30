/* Modelos calcados de los DTOs del backend (Java records). */

export type Rol = 'ESTUDIANTE' | 'ADMIN';
export type Modalidad = 'ONLINE' | 'PRESENCIAL';
export type Moneda = 'PEN' | 'USD';
export type EstadoInscripcion = 'PENDIENTE_PAGO' | 'PAGADA' | 'VENCIDA' | 'CANCELADA';
export type EstadoPago = 'PENDIENTE' | 'EN_PROCESO' | 'APROBADO' | 'RECHAZADO' | 'REEMBOLSADO';
export type EstadoEnvio = 'PENDIENTE' | 'ENVIADO' | 'ERROR' | 'FALLIDO';
export type EstadoNotificacion = 'PENDIENTE' | 'ENVIADO' | 'ENTREGADO' | 'LEIDO' | 'FALLIDO';

/* ---------- matricula-service: auth ---------- */
export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  telefono: string;
  rol: Rol;
  dosFactores: boolean;
}

export interface RegistroRequest {
  nombre: string;
  email: string;
  telefono: string;
  documento: string | null;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/** requiere2fa = true → hay que pedir el código y llamar a /2fa/verificar con tokenTemporal. */
export interface LoginResponse {
  requiere2fa: boolean;
  accessToken: string | null;
  tokenTemporal: string | null;
  tipo: string | null;
  expiraEnSegundos: number;
  usuario: Usuario | null;
}

export interface Configurar2faResponse {
  secreto: string;
  otpauthUri: string;
}

/* ---------- matricula-service: cursos ---------- */
/** Catálogo público (CursoDto). Para cursos ONLINE, `ubicacion` viene oculta hasta pagar. */
export interface Curso {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  modalidad: Modalidad;
  precio: number;
  moneda: Moneda;
  fechaInicio: string; // LocalDate: 'YYYY-MM-DD'
  horario: string | null;
  ubicacion: string | null;
  cupos: number;
  cuposDisponibles: number;
  activo: boolean;
}

/** /api/admin/cursos devuelve la entidad completa (sin cuposDisponibles). */
export interface CursoAdmin {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  modalidad: Modalidad;
  precio: number;
  moneda: Moneda;
  fechaInicio: string;
  horario: string | null;
  ubicacion: string | null;
  cupos: number;
  activo: boolean;
  creadoEn: string;
}

export interface CursoRequest {
  codigo: string;
  nombre: string;
  descripcion: string | null;
  modalidad: Modalidad;
  precio: number;
  moneda: Moneda;
  fechaInicio: string;
  horario: string | null;
  ubicacion: string | null;
  cupos: number;
  activo: boolean;
}

/* ---------- matricula-service: inscripciones ---------- */
export interface Inscripcion {
  id: number;
  cursoId: number;
  curso: string;
  modalidad: Modalidad;
  fechaInicio: string;
  horario: string | null;
  /** Enlace/aula: solo llega cuando la inscripción está PAGADA (o en vista admin). */
  ubicacion: string | null;
  estado: EstadoInscripcion;
  monto: number;
  moneda: Moneda;
  /** Solo llega mientras está PENDIENTE_PAGO. */
  linkPago: string | null;
  fechaPago: string | null;
  creadoEn: string;
  /** Solo en la vista admin. */
  estudiante: string | null;
  email: string | null;
}

/* ---------- pagos-service ---------- */
export interface Pago {
  pagoId: string;
  inscripcionId: number;
  curso: string;
  monto: number;
  moneda: Moneda;
  estado: EstadoPago;
  estadoDetalle: string | null;
  metodoPago: string | null;
  mpPaymentId: string | null;
  /** Solo llega si el pago está PENDIENTE o RECHAZADO (para reintentar). */
  linkPago: string | null;
  fechaAprobacion: string | null;
  creadoEn: string;
}

/* ---------- comprobantes-service ---------- */
export interface Comprobante {
  id: number;
  numero: string;
  inscripcionId: number;
  estudiante: string;
  email: string;
  curso: string;
  monto: number;
  moneda: Moneda;
  fechaEmision: string;
  estadoEnvio: EstadoEnvio;
  intentosEnvio: number;
  ultimoError: string | null;
  enviadoEn: string | null;
}

export interface ReenvioComprobanteResponse {
  reenviado: boolean;
  comprobante: Comprobante;
}

/* ---------- notificaciones-service ---------- */
export interface Notificacion {
  id: number;
  eventoId: string | null;
  tipo: string;
  telefono: string;
  mensaje: string;
  plantilla: string | null;
  parametros: string | null;
  estado: EstadoNotificacion;
  intentos: number;
  proximoIntento: string | null;
  waMessageId: string | null;
  error: string | null;
  creadoEn: string;
  enviadoEn: string | null;
}

/* ---------- api-gateway ---------- */
export interface IpBloqueada {
  ip: string;
  /** ISO-8601 o la palabra "permanente" (lista negra fija). */
  hasta: string;
  motivo: string;
}

export interface BloqueoIpRequest {
  ip: string;
  minutos: number;
  motivo: string | null;
}

/* ---------- formato de error del backend (ManejadorErrores) ---------- */
export interface ErrorApi {
  timestamp?: string;
  status?: number;
  error?: string;
  detalle?: Record<string, string>;
  /** Solo en el 403 "IP bloqueada" del gateway. */
  hasta?: string;
}
