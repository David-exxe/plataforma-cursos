/** Guarda un Blob como archivo descargado. */
export function guardarBlob(blob: Blob, nombre: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/** Extrae el nombre de archivo de Content-Disposition (el gateway lo expone por CORS). */
export function nombreDeContentDisposition(cabecera: string | null, porDefecto: string): string {
  if (!cabecera) return porDefecto;
  const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cabecera);
  if (!m) return porDefecto;
  try {
    return decodeURIComponent(m[1]);
  } catch {
    return m[1];
  }
}
