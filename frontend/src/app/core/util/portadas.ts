/**
 * Portadas ilustradas para los cursos, generadas en el navegador.
 *
 * La base de datos no guarda imágenes, así que cada curso recibe una portada tipo "libro"
 * a partir de su nombre y código: se detecta el tema (web, backend, datos, diseño…),
 * se elige su paleta y se dibuja un patrón geométrico propio de ese curso (siempre el mismo
 * para el mismo código). Sale como data URL SVG: no hay archivos ni llamadas al backend.
 */

export interface DatosPortada {
  nombre: string;
  codigo?: string | null;
  modalidad?: string | null;
}

export interface Tema {
  id: string;
  etiqueta: string;
  glifo: string;
  fondo: string;
  acento: string;
  tinta: string;
}

const TEMAS: (Tema & { claves: string[] })[] = [
  { id: 'web', etiqueta: 'Desarrollo web', glifo: '</>', fondo: '#0a6e80', acento: '#f6b40e', tinta: '#ffffff',
    claves: ['angular', 'react', 'vue', 'javascript', 'typescript', 'html', 'css', 'frontend', 'front-end', 'web'] },
  { id: 'backend', etiqueta: 'Backend', glifo: '{ }', fondo: '#0e2a35', acento: '#5ad1c4', tinta: '#ffffff',
    claves: ['spring', 'java', 'backend', 'back-end', 'api', 'microservicio', 'node', 'php', 'laravel', '.net', 'c#'] },
  { id: 'datos', etiqueta: 'Datos', glifo: 'Σx', fondo: '#1f5fa6', acento: '#ffd166', tinta: '#ffffff',
    claves: ['python', 'datos', 'data', 'sql', 'excel', 'power bi', 'analit', 'estadist', 'machine', ' ia', 'inteligencia'] },
  { id: 'diseno', etiqueta: 'Diseño', glifo: 'Aa', fondo: '#9b2f6f', acento: '#ffc2dd', tinta: '#ffffff',
    claves: ['diseño', 'diseno', 'ux', 'ui', 'figma', 'photoshop', 'illustrator', 'grafico', 'gráfico'] },
  { id: 'infra', etiqueta: 'Infraestructura', glifo: '>_', fondo: '#2d2a4a', acento: '#9be7ff', tinta: '#ffffff',
    claves: ['docker', 'linux', 'cloud', 'devops', 'redes', 'seguridad', 'ciber', 'kubernetes', 'aws', 'servidor'] },
  { id: 'idiomas', etiqueta: 'Idiomas', glifo: 'Hi', fondo: '#17784a', acento: '#c8f0a0', tinta: '#ffffff',
    claves: ['ingles', 'inglés', 'english', 'idioma', 'frances', 'francés', 'portugues', 'portugués', 'aleman'] },
  { id: 'negocios', etiqueta: 'Negocios', glifo: '+%', fondo: '#8f4b00', acento: '#ffe0a8', tinta: '#ffffff',
    claves: ['marketing', 'ventas', 'negocio', 'emprend', 'finanzas', 'contab', 'gestion', 'gestión', 'liderazgo'] },
  { id: 'agro', etiqueta: 'Agro', glifo: '≈', fondo: '#3f6b2a', acento: '#f6e27a', tinta: '#ffffff',
    claves: ['agro', 'cafe', 'café', 'cacao', 'riego', 'cultivo', 'suelo', 'nutricion', 'nutrición', 'fertil'] },
];

const POR_DEFECTO: Tema = { id: 'general', etiqueta: 'Curso', glifo: '', fondo: '#35525e', acento: '#f6b40e', tinta: '#ffffff' };

