import { Component, computed, input, model } from '@angular/core';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-paginacion',
  imports: [IconComponent],
  template: `
    @if (paginas() > 1) {
      <nav class="paginacion" aria-label="Paginación">
        <button type="button" class="btn btn-ghost btn-sm" [disabled]="pagina() <= 1" (click)="ir(pagina() - 1)">
          <app-icon name="chevron-left" [size]="16" /> Anterior
        </button>
        <span class="small muted num">Página {{ pagina() }} de {{ paginas() }}</span>
        <button type="button" class="btn btn-ghost btn-sm" [disabled]="pagina() >= paginas()" (click)="ir(pagina() + 1)">
          Siguiente <app-icon name="chevron-right" [size]="16" />
        </button>
      </nav>
    }
  `,
})
export class PaginacionComponent {
  readonly total = input.required<number>();
  readonly tamano = input(12);
  readonly pagina = model(1);

  protected readonly paginas = computed(() => Math.max(1, Math.ceil(this.total() / this.tamano())));

  protected ir(n: number): void {
    this.pagina.set(Math.min(Math.max(1, n), this.paginas()));
  }
}
