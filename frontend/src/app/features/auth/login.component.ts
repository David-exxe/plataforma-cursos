import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError, mensajeDe } from '../../core/http/api-error';
import { rutaInternaSegura } from '../../core/util/texto';
import { AuthLayoutComponent } from '../../shared/auth-layout.component';
import { CodigoComponent } from '../../shared/codigo.component';
import { errorDeCampo } from '../../shared/forms';
import { IconComponent } from '../../shared/icon.component';

/**
 * Login en dos pasos cuando el usuario tiene 2FA:
 *  1) POST /api/auth/login            → { requiere2fa, tokenTemporal (5 min) }
 *  2) POST /api/auth/2fa/verificar    → JWT real
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent, IconComponent, CodigoComponent],
  template: `
    <app-auth-layout
      [titulo]="paso() === 'credenciales' ? 'Ingresa a tu cuenta' : 'Verificación en dos pasos'"
      [subtitulo]="paso() === 'credenciales' ? 'Usa el correo con el que te registraste.' : 'Abre tu app autenticadora (Google Authenticator, Microsoft Authenticator) e ingresa el código de 6 dígitos.'">

      @if (auth.sesionExpirada() && paso() === 'credenciales') {
        <div class="alert alert-info" role="status"><app-icon name="info" [size]="18" /><div>Tu sesión expiró. Ingresa de nuevo para continuar.</div></div>
      }
      @if (error()) {
        <div class="alert alert-bad" role="alert"><app-icon name="alert-circle" [size]="18" /><div>{{ error() }}</div></div>
      }

      @if (paso() === 'credenciales') {
        <form [formGroup]="form" (ngSubmit)="enviar()" novalidate class="stack">
          <div class="field">
            <label for="email">Correo electrónico</label>
            <input id="email" type="email" formControlName="email" autocomplete="email" inputmode="email"
                   [class.invalid]="!!err('email')" [attr.aria-invalid]="!!err('email')" />
            @if (err('email'); as m) { <span class="error-msg">{{ m }}</span> }
          </div>

          <div class="field">
            <label for="password">Contraseña</label>
            <div class="input-icon">
              <input id="password" [type]="verClave() ? 'text' : 'password'" formControlName="password" autocomplete="current-password"
                     [class.invalid]="!!err('password')" [attr.aria-invalid]="!!err('password')" />
              <button type="button" class="btn-icon" (click)="verClave.set(!verClave())"
                      [attr.aria-label]="verClave() ? 'Ocultar contraseña' : 'Mostrar contraseña'">
                <app-icon [name]="verClave() ? 'eye-off' : 'eye'" [size]="18" />
              </button>
            </div>
            @if (err('password'); as m) { <span class="error-msg">{{ m }}</span> }
          </div>

          <button type="submit" class="btn btn-brand btn-block" [disabled]="enviando()">
            @if (enviando()) { <span class="spinner"></span> } Ingresar
          </button>
        </form>
        <p class="auth-alt">¿Aún no tienes cuenta? <a routerLink="/registro" queryParamsHandling="preserve">Crear cuenta</a></p>
      } @else {
        <div class="stack">
          <div class="field">
            <span class="label">Código de verificación</span>
            <app-codigo [(valor)]="codigo" [autoenfocar]="true" [deshabilitado]="enviando()" (completo)="verificar($event)" />
            <span class="hint">El código vence en <strong class="num">{{ tiempo() }}</strong>. Cambia cada 30 segundos.</span>
          </div>
          @if (fallos2fa() >= 3) {
            <div class="alert alert-warn" role="status">
              <app-icon name="alert-triangle" [size]="18" />
              <div>Varios códigos incorrectos. Tras 5 intentos fallidos tu cuenta se bloquea 15 minutos.</div>
            </div>
          }
          <button type="button" class="btn btn-brand btn-block" [disabled]="enviando() || codigo().length !== 6" (click)="verificar(codigo())">
            @if (enviando()) { <span class="spinner"></span> } Verificar y entrar
          </button>
          <button type="button" class="btn btn-ghost btn-block" (click)="volver()">Volver</button>
        </div>
      }
    </app-auth-layout>
  `,
})
export class LoginComponent {
  protected readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    password: ['', [Validators.required]],
  });

  protected readonly paso = signal<'credenciales' | 'codigo'>('credenciales');
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly verClave = signal(false);
  protected readonly codigo = signal('');
  protected readonly fallos2fa = signal(0);
  private readonly segundos = signal(0);
  protected readonly tiempo = computed(() => {
    const s = Math.max(0, this.segundos());
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  });

  private tokenTemporal = '';
  private reloj: ReturnType<typeof setInterval> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.detenerReloj());
  }

  protected err(campo: string): string | null {
    return errorDeCampo(this.form, campo);
  }

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.auth.login({ email: v.email.trim(), password: v.password }).subscribe({
      next: (r) => {
        this.enviando.set(false);
        if (r.requiere2fa && r.tokenTemporal) this.pedirCodigo(r.tokenTemporal, r.expiraEnSegundos);
        else this.entrar();
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDe(e));
      },
    });
  }

  protected verificar(codigo: string): void {
    if (codigo.length !== 6 || this.enviando()) return;
    this.enviando.set(true);
    this.error.set(null);
    this.auth.verificar2fa(this.tokenTemporal, codigo).subscribe({
      next: () => {
        this.detenerReloj();
        this.entrar();
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.codigo.set('');
        this.fallos2fa.update((n) => n + 1);
        const msg = mensajeDe(e);
        // El token temporal dura 5 min: si venció hay que empezar de nuevo
        if (e instanceof ApiError && e.estado === 401 && msg.includes('venció')) this.volver(msg);
        else this.error.set(msg);
      },
    });
  }

  protected volver(mensaje?: string): void {
    this.detenerReloj();
    this.paso.set('credenciales');
    this.codigo.set('');
    this.tokenTemporal = '';
    this.fallos2fa.set(0);
    this.error.set(mensaje ?? null);
  }

  private pedirCodigo(tokenTemporal: string, expiraEnSegundos: number): void {
    this.tokenTemporal = tokenTemporal;
    this.codigo.set('');
    this.fallos2fa.set(0);
    this.segundos.set(expiraEnSegundos);
    this.paso.set('codigo');
    this.detenerReloj();
    this.reloj = setInterval(() => {
      this.segundos.update((s) => s - 1);
      if (this.segundos() <= 0) this.volver('El tiempo para ingresar el código venció. Inicia sesión de nuevo.');
    }, 1000);
  }

  private detenerReloj(): void {
    if (this.reloj) clearInterval(this.reloj);
    this.reloj = null;
  }

  private entrar(): void {
    const destino = rutaInternaSegura(this.route.snapshot.queryParamMap.get('returnUrl'));
    void this.router.navigateByUrl(destino ?? (this.auth.esAdmin() ? '/admin' : '/cursos'));
  }
}
