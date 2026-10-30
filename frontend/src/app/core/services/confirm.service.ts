import { Injectable, signal } from '@angular/core';

export interface OpcionesConfirmacion {
  titulo: string;
  mensaje: string;
  confirmar?: string;
  cancelar?: string;
  /** Acción destructiva: el botón se muestra en rojo. */
  peligro?: boolean;
}

export interface EstadoConfirmacion extends Required<OpcionesConfirmacion> {
  resolver: (valor: boolean) => void;
}

/** Diálogo de confirmación basado en promesas: if (await confirm.preguntar({...})) { ... } */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly estado = signal<EstadoConfirmacion | null>(null);

  preguntar(o: OpcionesConfirmacion): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      this.estado.set({
        titulo: o.titulo,
        mensaje: o.mensaje,
        confirmar: o.confirmar ?? 'Confirmar',
        cancelar: o.cancelar ?? 'Volver',
        peligro: o.peligro ?? false,
        resolver: resolve,
      });
    });
  }

  resolver(valor: boolean): void {
    const e = this.estado();
    if (!e) return; // idempotente: el evento "close" del <dialog> también llama aquí
    this.estado.set(null);
    e.resolver(valor);
  }
}
