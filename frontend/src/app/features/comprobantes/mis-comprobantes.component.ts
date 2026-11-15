import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { mensajeDe } from '../../core/http/api-error';
import { Comprobante } from '../../core/models';
import { ComprobantesService } from '../../core/services/comprobantes.service';
import { ToastService } from '../../core/services/toast.service';
import { EstadoComponent } from '../../shared/estado.component';
import { IconComponent } from '../../shared/icon.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

@Component({
  selector: 'app-mis-comprobantes',
  imports: [RouterLink, IconComponent, EstadoComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <section class="container page">
      <header class="page-head">
        <h1>Mis comprobantes</h1>
        <p class="lead">Descárgalos aquí aunque el correo no haya llegado. Este documento es un comprobante de control interno.</p>
      </header>

      @if (cargando()) {
        <div class="stack" aria-busy="true">@for (n of [1, 2]; track n) { <div class="skel" style="height:110px"></div> }</div>
      } @else if (error()) {
        <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar tus comprobantes" [texto]="error()!">
          <button type="button" class="btn btn-brand" (click)="cargar()"><app-icon name="refresh" [size]="16" /> Reintentar</button>
        </app-vacio>
      } @else if (lista().length === 0) {
        <app-vacio icono="file-text" titulo="Aún no tienes comprobantes" texto="Se generan automáticamente cuando se confirma un pago.">
          <a routerLink="/mis-inscripciones" class="btn btn-brand">Ver mis inscripciones</a>
        </app-vacio>
      } @else {
        <div class="stack">
          @for (c of lista(); track c.id) {
            <article class="comp card">
              <div class="comp-main">
                <p class="comp-numero mono">{{ c.numero }}</p>
                <h2 class="comp-curso">{{ c.curso }}</h2>
                <p class="small muted">Emitido el {{ c.fechaEmision | fecha: 'hora' }} · {{ c.email }}</p>
              </div>
              <div class="comp-monto num">{{ c.monto | money: c.moneda }}</div>
              <div class="comp-estado">
                <span class="label">Envío al correo</span>
                <app-estado tipo="envio" [valor]="c.estadoEnvio" />
              </div>
              <button type="button" class="btn btn-brand" [disabled]="descargando() === c.id" (click)="descargar(c)">
                @if (descargando() === c.id) { <span class="spinner"></span> } @else { <app-icon name="download" [size]="16" /> }
                Descargar PDF
              </button>
              @if (c.estadoEnvio === 'ERROR' || c.estadoEnvio === 'FALLIDO') {
                <p class="comp-aviso small"><app-icon name="info" [size]="15" />
                  No pudimos enviarlo a tu correo{{ c.estadoEnvio === 'FALLIDO' ? ', pero nuestro equipo ya fue avisado' : ' por ahora; seguimos intentando' }}. Mientras tanto, descárgalo aquí.</p>
              }
            </article>
          }
        </div>
      }
    </section>
  `,
})
export class MisComprobantesComponent {
  private readonly api = inject(ComprobantesService);
  private readonly toast = inject(ToastService);

  protected readonly lista = signal<Comprobante[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly descargando = signal<number | null>(null);

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.mios().subscribe({
      next: (l) => {
        this.lista.set(l);
        this.cargando.set(false);
      },
      error: (e: unknown) => {
        this.error.set(mensajeDe(e));
        this.cargando.set(false);
      },
    });
  }

  protected descargar(c: Comprobante): void {
    this.descargando.set(c.id);
    this.api.descargar(c).subscribe({
      next: () => this.descargando.set(null),
      error: (e: unknown) => {
        this.descargando.set(null);
        this.toast.error(mensajeDe(e));
      },
    });
  }
}
