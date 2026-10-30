import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Curso } from '../core/models';
import { diasHasta, etiquetaInicio, partesTalon } from '../core/util/fechas';
import { CuposComponent } from './cupos.component';
import { IconComponent } from './icon.component';
import { MoneyPipe } from './pipes';

/**
 * Tarjeta de curso en forma de boleto: el talón lleva la fecha de inicio (lo que decide la compra)
 * y se vuelve amarillo cuando faltan 3 días o menos.
 */
@Component({
  selector: 'app-curso-ticket',
  imports: [RouterLink, IconComponent, CuposComponent, MoneyPipe],
  template: `
    <a class="ticket" [class.pronto]="pronto()" [class.agotado]="curso().cuposDisponibles <= 0"
       [class.destacado]="destacado()" [routerLink]="['/cursos', curso().id]">
      <div class="ticket-stub" aria-hidden="true">
        <span class="stub-semana">{{ talon().semana }}</span>
        <strong class="stub-dia">{{ talon().dia }}</strong>
        <span class="stub-mes">{{ talon().mes }}</span>
      </div>
      <div class="ticket-body">
        <div class="ticket-chips">
          <span class="chip">
            <app-icon [name]="curso().modalidad === 'ONLINE' ? 'video' : 'map-pin'" [size]="14" />
            {{ curso().modalidad === 'ONLINE' ? 'Online' : 'Presencial' }}
          </span>
          <span class="ticket-inicio">{{ etiqueta() }}</span>
        </div>
        <h3>{{ curso().nombre }}</h3>
        @if (curso().descripcion) { <p class="ticket-desc">{{ curso().descripcion }}</p> }
        <p class="ticket-meta"><app-icon name="clock" [size]="15" /> {{ curso().horario || 'Horario por confirmar' }}</p>
        <div class="ticket-foot">
          <span class="precio num">{{ curso().precio | money: curso().moneda }}</span>
          <app-cupos [total]="curso().cupos" [disponibles]="curso().cuposDisponibles" />
        </div>
      </div>
    </a>
  `,
})
export class CursoTicketComponent {
  readonly curso = input.required<Curso>();
  readonly destacado = input(false);

  protected readonly talon = computed(() => partesTalon(this.curso().fechaInicio));
  protected readonly etiqueta = computed(() => etiquetaInicio(this.curso().fechaInicio));
  protected readonly pronto = computed(() => diasHasta(this.curso().fechaInicio) <= 3);
}
