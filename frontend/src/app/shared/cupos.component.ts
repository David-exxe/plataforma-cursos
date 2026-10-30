import { Component, computed, input } from '@angular/core';

/** Medidor de cupos: la barra muestra cuántos están ocupados y cambia de color al agotarse. */
@Component({
  selector: 'app-cupos',
  template: `
    <div class="cupos" [attr.data-nivel]="nivel()">
      <div class="cupos-barra" role="img" [attr.aria-label]="texto()"><span [style.width.%]="ocupado()"></span></div>
      <span class="cupos-texto">{{ texto() }}</span>
    </div>
  `,
})
export class CuposComponent {
  readonly total = input.required<number>();
  readonly disponibles = input.required<number>();

  protected readonly ocupado = computed(() => {
    const t = this.total();
    return t > 0 ? Math.min(100, Math.max(0, Math.round(((t - this.disponibles()) / t) * 100))) : 100;
  });

  protected readonly nivel = computed(() => {
    const d = this.disponibles();
    if (d <= 0) return 'agotado';
    return d <= 5 || this.ocupado() >= 80 ? 'bajo' : 'ok';
  });

  protected readonly texto = computed(() => {
    const d = this.disponibles();
    if (d <= 0) return 'Cupos agotados';
    if (d === 1) return 'Queda 1 cupo';
    return this.nivel() === 'bajo' ? `Quedan ${d} cupos` : `${d} de ${this.total()} cupos libres`;
  });
}
