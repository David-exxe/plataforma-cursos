import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { mensajeDe } from '../../core/http/api-error';
import { Comprobante, Inscripcion } from '../../core/models';
import { ComprobantesService } from '../../core/services/comprobantes.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { InscripcionesService } from '../../core/services/inscripciones.service';
import { PagosService } from '../../core/services/pagos.service';
import { ToastService } from '../../core/services/toast.service';
import { tiempoRestante } from '../../core/util/fechas';
import { esUrlHttp } from '../../core/util/texto';
import { EstadoComponent } from '../../shared/estado.component';
import { IconComponent } from '../../shared/icon.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

type Filtro = 'todas' | 'pendientes' | 'pagadas' | 'otras';

@Component({
  selector: 'app-mis-inscripciones',
  imports: [RouterLink, IconComponent, EstadoComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <section class="container page">
      <header class="page-head">
        <h1>Mis inscripciones</h1>
        <p class="lead">Aquí pagas tus cupos reservados, entras a tus clases y descargas tus comprobantes.</p>
      </header>

      @if (cargando()) {
        <div class="stack" aria-busy="true">
          @for (n of [1, 2, 3]; track n) { <div class="skel" style="height:150px"></div> }
        </div>
      } @else if (error()) {
        <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar tus inscripciones" [texto]="error()!">
          <button type="button" class="btn btn-brand" (click)="cargar()"><app-icon name="refresh" [size]="16" /> Reintentar</button>
        </app-vacio>
      } @else if (lista().length === 0) {
        <app-vacio icono="ticket" titulo="Todavía no reservaste ningún cupo" texto="Elige un curso y reserva tu cupo en un minuto.">
          <a routerLink="/cursos" class="btn btn-brand">Ver cursos</a>
        </app-vacio>
      } @else {
        <div class="toolbar">
          <div class="seg" role="group" aria-label="Filtrar inscripciones">
            @for (f of filtros; track f.valor) {
              <button type="button" [class.on]="filtro() === f.valor" [attr.aria-pressed]="filtro() === f.valor" (click)="filtro.set(f.valor)">
                {{ f.texto }} <span class="seg-n">{{ conteo()[f.valor] }}</span>
              </button>
            }
          </div>
        </div>

        <div class="stack">
          @for (i of visibles(); track i.id) {
            <article class="insc card" [class.nueva]="i.id === nuevaId">
              <div class="insc-top">
                <h2 class="insc-titulo"><a [routerLink]="['/cursos', i.cursoId]">{{ i.curso }}</a></h2>
                <app-estado tipo="inscripcion" [valor]="i.estado" />
              </div>
              <ul class="insc-meta">
                <li><app-icon name="calendar" [size]="16" /> {{ i.fechaInicio | fecha: 'larga' }}</li>
                <li><app-icon name="clock" [size]="16" /> {{ i.horario || 'Horario por confirmar' }}</li>
                <li class="num"><app-icon name="dollar" [size]="16" /> {{ i.monto | money: i.moneda }}</li>
              </ul>

              @switch (i.estado) {
                @case ('PENDIENTE_PAGO') {
                  <div class="insc-accion">
                    @if (i.id === nuevaId) { <p class="insc-aviso"><strong>¡Cupo reservado!</strong> Paga para confirmarlo.</p> }
                    @if (reserva(i); as r) {
                      <p class="small"><app-icon name="clock" [size]="15" /> Tu cupo está reservado por <strong>{{ r }}</strong> más.</p>
                    } @else {
                      <p class="small muted">El plazo de reserva terminó; el cupo se liberará en cualquier momento.</p>
                    }
                    <div class="row">
                      <button type="button" class="btn btn-primary" [disabled]="trabajando() === i.id" (click)="pagar(i)">
                        @if (trabajando() === i.id) { <span class="spinner"></span> }
                        {{ i.linkPago ? 'Pagar ahora' : 'Generar link de pago' }}
                      </button>
                      <button type="button" class="btn btn-ghost" [disabled]="trabajando() === i.id" (click)="cancelar(i)">Cancelar inscripción</button>
                    </div>
                    @if (!i.linkPago) {
                      <p class="small muted">No pudimos generar el link al reservar. Pulsa el botón para intentarlo de nuevo.</p>
                    }
                  </div>
                }
                @case ('PAGADA') {
                  <div class="insc-accion">
                    <div class="insc-clase">
                      <span class="label">{{ i.modalidad === 'ONLINE' ? 'Tu clase online' : 'Tu aula' }}</span>
                      @if (esUrl(i.ubicacion)) {
                        <a class="btn btn-brand btn-sm" [href]="i.ubicacion" target="_blank" rel="noopener noreferrer">
                          <app-icon name="video" [size]="16" /> Entrar a la clase
                        </a>
                      } @else {
                        <strong>{{ i.ubicacion || 'Se confirmará antes del inicio' }}</strong>
                      }
                    </div>
                    @if (comprobanteDe(i); as c) {
                      <button type="button" class="btn btn-ghost btn-sm" [disabled]="descargando() === c.id" (click)="descargar(c)">
                        <app-icon name="download" [size]="16" /> Comprobante {{ c.numero }}
                      </button>
                    } @else {
                      <p class="small muted">Tu comprobante se está generando y llegará a tu correo.</p>
                    }
                  </div>
                }
                @default {
                  <div class="insc-accion">
                    <p class="small muted">{{ i.estado === 'VENCIDA' ? 'Venció por falta de pago y se liberó el cupo.' : 'Cancelaste esta inscripción.' }}</p>
                    <a [routerLink]="['/cursos', i.cursoId]" class="btn btn-ghost btn-sm">Volver a inscribirme</a>
                  </div>
                }
              }
            </article>
          } @empty {
            <app-vacio icono="search" titulo="No hay inscripciones en esta vista" />
          }
        </div>
      }
    </section>
  `,
})
export class MisInscripcionesComponent {
  private readonly inscripciones = inject(InscripcionesService);
  private readonly comprobantes = inject(ComprobantesService);
  private readonly pagos = inject(PagosService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly route = inject(ActivatedRoute);

  protected readonly nuevaId = Number(this.route.snapshot.queryParamMap.get('nueva')) || null;
  protected readonly lista = signal<Inscripcion[]>([]);
  protected readonly comps = signal<Comprobante[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly filtro = signal<Filtro>('todas');
  protected readonly trabajando = signal<number | null>(null);
  protected readonly descargando = signal<number | null>(null);
  private readonly ahora = signal(Date.now());

  protected readonly filtros: { valor: Filtro; texto: string }[] = [
    { valor: 'todas', texto: 'Todas' },
    { valor: 'pendientes', texto: 'Por pagar' },
    { valor: 'pagadas', texto: 'Pagadas' },
    { valor: 'otras', texto: 'Vencidas o canceladas' },
  ];

  protected readonly conteo = computed(() => {
    const l = this.lista();
    return {
      todas: l.length,
      pendientes: l.filter((i) => i.estado === 'PENDIENTE_PAGO').length,
      pagadas: l.filter((i) => i.estado === 'PAGADA').length,
      otras: l.filter((i) => i.estado === 'VENCIDA' || i.estado === 'CANCELADA').length,
    };
  });

  protected readonly visibles = computed(() => {
    const f = this.filtro();
    return this.lista().filter((i) =>
      f === 'todas' ? true
        : f === 'pendientes' ? i.estado === 'PENDIENTE_PAGO'
        : f === 'pagadas' ? i.estado === 'PAGADA'
        : i.estado === 'VENCIDA' || i.estado === 'CANCELADA');
  });

  constructor() {
    this.cargar();
    // Refresca la cuenta regresiva de la reserva cada minuto
    const reloj = setInterval(() => this.ahora.set(Date.now()), 60_000);
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    forkJoin({
      ins: this.inscripciones.mias(),
      // Los comprobantes son un complemento: si ese servicio falla, la pantalla sigue funcionando
      comps: this.comprobantes.mios().pipe(catchError(() => of([] as Comprobante[]))),
    }).subscribe({
      next: ({ ins, comps }) => {
        this.lista.set([...ins].sort((a, b) => b.creadoEn.localeCompare(a.creadoEn)));
        this.comps.set(comps);
        this.cargando.set(false);
      },
      error: (e: unknown) => {
        this.error.set(mensajeDe(e));
        this.cargando.set(false);
      },
    });
  }

  protected esUrl(v: string | null): boolean {
    return esUrlHttp(v);
  }

  protected reserva(i: Inscripcion): string | null {
    return tiempoRestante(new Date(i.creadoEn).getTime() + environment.horasReserva * 3_600_000, this.ahora());
  }

  protected comprobanteDe(i: Inscripcion): Comprobante | null {
    return this.comps().find((c) => c.inscripcionId === i.id) ?? null;
  }

  protected pagar(i: Inscripcion): void {
    if (i.linkPago) {
      this.pagos.abrirLink(i.linkPago);
      return;
    }
    this.trabajando.set(i.id);
    this.inscripciones.regenerarLink(i.id).subscribe({
      next: (n) => {
        this.reemplazar(n);
        this.trabajando.set(null);
        if (n.linkPago) this.pagos.abrirLink(n.linkPago);
      },
      error: (e: unknown) => {
        this.trabajando.set(null);
        this.toast.error(mensajeDe(e));
      },
    });
  }

  protected async cancelar(i: Inscripcion): Promise<void> {
    const ok = await this.confirm.preguntar({
      titulo: 'Cancelar inscripción',
      mensaje: `Se liberará tu cupo en «${i.curso}». Podrás volver a inscribirte mientras queden cupos.`,
      confirmar: 'Cancelar inscripción',
      cancelar: 'Mantenerla',
      peligro: true,
    });
    if (!ok) return;
    this.trabajando.set(i.id);
    this.inscripciones.cancelar(i.id).subscribe({
      next: (n) => {
        this.reemplazar(n);
        this.trabajando.set(null);
        this.toast.exito('Inscripción cancelada.');
      },
      error: (e: unknown) => {
        this.trabajando.set(null);
        this.toast.error(mensajeDe(e));
      },
    });
  }

  protected descargar(c: Comprobante): void {
    this.descargando.set(c.id);
    this.comprobantes.descargar(c).subscribe({
      next: () => this.descargando.set(null),
      error: (e: unknown) => {
        this.descargando.set(null);
        this.toast.error(mensajeDe(e));
      },
    });
  }

  private reemplazar(n: Inscripcion): void {
    this.lista.update((l) => l.map((x) => (x.id === n.id ? n : x)));
  }
}
