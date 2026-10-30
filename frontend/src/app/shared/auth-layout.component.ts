import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from './icon.component';
import { LogoComponent } from './logo.component';

/** Estructura compartida por ingresar / registro: panel de marca con el flujo real + formulario. */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterLink, IconComponent, LogoComponent],
  template: `
    <div class="auth">
      <aside class="auth-marca">
        <a routerLink="/cursos" class="marca marca-clara"><app-logo /> Academia</a>
        <div class="auth-marca-centro">
          <h2>Tu cupo, confirmado en minutos.</h2>
          <ol class="auth-pasos">
            <li><strong>Reserva</strong><span>Elige el curso y asegura tu cupo por 72 horas.</span></li>
            <li><strong>Paga</strong><span>Con Mercado Pago, con tarjeta u otros medios.</span></li>
            <li><strong>Recibe</strong><span>Confirmación por WhatsApp y comprobante PDF en tu correo.</span></li>
          </ol>
        </div>
        <p class="small auth-marca-pie">¿Dudas? Escríbenos por WhatsApp y nuestro asistente te responde al instante.</p>
      </aside>

      <main id="contenido" class="auth-form">
        <a routerLink="/cursos" class="volver"><app-icon name="arrow-left" [size]="16" /> Ver cursos</a>
        <div class="auth-caja">
          <h1>{{ titulo() }}</h1>
          @if (subtitulo()) { <p class="lead">{{ subtitulo() }}</p> }
          <ng-content />
        </div>
      </main>
    </div>
  `,
})
export class AuthLayoutComponent {
  readonly titulo = input.required<string>();
  readonly subtitulo = input('');
}
