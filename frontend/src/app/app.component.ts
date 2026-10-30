import { Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { GatewayStatusService } from './core/http/gateway-status.service';
import { fechaHora } from './core/util/fechas';
import { ConfirmHostComponent } from './shared/confirm-host.component';
import { IconComponent } from './shared/icon.component';
import { ToastsComponent } from './shared/toasts.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastsComponent, ConfirmHostComponent, IconComponent],
  template: `
    @if (gateway.bloqueo()) {
      <div class="banner-ip" role="alert">
        <app-icon name="slash" [size]="18" />
        <span>{{ textoBloqueo() }}</span>
      </div>
    }
    <router-outlet />
    <app-toasts />
    <app-confirm-host />
  `,
})
export class AppComponent {
  protected readonly gateway = inject(GatewayStatusService);

  protected readonly textoBloqueo = computed(() => {
    const hasta = this.gateway.bloqueo()?.hasta;
    if (!hasta) return 'Tu conexión está bloqueada temporalmente por seguridad. Vuelve a intentarlo en unos minutos.';
    if (hasta === 'permanente') return 'Esta dirección IP está bloqueada. Contacta al administrador de la academia.';
    return `Tu conexión está bloqueada temporalmente por seguridad. Podrás volver a usar la plataforma después de ${fechaHora(hasta)}.`;
  });
}
