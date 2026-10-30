import { Component, computed, inject, signal } from '@angular/core';
import { toDataURL } from 'qrcode';
import { AuthService } from '../../core/auth/auth.service';
import { mensajeDe } from '../../core/http/api-error';
import { Configurar2faResponse } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { CodigoComponent } from '../../shared/codigo.component';
import { IconComponent } from '../../shared/icon.component';

type Modo = 'reposo' | 'configurando' | 'desactivando';

/**
 * Perfil + verificación en dos pasos (TOTP):
 *  configurar → muestra QR/clave → activar (con un código válido) | desactivar (con un código válido).
 */
@Component({
  selector: 'app-cuenta',
  imports: [IconComponent, CodigoComponent],
  template: `
    <section class="container page page-estrecha">
      <header class="page-head"><h1>Mi cuenta</h1></header>

      @if (usuario(); as u) {
        <div class="card card-pad stack">
          <h2>Tus datos</h2>
          <dl class="datos">
            <div><dt>Nombre</dt><dd>{{ u.nombre }}</dd></div>
            <div><dt>Correo</dt><dd>{{ u.email }}</dd></div>
            <div><dt>Celular (WhatsApp)</dt><dd class="num">{{ u.telefono }}</dd></div>
            <div><dt>Tipo de cuenta</dt><dd>{{ u.rol === 'ADMIN' ? 'Administrador' : 'Estudiante' }}</dd></div>
          </dl>
        </div>

        <div class="card card-pad stack seguridad">
          <div class="row">
            <h2>Verificación en dos pasos</h2>
            <span class="spacer"></span>
            <span class="badge" [class.badge-ok]="u.dosFactores" [class.badge-neutral]="!u.dosFactores">{{ u.dosFactores ? 'Activada' : 'Desactivada' }}</span>
          </div>
          <p class="muted">Al ingresar te pediremos, además de tu contraseña, un código de 6 dígitos de tu app autenticadora (Google Authenticator o Microsoft Authenticator).</p>

          @if (!u.dosFactores && u.rol === 'ADMIN' && modo() === 'reposo') {
            <div class="alert alert-warn"><app-icon name="alert-triangle" [size]="18" /><div>Como administrador, te recomendamos activarla: tu cuenta gestiona pagos y datos de estudiantes.</div></div>
          }
          @if (error()) {
            <div class="alert alert-bad" role="alert"><app-icon name="alert-circle" [size]="18" /><div>{{ error() }}</div></div>
          }

          @switch (modo()) {
            @case ('reposo') {
              <div class="row">
                @if (u.dosFactores) {
                  <button type="button" class="btn btn-ghost" (click)="iniciarDesactivacion()">Desactivar</button>
                } @else {
                  <button type="button" class="btn btn-brand" [disabled]="trabajando()" (click)="iniciarConfiguracion()">
                    @if (trabajando()) { <span class="spinner"></span> } <app-icon name="smartphone" [size]="16" /> Activar verificación
                  </button>
                }
              </div>
            }
            @case ('configurando') {
              <ol class="pasos-lista">
                <li><strong>Escanea el código QR</strong> con tu app autenticadora.</li>
              </ol>
              <div class="qr-bloque">
                @if (qr()) { <img class="qr" [src]="qr()" width="200" height="200" alt="Código QR para configurar la verificación en dos pasos" /> }
                <div class="stack">
                  <p class="small muted">¿No puedes escanear? Escribe esta clave en la app (tipo: basada en tiempo):</p>
                  <p class="clave mono">{{ claveFormateada() }}</p>
                  <button type="button" class="btn btn-ghost btn-sm" (click)="copiarClave()">
                    <app-icon [name]="copiado() ? 'check' : 'copy'" [size]="16" /> {{ copiado() ? 'Copiada' : 'Copiar clave' }}
                  </button>
                </div>
              </div>
              <ol class="pasos-lista" start="2">
                <li><strong>Ingresa el código de 6 dígitos</strong> que muestra la app para confirmar que quedó bien configurada.</li>
              </ol>
              <app-codigo [(valor)]="codigo" [autoenfocar]="true" [deshabilitado]="trabajando()" (completo)="activar($event)" />
              <div class="row">
                <button type="button" class="btn btn-brand" [disabled]="trabajando() || codigo().length !== 6" (click)="activar(codigo())">
                  @if (trabajando()) { <span class="spinner"></span> } Activar
                </button>
                <button type="button" class="btn btn-ghost" (click)="cancelar()">Cancelar</button>
              </div>
            }
            @case ('desactivando') {
              <p>Para desactivarla, ingresa un código actual de tu app autenticadora.</p>
              <app-codigo [(valor)]="codigo" [autoenfocar]="true" [deshabilitado]="trabajando()" (completo)="desactivar($event)" />
              <div class="row">
                <button type="button" class="btn btn-danger" [disabled]="trabajando() || codigo().length !== 6" (click)="desactivar(codigo())">
                  @if (trabajando()) { <span class="spinner"></span> } Desactivar
                </button>
                <button type="button" class="btn btn-ghost" (click)="cancelar()">Cancelar</button>
              </div>
            }
          }
        </div>
      }
    </section>
  `,
})
export class CuentaComponent {
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  protected readonly usuario = this.auth.usuario;
  protected readonly modo = signal<Modo>('reposo');
  protected readonly config = signal<Configurar2faResponse | null>(null);
  protected readonly qr = signal<string | null>(null);
  protected readonly codigo = signal('');
  protected readonly trabajando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly copiado = signal(false);

