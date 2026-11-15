import { Component, inject } from '@angular/core';
import { ToastService } from '../core/services/toast.service';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-toasts',
  imports: [IconComponent],
  template: `
    <div class="toasts" aria-live="polite">
      @for (t of toast.lista(); track t.id) {
        <div class="toast" [class]="'toast toast-' + t.tipo" [attr.role]="t.tipo === 'error' ? 'alert' : 'status'">
          <app-icon [name]="t.tipo === 'exito' ? 'check-circle' : t.tipo === 'error' ? 'alert-circle' : 'info'" [size]="20" />
          <span>{{ t.mensaje }}</span>
          <button type="button" class="btn-icon" (click)="toast.cerrar(t.id)" aria-label="Cerrar aviso"><app-icon name="x" [size]="16" /></button>
        </div>
      }
    </div>
  `,
})
export class ToastsComponent {
  protected readonly toast = inject(ToastService);
}
