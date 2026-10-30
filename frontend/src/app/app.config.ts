import { ApplicationConfig, Injectable, inject, provideZoneChangeDetection } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  RouterStateSnapshot, TitleStrategy, provideRouter, withComponentInputBinding, withInMemoryScrolling,
} from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor, errorInterceptor } from './core/http/interceptors';

/** Título de pestaña: "Cursos · Academia". */
@Injectable({ providedIn: 'root' })
export class TituloAcademia extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const t = this.buildTitle(snapshot);
    this.title.setTitle(t ? `${t} · Academia` : 'Academia de Cursos');
  }
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
    ),
    // Orden: primero adjunta el token, después normaliza los errores de la respuesta.
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    { provide: TitleStrategy, useClass: TituloAcademia },
  ],
};
