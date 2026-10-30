/** Minúsculas y sin tildes: para búsquedas tolerantes ("matricula" encuentra "Matrícula"). */
export function normalizar(s: string | null | undefined): string {
  return (s ?? '').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().trim();
}

/** Solo permite rutas internas para returnUrl (evita open-redirect). */
export function rutaInternaSegura(url: string | null | undefined): string | null {
  if (!url || !url.startsWith('/') || url.startsWith('//') || url.includes('\\')) return null;
  return url;
}

export function esUrlHttp(valor: string | null | undefined): boolean {
  return !!valor && /^https?:\/\//i.test(valor);
}
