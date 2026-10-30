import { Component, input } from '@angular/core';
import { IconComponent } from './icon.component';

/** Estado vacío / error con acción opcional (contenido proyectado). */
@Component({
  selector: 'app-vacio',
  imports: [IconComponent],
  template: `
    <div class="vacio" [class.vacio-error]="tono() === 'error'" [attr.role]="tono() === 'error' ? 'alert' : null">
      <app-icon [name]="icono()" [size]="30" />
      <h3>{{ titulo() }}</h3>
      @if (texto()) { <p class="muted">{{ texto() }}</p> }
      <div class="row row-center"><ng-content /></div>
    </div>
  `,
})
export class VacioComponent {
  readonly icono = input('info');
  readonly titulo = input.required<string>();
  readonly texto = input('');
  readonly tono = input<'neutro' | 'error'>('neutro');
}
