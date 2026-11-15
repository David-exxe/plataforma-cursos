import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { ToastService } from '../core/services/toast.service';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';

@Component({
  selector: 'app-public-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, LogoComponent],
  template: `
    <a class="skip" href="#contenido">Saltar al contenido</a>
    <header class="topbar">
      <div class="container topbar-in">
        <a routerLink="/cursos" class="marca"><app-logo /> Academia</a>

        <nav id="nav-principal" class="nav" [class.abierto]="menu()" aria-label="Principal">
          <a routerLink="/cursos" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: false }" (click)="menu.set(false)">Cursos</a>
          @if (auth.autenticado()) {
            <a routerLink="/mis-inscripciones" routerLinkActive="activo" (click)="menu.set(false)">Mis inscripciones</a>
            <a routerLink="/comprobantes" routerLinkActive="activo" (click)="menu.set(false)">Comprobantes</a>
            @if (auth.esAdmin()) {
              <a routerLink="/admin" (click)="menu.set(false)">Panel admin</a>
            }
          }
          <span class="spacer"></span>
          @if (auth.autenticado()) {
            <a routerLink="/cuenta" class="nav-usuario" routerLinkActive="activo" (click)="menu.set(false)" aria-label="Mi cuenta">
              <span class="avatar" aria-hidden="true">{{ iniciales() }}</span>
              <span>{{ primerNombre() }}</span>
            </a>
            <button type="button" class="btn btn-ghost btn-sm" (click)="salir()"><app-icon name="log-out" [size]="16" /> Salir</button>
          } @else {
            <a routerLink="/ingresar" class="btn btn-ghost btn-sm" (click)="menu.set(false)">Ingresar</a>
            <a routerLink="/registro" class="btn btn-primary btn-sm" (click)="menu.set(false)">Crear cuenta</a>
          }
        </nav>

        <button type="button" class="btn-icon nav-toggle" (click)="menu.set(!menu())"
                [attr.aria-expanded]="menu()" aria-controls="nav-principal" aria-label="Abrir menú">
          <app-icon [name]="menu() ? 'x' : 'menu'" />
        </button>
      </div>
    </header>

    <main id="contenido" tabindex="-1"><router-outlet /></main>

    <footer class="footer">
      <div class="container footer-in">
        <span>© {{ anio }} Academia de Cursos</span>
        <span class="muted">Confirmaciones por WhatsApp y comprobante PDF en tu correo.</span>
      </div>
    </footer>
  `,
})
export class PublicShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly menu = signal(false);
  protected readonly anio = new Date().getFullYear();
  protected readonly primerNombre = computed(() => this.auth.usuario()?.nombre.trim().split(/\s+/)[0] ?? '');
  protected readonly iniciales = computed(() =>
    (this.auth.usuario()?.nombre ?? '').trim().split(/\s+/).slice(0, 2).map((p) => p.charAt(0).toUpperCase()).join(''));

  protected salir(): void {
    this.menu.set(false);
    this.auth.cerrarSesion();
    this.toast.info('Cerraste sesión.');
    void this.router.navigate(['/cursos']);
  }
}