  protected readonly claveFormateada = computed(() => (this.config()?.secreto ?? '').replace(/(.{4})/g, '$1 ').trim());

  constructor() {
    // Sincroniza el estado real del 2FA con el servidor (la sesión guardada puede estar desactualizada)
    this.auth.perfil().subscribe({ error: () => undefined });
  }

  protected iniciarConfiguracion(): void {
    this.trabajando.set(true);
    this.error.set(null);
    this.auth.configurar2fa().subscribe({
      next: async (c) => {
        this.config.set(c);
        try {
          this.qr.set(await toDataURL(c.otpauthUri, { width: 200, margin: 1, errorCorrectionLevel: 'M' }));
        } catch {
          this.qr.set(null); // queda la clave escrita como alternativa
        }
        this.codigo.set('');
        this.modo.set('configurando');
        this.trabajando.set(false);
      },
      error: (e: unknown) => {
        this.trabajando.set(false);
        this.error.set(mensajeDe(e));
      },
    });
  }

  protected activar(codigo: string): void {
    if (codigo.length !== 6 || this.trabajando()) return;
    this.trabajando.set(true);
    this.error.set(null);
    this.auth.activar2fa(codigo).subscribe({
      next: () => {
        this.toast.exito('Verificación en dos pasos activada.');
        this.terminar();
      },
      error: (e: unknown) => {
        this.trabajando.set(false);
        this.codigo.set('');
        this.error.set(mensajeDe(e));
      },
    });
  }

  protected iniciarDesactivacion(): void {
    this.error.set(null);
    this.codigo.set('');
    this.modo.set('desactivando');
  }

  protected desactivar(codigo: string): void {
    if (codigo.length !== 6 || this.trabajando()) return;
    this.trabajando.set(true);
    this.error.set(null);
    this.auth.desactivar2fa(codigo).subscribe({
      next: () => {
        this.toast.info('Verificación en dos pasos desactivada.');
        this.terminar();
      },
      error: (e: unknown) => {
        this.trabajando.set(false);
        this.codigo.set('');
        this.error.set(mensajeDe(e));
      },
    });
  }

  protected cancelar(): void {
    this.terminar();
  }

  protected async copiarClave(): Promise<void> {
    const clave = this.config()?.secreto;
    if (!clave) return;
    try {
      await navigator.clipboard.writeText(clave);
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2000);
    } catch {
      this.toast.info('No pudimos copiarla automáticamente. Selecciónala y cópiala a mano.');
    }
  }

  private terminar(): void {
    this.modo.set('reposo');
    this.config.set(null);
    this.qr.set(null);
    this.codigo.set('');
    this.error.set(null);
    this.trabajando.set(false);
  }
}
