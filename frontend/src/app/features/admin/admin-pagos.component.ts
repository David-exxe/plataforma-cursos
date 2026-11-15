import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { mensajeDe } from '../../core/http/api-error';
import { Pago } from '../../core/models';
import { PagosService } from '../../core/services/pagos.service';
import { normalizar } from '../../core/util/texto';
import { EstadoComponent } from '../../shared/estado.component';
import { IconComponent } from '../../shared/icon.component';
import { PaginacionComponent } from '../../shared/paginacion.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

const TAM = 12;
const OPCIONES = [
  { valor: '', texto: 'Todos los estados' },
  { valor: 'PENDIENTE', texto: 'Pendiente' },
  { valor: 'EN_PROCESO', texto: 'En proceso' },
  { valor: 'APROBADO', texto: 'Aprobado' },
  { valor: 'RECHAZADO', texto: 'Rechazado' },
  { valor: 'REEMBOLSADO', texto: 'Reembolsado' },
];

@Component({
  selector: 'app-admin-pagos',
  imports: [IconComponent, EstadoComponent, PaginacionComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <header class="page-head"><h1>Pagos</h1><p class="muted">Cobros registrados por Mercado Pago. Un pago aprobado nunca retrocede; solo puede reembolsarse.</p></header>

    <div class="toolbar">
      <label class="search">
        <app-icon name="search" [size]="18" />
        <input type="search" placeholder="Buscar por curso, N.º de inscripción o ID de Mercado Pago" [value]="q()" (input)="buscar($any($event.target).value)" aria-label="Buscar pagos" />
      </label>
      <select (change)="cambiarEstado($any($event.target).value)" aria-label="Filtrar por estado">
        @for (o of opciones; track o.valor) { <option [value]="o.valor" [selected]="o.valor === estado()">{{ o.texto }}</option> }
      </select>
      <span class="spacer"></span>
      <button type="button" class="btn btn-ghost btn-sm" [disabled]="cargando()" (click)="cargar()"><app-icon name="refresh" [size]="16" /> Actualizar</button>
    </div>

    @if (cargando()) {
      <div class="skel" style="height:320px" aria-busy="true"></div>
    } @else if (error()) {
      <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar los pagos" [texto]="error()!">
        <button type="button" class="btn btn-brand" (click)="cargar()">Reintentar</button>
      </app-vacio>
    } @else if (filtrados().length === 0) {
      <app-vacio icono="credit-card" titulo="No hay pagos con estos filtros" />
    } @else {
      <p class="small muted conteo" aria-live="polite">{{ filtrados().length }} {{ filtrados().length === 1 ? 'pago' : 'pagos' }}</p>
      <div class="table-wrap">
        <table class="tbl">
          <thead><tr><th>Inscripción</th><th>Curso</th><th class="num">Monto</th><th>Estado</th><th>Medio</th><th>ID Mercado Pago</th><th>Creado</th><th>Aprobado</th></tr></thead>
          <tbody>
            @for (p of visibles(); track p.pagoId) {
              <tr>
                <td class="mono">#{{ p.inscripcionId }}</td>
                <td>{{ p.curso }}</td>
                <td class="num">{{ p.monto | money: p.moneda }}</td>
                <td><app-estado tipo="pago" [valor]="p.estado" />@if (p.estadoDetalle) { <span class="cell-sub">{{ p.estadoDetalle }}</span> }</td>
                <td>{{ p.metodoPago || '—' }}</td>
                <td class="mono">{{ p.mpPaymentId || '—' }}</td>
                <td>{{ p.creadoEn | fecha: 'hora' }}</td>
                <td>{{ p.fechaAprobacion | fecha: 'hora' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-paginacion [total]="filtrados().length" [tamano]="tam" [(pagina)]="pagina" />
    }
  `,
})
export class AdminPagosComponent {
  private readonly api = inject(PagosService);
  private readonly route = inject(ActivatedRoute);

  protected readonly tam = TAM;
  protected readonly opciones = OPCIONES;
  protected readonly lista = signal<Pago[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly q = signal('');
  protected readonly pagina = signal(1);
  protected readonly estado = signal(this.estadoInicial());

  protected readonly filtrados = computed(() => {
    const q = normalizar(this.q());
    if (!q) return this.lista();
    return this.lista().filter((p) => normalizar(`${p.curso} ${p.inscripcionId} ${p.mpPaymentId ?? ''} ${p.metodoPago ?? ''}`).includes(q));
  });
  protected readonly visibles = computed(() => this.filtrados().slice((this.pagina() - 1) * TAM, this.pagina() * TAM));

  constructor() {
    this.cargar();
  }

  private estadoInicial(): string {
    const e = this.route.snapshot.queryParamMap.get('estado') ?? '';
    return OPCIONES.some((o) => o.valor === e) ? e : '';
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.admin(this.estado()).subscribe({
      next: (l) => {
        this.lista.set(l);
        this.pagina.set(1);
        this.cargando.set(false);
      },
      error: (e: unknown) => {
        this.error.set(mensajeDe(e));
        this.cargando.set(false);
      },
    });
  }

  protected cambiarEstado(v: string): void {
    this.estado.set(v);
    this.cargar();
  }

  protected buscar(v: string): void {
    this.q.set(v);
    this.pagina.set(1);
  }
}
