import { Component, ElementRef, effect, input, output, viewChild } from '@angular/core';
import { IconComponent } from './icon.component';

/**
 * Modal / panel lateral sobre <dialog> nativo: trampa de foco, Escape y capa superior sin librerías.
 * El padre controla `abierto`; `cerrar` se emite con el botón, Escape o clic en el fondo.
 */
@Component({
  selector: 'app-modal',
  imports: [IconComponent],
  template: `
    <dialog #dlg class="modal" [class.drawer]="lateral()" [class.ancho-sm]="ancho() === 'sm'" [class.ancho-lg]="ancho() === 'lg'"
            (close)="cerrar.emit()" (click)="alClicFondo($event)" [attr.aria-label]="titulo()">
      <div class="modal-panel">
        <header class="modal-head">
          <h2>{{ titulo() }}</h2>
          <button type="button" class="btn-icon" (click)="cerrar.emit()" aria-label="Cerrar"><app-icon name="x" /></button>
        </header>
        <div class="modal-body"><ng-content /></div>
      </div>
    </dialog>
  `,
})
export class ModalComponent {
  readonly abierto = input(false);
  readonly titulo = input('');
  readonly lateral = input(false);
  readonly ancho = input<'sm' | 'md' | 'lg'>('md');
  readonly cerrar = output<void>();

  private readonly dlg = viewChild<ElementRef<HTMLDialogElement>>('dlg');

  constructor() {
    effect(() => {
      const d = this.dlg()?.nativeElement;
      if (!d) return;
      if (this.abierto() && !d.open) d.showModal();
      else if (!this.abierto() && d.open) d.close();
    });
  }

  protected alClicFondo(ev: MouseEvent): void {
    if (ev.target === ev.currentTarget) this.cerrar.emit(); // clic en el ::backdrop
  }
}
