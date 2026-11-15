import {
  HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest, HttpResponse,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, from, map, mergeMap, retry, tap, throwError, timer } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { ErrorApi } from '../models';
import { ApiError, construirError } from './api-error';
import { GatewayStatusService } from './gateway-status.service';

/**
 * Rutas públicas del gateway (ver SeguridadConfig). IMPORTANTE: no se les adjunta el token.
 * Spring Security rechaza con 401 un Bearer inválido/expirado incluso en rutas permitAll,
 * así que un token vencido en localStorage rompería el catálogo si lo enviáramos.
 */
const PUBLICAS: { metodo: string; ruta: RegExp }[] = [
  { metodo: 'POST', ruta: /^\/api\/auth\/(registro|login|2fa\/verificar)$/ },
  { metodo: 'GET', ruta: /^\/api\/cursos(\/\d+)?$/ },
];

function rutaDe(url: string): string {
  return url.slice(environment.apiUrl.length).split('?')[0];
}

function esPublica(req: HttpRequest<unknown>): boolean {
  const ruta = rutaDe(req.url);
  return PUBLICAS.some((p) => p.metodo === req.method && p.ruta.test(ruta));
}

/** 1) Adjunta "Authorization: Bearer" solo al gateway y solo a rutas protegidas. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiUrl) || esPublica(req)) return next(req);
  const token = inject(AuthService).tokenVigente();
  return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req);
};

function esTransitorio(e: unknown): boolean {
  return e instanceof HttpErrorResponse && [0, 502, 503, 504].includes(e.status);
}

async function leerCuerpo(err: HttpErrorResponse): Promise<ErrorApi | null> {
  if (err.status === 0) return null;
  try {
    let crudo: unknown = err.error;
    if (crudo instanceof Blob) crudo = await crudo.text(); // descargas PDF (responseType: blob)
    if (typeof crudo === 'string') return crudo ? (JSON.parse(crudo) as ErrorApi) : null;
    return crudo && typeof crudo === 'object' ? (crudo as ErrorApi) : null;
  } catch {
    return null;
  }
}

/**
 * 2) Normaliza errores a ApiError, reintenta una vez los GET si el gateway no responde,
 *    cierra sesión ante 401 en rutas protegidas y avisa cuando la IP está bloqueada.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const gateway = inject(GatewayStatusService);

  let flujo: Observable<HttpEvent<unknown>> = next(req);
  if (req.method === 'GET') {
    flujo = flujo.pipe(retry({ count: 1, delay: (e: unknown) => (esTransitorio(e) ? timer(600) : throwError(() => e)) }));
  }

  return flujo.pipe(
    tap((ev) => {
      if (ev instanceof HttpResponse) gateway.limpiar();
    }),
    catchError((err: unknown): Observable<never> => {
      if (!(err instanceof HttpErrorResponse)) return throwError(() => err);
      return from(leerCuerpo(err)).pipe(
        map((cuerpo) => construirError(err.status, cuerpo)),
        tap((e: ApiError) => {
          if (e.tipo === 'ip-bloqueada') gateway.marcarBloqueada(e.hasta);
          if (e.estado === 401 && !esPublica(req) && req.url.startsWith(environment.apiUrl)) {
            auth.cerrarSesion(true);
            if (!router.url.startsWith('/ingresar')) {
              void router.navigate(['/ingresar'], { queryParams: { returnUrl: router.url } });
            }
          }
        }),
        mergeMap((e) => throwError(() => e)),
      );
    }),
  );
};
