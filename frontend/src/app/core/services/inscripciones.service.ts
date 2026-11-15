import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { filtroEstado } from '../http/filtros';
import { Inscripcion } from '../models';

@Injectable({ providedIn: 'root' })
export class InscripcionesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/inscripciones`;

  /** Crea la inscripción PENDIENTE_PAGO y devuelve el linkPago (puede venir null si pagos-service está caído). */
  inscribir(cursoId: number) {
    return this.http.post<Inscripcion>(this.base, { cursoId });
  }

  mias() {
    return this.http.get<Inscripcion[]>(`${this.base}/mias`);
  }

  detalle(id: number) {
    return this.http.get<Inscripcion>(`${this.base}/${id}`);
  }

  /** Regenera el link de pago (503 si pagos-service sigue caído). */
  regenerarLink(id: number) {
    return this.http.post<Inscripcion>(`${this.base}/${id}/link-pago`, {});
  }

  cancelar(id: number) {
    return this.http.post<Inscripcion>(`${this.base}/${id}/cancelar`, {});
  }

  /* ADMIN */
  admin(estado?: string | null) {
    return this.http.get<Inscripcion[]>(`${environment.apiUrl}/api/admin/inscripciones`, filtroEstado(estado));
  }

  /** Dispara los recordatorios de "mañana empieza tu curso" sin esperar al cron de las 9:00 a. m. */
  ejecutarRecordatorios() {
    return this.http.post<{ recordatoriosGenerados: number }>(`${environment.apiUrl}/api/admin/recordatorios/ejecutar`, {});
  }
}
