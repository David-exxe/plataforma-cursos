import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { filtroEstado } from '../http/filtros';
import { BloqueoIpRequest, IpBloqueada, Notificacion } from '../models';

/** notificaciones-service: mensajes de WhatsApp enviados/fallidos. */
@Injectable({ providedIn: 'root' })
export class NotificacionesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/notificaciones`;

  listar(estado?: string | null) {
    return this.http.get<Notificacion[]>(this.base, filtroEstado(estado));
  }

  /** Responde 202 sin cuerpo: la notificación vuelve a PENDIENTE y el planificador la reenvía. */
  reintentar(id: number) {
    return this.http.post<void>(`${this.base}/${id}/reintentar`, null);
  }
}

/** api-gateway: IPs bloqueadas (rate limit, fuerza bruta o manuales). */
@Injectable({ providedIn: 'root' })
export class IpsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/gateway/admin/ips`;

  /** El gateway responde un objeto { "<ip>": { hasta, motivo } }. */
  listar() {
    return this.http.get<Record<string, { hasta: string; motivo: string }>>(this.base).pipe(
      map((o): IpBloqueada[] => Object.entries(o).map(([ip, v]) => ({ ip, hasta: v.hasta, motivo: v.motivo }))),
    );
  }

  bloquear(r: BloqueoIpRequest) {
    return this.http.post<void>(this.base, r);
  }

  desbloquear(ip: string) {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(ip)}`);
  }
}
