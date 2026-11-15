import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { mensajeDe } from '../../core/http/api-error';
import { Inscripcion } from '../../core/models';
import { InscripcionesService } from '../../core/services/inscripciones.service';
import { PagosService } from '../../core/services/pagos.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/icon.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

/**
 * Checkout de prueba (solo con MP_MODO=mock): reemplaza al checkout de Mercado Pago.
 * Llama a POST /api/pagos/simulacion/{id}?resultado=approved|rejected, que recorre el mismo
 * código que un webhook real.
 */
@Component({
  selector: 'app-pago-simulado',
  imports: [RouterLink, IconComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <section class="container page page-estrecha">
      @if (!idValido) {
        <app-vacio tono="error" icono="alert-circle" titulo="Falta la inscripción" texto="El enlace no indica qué inscripción pagar.">
          <a routerLink="/mis-inscripciones" class="btn btn-brand">Ir a mis inscripciones</a>
        </app-vacio>
      } @else if (cargando()) {
        <div class="skel" style="height:320px" aria-busy="true"></div>
      } @else if (error()) {
        <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar la inscripción" [texto]="error()!">
          <a routerLink="/mis-inscripciones" class="btn btn-brand">Ir a mis inscripciones</a>
        </app-vacio>
      } @else if (ins(); as i) {
        <div class="checkout card">
          <div class="alert alert-warn" role="note">
            <app-icon name="alert-triangle" [size]="18" />
            <div><strong>Modo de pruebas.</strong> Aquí no se cobra nada. Con Mercado Pago real verás su checkout en lugar de esta pantalla.</div>
          </div>

          <h1>Pago de prueba</h1>
          <dl class="resumen">
            <div><dt>Curso</dt><dd>{{ i.curso }}</dd></div>
            <div><dt>Inicio</dt><dd>{{ i.fechaInicio | fecha: 'larga' }}</dd></div>
            <div class="resumen-total"><dt>Total a pagar</dt><dd class="num">{{ i.monto | money: i.moneda }}</dd></div>
          </dl>

          @if (i.estado !== 'PENDIENTE_PAGO') {
            <div class="alert alert-info"><app-icon name="info" [size]="18" /><div>Esta inscripción ya no está pendiente de pago.</div></div>
            <a routerLink="/mis-inscripciones" class="btn btn-brand btn-block">Ver mis inscripciones</a>
          } @else {
            <div class="stack">
              <button type="button" class="btn btn-primary btn-block" [disabled]="enviando() !== null" (click)="simular('approved')">
                @if (enviando() === 'approved') { <span class="spinner"></span> } Aprobar pago
              </button>
              <button type="button" class="btn btn-ghost btn-block" [disabled]="enviando() !== null" (click)="simular('rejected')">
                @if (enviando() === 'rejected') { <span class="spinner"></span> } Rechazar pago
              </button>
            </div>
          }
        </div>
      }
    </section>
  `,
})
export class PagoSimuladoComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly inscripciones = inject(InscripcionesService);
  private readonly pagos = inject(PagosService);
  private readonly toast = inject(ToastService);

  protected readonly id = Number(this.route.snapshot.queryParamMap.get('inscripcion'));
  protected readonly idValido = Number.isInteger(this.id) && this.id > 0;
  protected readonly ins = signal<Inscripcion | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal<'approved' | 'rejected' | null>(null);

  constructor() {
    if (!this.idValido) return;
    this.inscripciones.detalle(this.id).subscribe({
      next: (i) => {
        this.ins.set(i);
        this.cargando.set(false);
      },
      error: (e: unknown) => {
        this.error.set(mensajeDe(e));
        this.cargando.set(false);
      },
    });
  }

  protected simular(resultado: 'approved' | 'rejected'): void {
    this.enviando.set(resultado);
    this.pagos.simular(this.id, resultado).subscribe({
      next: () =>
        void this.router.navigate(['/pago/resultado'], {
          queryParams: { estado: resultado === 'approved' ? 'aprobado' : 'rechazado', inscripcion: this.id },
        }),
      error: (e: unknown) => {
        this.enviando.set(null);
        this.toast.error(mensajeDe(e));
      },
    });
  }
}
