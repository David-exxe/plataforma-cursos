import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { mensajeDe } from '../../core/http/api-error';
import { RegistroRequest } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { rutaInternaSegura } from '../../core/util/texto';
import { AuthLayoutComponent } from '../../shared/auth-layout.component';
import { Validadores, aplicarErroresServidor, errorDeCampo } from '../../shared/forms';
import { IconComponent } from '../../shared/icon.component';

const coinciden = (g: AbstractControl): ValidationErrors | null =>
  g.get('password')?.value === g.get('confirmar')?.value ? null : { noCoincide: true };

@Component({
  selector: 'app-registro',
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent, IconComponent],
  template: `
    <app-auth-layout titulo="Crea tu cuenta" subtitulo="Con tu cuenta puedes reservar cupos, pagar y descargar tus comprobantes.">
      @if (error()) {
        <div class="alert alert-bad" role="alert"><app-icon name="alert-circle" [size]="18" /><div>{{ error() }}</div></div>
      }
      <form [formGroup]="form" (ngSubmit)="enviar()" novalidate class="stack">
        <div class="field">
          <label for="nombre">Nombre completo</label>
          <input id="nombre" type="text" formControlName="nombre" autocomplete="name" [class.invalid]="!!err('nombre')" />
          @if (err('nombre'); as m) { <span class="error-msg">{{ m }}</span> }
        </div>

        <div class="field">
          <label for="email">Correo electrónico</label>
          <input id="email" type="email" formControlName="email" autocomplete="email" inputmode="email" [class.invalid]="!!err('email')" />
          <span class="hint">Aquí te enviaremos tu comprobante.</span>
          @if (err('email'); as m) { <span class="error-msg">{{ m }}</span> }
        </div>

        <div class="form-grid">
          <div class="field">
            <label for="telefono">Celular (WhatsApp)</label>
            <input id="telefono" type="tel" formControlName="telefono" autocomplete="tel" inputmode="tel" placeholder="987654321" [class.invalid]="!!err('telefono')" />
            @if (err('telefono'); as m) { <span class="error-msg">{{ m }}</span> }
          </div>
          <div class="field">
            <label for="documento">DNI o CE <span class="muted">(opcional)</span></label>
            <input id="documento" type="text" formControlName="documento" autocomplete="off" maxlength="12" [class.invalid]="!!err('documento')" />
            @if (err('documento'); as m) { <span class="error-msg">{{ m }}</span> } @else { <span class="hint">Aparece en tu comprobante.</span> }
          </div>
        </div>

        <div class="form-grid">
          <div class="field">
            <label for="password">Contraseña</label>
            <input id="password" [type]="verClave() ? 'text' : 'password'" formControlName="password" autocomplete="new-password" [class.invalid]="!!err('password')" />
            @if (err('password'); as m) { <span class="error-msg">{{ m }}</span> } @else { <span class="hint">Mínimo 8 caracteres, con letras y números.</span> }
          </div>
          <div class="field">
            <label for="confirmar">Repite la contraseña</label>
            <input id="confirmar" [type]="verClave() ? 'text' : 'password'" formControlName="confirmar" autocomplete="new-password" [class.invalid]="noCoincide()" />
            @if (noCoincide()) { <span class="error-msg">Las contraseñas no coinciden.</span> }
          </div>
        </div>

        <label class="check"><input type="checkbox" [checked]="verClave()" (change)="verClave.set($any($event.target).checked)" /> Mostrar contraseñas</label>

        <button type="submit" class="btn btn-brand btn-block" [disabled]="enviando()">
          @if (enviando()) { <span class="spinner"></span> } Crear cuenta
        </button>
      </form>
      <p class="auth-alt">¿Ya tienes cuenta? <a routerLink="/ingresar" queryParamsHandling="preserve">Ingresar</a></p>
    </app-auth-layout>
  `,
})
export class RegistroComponent {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  protected readonly form = this.fb.nonNullable.group(
    {
      nombre: ['', [Validators.required, Validators.maxLength(120)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
      telefono: ['', [Validators.required, Validadores.telefono]],
      documento: ['', [Validadores.documento]],
      password: ['', [Validators.required, Validadores.password]],
      confirmar: ['', [Validators.required]],
    },
    { validators: [coinciden] },
  );

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly verClave = signal(false);

  protected err(campo: string): string | null {
    return errorDeCampo(this.form, campo);
  }

  protected noCoincide(): boolean {
    return this.form.hasError('noCoincide') && this.form.controls.confirmar.touched;
  }

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const req: RegistroRequest = {
      nombre: v.nombre.trim(),
      email: v.email.trim(),
      telefono: v.telefono.trim(),
      documento: v.documento.trim() || null, // el backend rechaza "" (no cumple el patrón); se envía null
      password: v.password,
    };
    this.enviando.set(true);
    this.error.set(null);
    this.auth.registrar(req).subscribe({
      next: () => this.ingresarTrasRegistro(req),
      error: (e: unknown) => {
        this.enviando.set(false);
        if (!aplicarErroresServidor(this.form, e)) this.error.set(mensajeDe(e));
      },
    });
  }

  /** El registro no devuelve token: se inicia sesión con las mismas credenciales. */
  private ingresarTrasRegistro(req: RegistroRequest): void {
    this.auth.login({ email: req.email, password: req.password }).subscribe({
      next: () => {
        this.enviando.set(false);
        this.toast.exito('¡Cuenta creada! Ya puedes reservar tu cupo.');
        const destino = rutaInternaSegura(this.route.snapshot.queryParamMap.get('returnUrl'));
        void this.router.navigateByUrl(destino ?? '/cursos');
      },
      error: () => {
        this.enviando.set(false);
        this.toast.info('Cuenta creada. Ingresa con tu correo y contraseña.');
        void this.router.navigate(['/ingresar']);
      },
    });
  }
}
