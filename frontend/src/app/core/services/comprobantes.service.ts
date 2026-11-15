import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { filtroEstado } from '../http/filtros';
import { Comprobante, ReenvioComprobanteResponse } from '../models';
import { guardarBlob, nombreDeContentDisposition } from '../util/descargas';

@Injectable({ providedIn: 'root' })
export class ComprobantesService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  mios() {
    return this.http.get<Comprobante[]>(`${this.base}/api/comprobantes/mios`);
  }

  /** Descarga el PDF aunque el correo haya fallado (el comprobante se guarda antes de enviarse). */
  descargar(c: Pick<Comprobante, 'id' | 'numero'>): Observable<void> {
    return this.http
      .get(`${this.base}/api/comprobantes/${c.id}/pdf`, { responseType: 'blob', observe: 'response' })
      .pipe(
        tap((res) => {
          if (res.body) {
            guardarBlob(res.body, nombreDeContentDisposition(res.headers.get('Content-Disposition'), `${c.numero}.pdf`));
          }
        }),
        map(() => undefined),
      );
  }

  /* ADMIN */
  admin(estado?: string | null) {
    return this.http.get<Comprobante[]>(`${this.base}/api/admin/comprobantes`, filtroEstado(estado));
  }

  reenviar(id: number) {
    return this.http.post<ReenvioComprobanteResponse>(`${this.base}/api/admin/comprobantes/${id}/reenviar`, {});
  }
}
