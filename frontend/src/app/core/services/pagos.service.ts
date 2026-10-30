import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { filtroEstado } from '../http/filtros';
import { Pago } from '../models';

@Injectable({ providedIn: 'root' })
export class PagosService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly base = environment.apiUrl;

  /** Estado del pago de una inscripción (página de retorno de Mercado Pago). */
  estado(inscripcionId: number) {
    return this.http.get<Pago>(`${this.base}/api/pagos/inscripcion/${inscripcionId}`);
  }

  /** Solo con MP_MODO=mock en el backend. */
  simular(inscripcionId: number, resultado: 'approved' | 'rejected') {
    return this.http.post<Pago>(`${this.base}/api/pagos/simulacion/${inscripcionId}`, null, { params: { resultado } });
  }

  admin(estado?: string | null) {
    return this.http.get<Pago[]>(`${this.base}/api/admin/pagos`, filtroEstado(estado));
  }

  /**
   * Abre el link de pago. En modo real es un init_point externo de Mercado Pago;
   * en modo mock apunta a /pago-simulado de este mismo frontend (se navega sin recargar).
   */
  abrirLink(url: string): void {
    try {
      const destino = new URL(url, window.location.origin);
      if (destino.origin === window.location.origin) {
        void this.router.navigateByUrl(destino.pathname + destino.search + destino.hash);
        return;
      }
    } catch {
      /* URL inválida: se intenta abrir tal cual */
    }
    window.location.assign(url);
  }
}
