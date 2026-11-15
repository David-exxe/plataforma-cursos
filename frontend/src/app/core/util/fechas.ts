/**
 * Utilidades de fecha. El backend trabaja en America/Lima:
 *  - LocalDate  ('2026-10-28')           → fechas de inicio de curso
 *  - Instant    ('2026-10-27T15:04:05Z') → creadoEn, fechaPago, etc.
 *
 * IMPORTANTE: new Date('2026-10-28') se interpreta como UTC y en Lima (UTC-5) muestra el
 * día anterior. Por eso las LocalDate se convierten con fechaLocal().
 */
const ZONA = 'America/Lima';
const LOCALE = 'es-PE';

function partesHoy(): { y: number; m: number; d: number } {
  const p = new Intl.DateTimeFormat('en-CA', { timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit' })
    .formatToParts(new Date());
  const get = (t: string) => Number(p.find((x) => x.type === t)?.value);
  return { y: get('year'), m: get('month'), d: get('day') };
}

/** Fecha de hoy en Lima con el formato de LocalDate: 'YYYY-MM-DD'. */
export function hoyLima(): string {
  const { y, m, d } = partesHoy();
  return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

/** 'YYYY-MM-DD' → Date a medianoche local, sin desfase. */
export function fechaLocal(iso: string): Date {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** Días calendario entre hoy (Lima) y una LocalDate. Negativo si ya pasó. */
export function diasHasta(iso: string): number {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  const hoy = partesHoy();
  return Math.round((Date.UTC(y, m - 1, d) - Date.UTC(hoy.y, hoy.m - 1, hoy.d)) / 86_400_000);
}

export function etiquetaInicio(iso: string): string {
  const n = diasHasta(iso);
  if (n === 0) return 'Empieza hoy';
  if (n === 1) return 'Empieza mañana';
  if (n > 1) return `Empieza en ${n} días`;
  if (n === -1) return 'Empezó ayer';
  return `Empezó hace ${-n} días`;
}

export function fechaLarga(iso: string): string {
  return new Intl.DateTimeFormat(LOCALE, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
    .format(fechaLocal(iso));
}

export function fechaCorta(iso: string): string {
  return new Intl.DateTimeFormat(LOCALE, { day: '2-digit', month: 'short', year: 'numeric' }).format(fechaLocal(iso));
}

/** Partes para el "talón" de la tarjeta de curso. */
export function partesTalon(iso: string): { semana: string; dia: string; mes: string } {
  const f = fechaLocal(iso);
  const limpiar = (s: string) => s.replace('.', '');
  return {
    semana: limpiar(new Intl.DateTimeFormat(LOCALE, { weekday: 'short' }).format(f)),
    dia: String(f.getDate()),
    mes: limpiar(new Intl.DateTimeFormat(LOCALE, { month: 'short' }).format(f)),
  };
}

/** Instant ISO → '27 oct 2026, 10:04 p. m.' en hora de Lima. */
export function fechaHora(instante: string | null | undefined): string {
  if (!instante) return '—';
  const f = new Date(instante);
  if (Number.isNaN(f.getTime())) return instante;
  // Espacios no separables, salvo tras la coma: en tablas la fecha se parte solo en "día," / "hora".
  return new Intl.DateTimeFormat(LOCALE, {
    timeZone: ZONA, day: '2-digit', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit',
  }).format(f).replace(/ /g, '\u00a0').replace(',\u00a0', ', ');
}

/** Tiempo restante legible: '2 d 4 h', '35 min'. Devuelve null si ya venció. */
export function tiempoRestante(hastaMs: number, ahora = Date.now()): string | null {
  const ms = hastaMs - ahora;
  if (ms <= 0) return null;
  const min = Math.floor(ms / 60_000);
  const d = Math.floor(min / 1440);
  const h = Math.floor((min % 1440) / 60);
  if (d > 0) return `${d} d ${h} h`;
  if (h > 0) return `${h} h ${min % 60} min`;
  return `${Math.max(1, min)} min`;
}
