import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';

interface ItemMenu { ruta: string; texto: string; icono: string }

/** Consola de operación: una sección por microservicio. */
@Component({
  selector: 'app-admin-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, LogoComponent],
  template: `
    <a class="skip" href="#contenido">Saltar al contenido</a>
    <div class="admin" [class.menu-abierto]="menu()">
      <aside class="sidebar" id="admin-nav">
        <a routerLink="/admin" class="marca marca-clara"><app-logo /> Academia <span class="marca-sub">Admin</span></a>
        <nav aria-label="Administración">
          @for (it of items; track it.ruta) {
            <a [routerLink]="it.ruta" routerLinkActive="activo" (click)="menu.set(false)">
              <app-icon [name]="it.icono" [size]="18" /> {{ it.texto }}
            </a>
          }
        </nav>
        <div class="sidebar-pie">
          <a routerLink="/cursos" (click)="menu.set(false)"><app-icon name="arrow-left" [size]="16" /> Ver sitio</a>
          <button type="button" (click)="salir()"><app-icon name="log-out" [size]="16" /> Cerrar sesión</button>
        </div>
      </aside>

      <div class="admin-main">
        <header class="admin-top">
          <button type="button" class="btn-icon menu-btn" (click)="menu.set(!menu())"
                  [attr.aria-expanded]="menu()" aria-controls="admin-nav" aria-label="Abrir menú"><app-icon name="menu" /></button>
          <span class="muted small">{{ auth.usuario()?.nombre }} · Administrador</span>
        </header>
        <main id="contenido" tabindex="-1" class="admin-content"><router-outlet /></main>
      </div>

      @if (menu()) { <div class="scrim" (click)="menu.set(false)" aria-hidden="true"></div> }
    </div>
  `,
})
export class AdminShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly menu = signal(false);

  protected readonly items: ItemMenu[] = [
    { ruta: '/admin/resumen', texto: 'Resumen', icono: 'grid' },
    { ruta: '/admin/cursos', texto: 'Cursos', icono: 'book-open' },
    { ruta: '/admin/inscripciones', texto: 'Inscripciones', icono: 'clipboard' },
    { ruta: '/admin/pagos', texto: 'Pagos', icono: 'credit-card' },
    { ruta: '/admin/comprobantes', texto: 'Comprobantes', icono: 'file-text' },
    { ruta: '/admin/notificaciones', texto: 'Notificaciones', icono: 'message' },
    { ruta: '/admin/seguridad', texto: 'Seguridad', icono: 'shield' },
  ];

  protected salir(): void {
    this.auth.cerrarSesion();
    void this.router.navigate(['/ingresar']);
  }
}
