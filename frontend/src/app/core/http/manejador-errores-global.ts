import { ErrorHandler, Injectable, NgZone, inject } from '@angular/core';
import { ToastService } from '../services/toast.service';
import { ApiError, mensajeDe } from './api-error';

/**
 * Último recurso: errores que ninguna pantalla atrapó (un subscribe sin callback de error,
 * una promesa rechazada, un fallo al cargar una pantalla tras desplegar una versión nueva…).
 * En vez de que la app "no haga nada", el usuario ve un aviso claro y el detalle queda en consola.
 */
@Injectable()
export class ManejadorErroresGlobal implements ErrorHandler {
  private readonly toast = inject(ToastService);
  private readonly zone = inject(NgZone);
  private ultimo = { texto: '', cuando: 0 };

  handleError(error: unknown): void {
    const e = desenvolver(error);
    console.error('[Academia] Error no controlado:', e);

    // 401 (redirige al login) e IP bloqueada (banner propio) ya tienen su aviso.
    if (e instanceof ApiError && (e.tipo === 'sesion' || e.tipo === 'ip-bloqueada')) return;

    const texto = esErrorDeCarga(e)
      ? 'Hay una nueva versión de la plataforma. Recarga la página (F5) para continuar.'
      : mensajeDe(e, 'Algo salió mal. Si el problema continúa, recarga la página.');

    // Evita repetir el mismo aviso varias veces seguidas.
    const ahora = Date.now();
    if (texto === this.ultimo.texto && ahora - this.ultimo.cuando < 4000) return;
    this.ultimo = { texto, cuando: ahora };

    this.zone.run(() => this.toast.error(texto));
  }
}

/** Angular y RxJS a veces envuelven el error original (rejection de promesas, errores de carga). */
function desenvolver(error: unknown): unknown {
  let e: unknown = error;
  for (let i = 0; i < 3 && e && typeof e === 'object'; i++) {
    const r = e as { rejection?: unknown; originalError?: unknown };
    if (r.rejection !== undefined) e = r.rejection;
    else if (r.originalError !== undefined) e = r.originalError;
    else break;
  }
  return e;
}

/** Tras un nuevo despliegue, los archivos .js antiguos de las pantallas ya no existen. */
function esErrorDeCarga(e: unknown): boolean {
  const msg = e instanceof Error ? e.message : String(e ?? '');
  return /ChunkLoadError|Loading chunk|Failed to fetch dynamically imported module|Importing a module script failed/i.test(msg);
}
