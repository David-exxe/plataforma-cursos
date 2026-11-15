import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  tipo: 'exito' | 'error' | 'info';
  mensaje: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly lista = signal<Toast[]>([]);
  private secuencia = 0;

  exito(mensaje: string): void { this.agregar('exito', mensaje, 4500); }
  info(mensaje: string): void { this.agregar('info', mensaje, 5000); }
  error(mensaje: string): void { this.agregar('error', mensaje, 8000); }

  cerrar(id: number): void {
    this.lista.update((l) => l.filter((t) => t.id !== id));
  }

  private agregar(tipo: Toast['tipo'], mensaje: string, ms: number): void {
    const id = ++this.secuencia;
    this.lista.update((l) => [...l.slice(-3), { id, tipo, mensaje }]);
    setTimeout(() => this.cerrar(id), ms);
  }
}
