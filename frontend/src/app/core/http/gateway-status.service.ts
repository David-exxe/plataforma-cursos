import { Injectable, signal } from '@angular/core';

/** Estado global: el gateway bloquea IPs (fuerza bruta o exceso de peticiones) y responde 403/429. */
@Injectable({ providedIn: 'root' })
export class GatewayStatusService {
  readonly bloqueo = signal<{ hasta: string | null } | null>(null);

  marcarBloqueada(hasta: string | null): void {
    this.bloqueo.set({ hasta });
  }

  limpiar(): void {
    if (this.bloqueo()) this.bloqueo.set(null);
  }
}
