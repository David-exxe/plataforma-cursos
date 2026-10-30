import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { Curso, CursoAdmin, CursoRequest } from '../models';

@Injectable({ providedIn: 'root' })
export class CursosService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /* Público */
  catalogo() {
    return this.http.get<Curso[]>(`${this.base}/api/cursos`);
  }

  detalle(id: number | string) {
    return this.http.get<Curso>(`${this.base}/api/cursos/${id}`);
  }

  /* ADMIN */
  adminListar() {
    return this.http.get<CursoAdmin[]>(`${this.base}/api/admin/cursos`);
  }

  adminCrear(r: CursoRequest) {
    return this.http.post<CursoAdmin>(`${this.base}/api/admin/cursos`, r);
  }

  adminActualizar(id: number, r: CursoRequest) {
    return this.http.put<CursoAdmin>(`${this.base}/api/admin/cursos/${id}`, r);
  }
}
