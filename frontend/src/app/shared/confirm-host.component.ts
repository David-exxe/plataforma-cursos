import { Component, inject } from '@angular/core';
import { ConfirmService } from '../core/services/confirm.service';
import { ModalComponent } from './modal.component';

@Component({
  selector: 'app-confirm-host',
  imports: [ModalComponent],
  template: `
    <app-modal [abierto]="svc.estado() !== null" [titulo]="svc.estado()?.titulo ?? ''" ancho="sm" (cerrar)="svc.resolver(false)">
      @if (svc.estado(); as e) {
        <p>{{ e.mensaje }}</p>
        <div class="modal-actions">
          <button type="button" class="btn btn-ghost" (click)="svc.resolver(false)">{{ e.cancelar }}</button>
          <button type="button" class="btn" [class.btn-danger]="e.peligro" [class.btn-brand]="!e.peligro" (click)="svc.resolver(true)">{{ e.confirmar }}</button>
        </div>
      }
    </app-modal>
  `,
})
export class ConfirmHostComponent {
  protected readonly svc = inject(ConfirmService);
}