function sinTildes(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/** Minúsculas, sin tildes y solo letras/números separados por un espacio. */
function limpiar(s: string): string {
  return sinTildes(s).replace(/[^a-z0-9#]+/g, ' ').trim();
}

/**
 * Tema del curso según palabras clave del nombre y el código.
 * Cada clave debe coincidir con el INICIO de una palabra ("ux" no coincide con "linux").
 */
export function temaDe(d: DatosPortada): Tema {
  const texto = ` ${limpiar(`${d.nombre} ${d.codigo ?? ''}`)} `;
  for (const t of TEMAS) {
    if (t.claves.some((k) => texto.includes(` ${limpiar(k)}`))) return t;
  }
  return { ...POR_DEFECTO, glifo: iniciales(d.nombre) };
}

function iniciales(nombre: string): string {
  const p = nombre.trim().split(/\s+/).filter((x) => x.length > 2);
  const l = (p.length ? p : nombre.trim().split(/\s+/)).slice(0, 2).map((x) => x[0]?.toUpperCase() ?? '');
  return l.join('') || '·';
}

/** Hash estable (FNV-1a) para que cada curso tenga siempre el mismo patrón. */
function hash(s: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

function esc(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** SVG de la portada (640 x 360). */
export function portadaSvg(d: DatosPortada): string {
  const t = temaDe(d);
  let h = hash(`${d.codigo ?? ''}|${d.nombre}`);
  const r = () => {
    h = Math.imul(h ^ (h >>> 15), 0x2c1b3c6d) >>> 0;
    return (h % 1000) / 1000;
  };

  const variante = Math.floor(r() * 3);
  const formas: string[] = [];
  if (variante === 0) {
    // anillos concéntricos
    const cx = 420 + r() * 140, cy = 60 + r() * 200;
    for (let i = 5; i >= 1; i--) {
      formas.push(`<circle cx="${cx.toFixed(0)}" cy="${cy.toFixed(0)}" r="${i * 52}" fill="none" stroke="${t.tinta}" stroke-opacity="${0.05 + i * 0.012}" stroke-width="18"/>`);
    }
  } else if (variante === 1) {
    // franjas diagonales
    const ang = -25 - r() * 25;
    for (let i = 0; i < 7; i++) {
      formas.push(`<rect x="${260 + i * 64}" y="-120" width="26" height="620" fill="${t.tinta}" fill-opacity="${(0.04 + (i % 3) * 0.025).toFixed(3)}" transform="rotate(${ang.toFixed(1)} 320 180)"/>`);
    }
  } else {
    // rejilla de puntos
    for (let x = 0; x < 8; x++) {
      for (let y = 0; y < 6; y++) {
        if (r() > 0.55) formas.push(`<circle cx="${360 + x * 36}" cy="${30 + y * 44}" r="${(4 + r() * 7).toFixed(1)}" fill="${t.tinta}" fill-opacity="0.13"/>`);
      }
    }
  }
  const sol = `<circle cx="${(560 - r() * 60).toFixed(0)}" cy="${(300 - r() * 40).toFixed(0)}" r="${(70 + r() * 40).toFixed(0)}" fill="${t.acento}"/>`;

  const codigo = esc((d.codigo || t.etiqueta).toUpperCase());
  const glifo = esc(t.glifo);
  const glifoSize = t.glifo.length > 2 ? 132 : 150;
  const modalidad = d.modalidad === 'ONLINE' ? 'ONLINE' : d.modalidad === 'PRESENCIAL' ? 'PRESENCIAL' : '';

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 360" preserveAspectRatio="xMidYMid slice">`
    + `<rect width="640" height="360" fill="${t.fondo}"/>`
    + formas.join('')
    + sol
    + `<rect x="0" y="0" width="14" height="360" fill="#000" fill-opacity="0.18"/>`
    + `<text x="44" y="96" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="22" font-weight="700" letter-spacing="2" fill="${t.acento}">${codigo}</text>`
    + `<text x="40" y="232" font-family="ui-monospace, Menlo, Consolas, monospace" font-size="${glifoSize}" font-weight="800" fill="${t.tinta}">${glifo}</text>`
    + `<text x="44" y="290" font-family="'Segoe UI', Arial, sans-serif" font-size="22" font-weight="700" letter-spacing="3" fill="${t.tinta}" fill-opacity="0.85">${esc(t.etiqueta.toUpperCase())}${modalidad ? ` · ${modalidad}` : ''}</text>`
    + `</svg>`;
}

const cache = new Map<string, string>();

/** Data URL lista para usar en <img [src]>. */
export function portadaUrl(d: DatosPortada): string {
  const k = `${d.codigo ?? ''}|${d.nombre}|${d.modalidad ?? ''}`;
  let url = cache.get(k);
  if (!url) {
    url = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(portadaSvg(d))}`;
    cache.set(k, url);
  }
  return url;
}
