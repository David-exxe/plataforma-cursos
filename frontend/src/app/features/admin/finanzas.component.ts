import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, catchError, forkJoin, of } from 'rxjs';
import { Inscripcion, Pago } from '../../core/models';
import { InscripcionesService } from '../../core/services/inscripciones.service';
import { PagosService } from '../../core/services/pagos.service';
import { guardarBlob } from '../../core/util/descargas';
import { portadaUrl } from '../../core/util/portadas';
import { EstadoComponent } from '../../shared/estado.component';
import { IconComponent } from '../../shared/icon.component';
import { VacioComponent } from '../../shared/vacio.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';

type Periodo = '7' | '30' | '90' | 'todo';

interface Barra { clave: string; etiqueta: string; total: number; pagos: number }

const ZONA = 'America/Lima';
const DIA = new Intl.DateTimeFormat('en-CA', { timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit' });
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** 'YYYY-MM-DD' en hora de Lima. */
function diaLima(iso: string | Date): string {
  return DIA.format(typeof iso === 'string' ? new Date(iso) : iso);
}

/** Fecha en que el dinero entró (o se intentó cobrar). */
function fechaDe(p: Pago): string {
  return p.fechaAprobacion ?? p.creadoEn;
}

const METODOS: Record<string, string> = {
  account_money: 'Dinero en cuenta Mercado Pago',
  credit_card: 'Tarjeta de crédito',
  debit_card: 'Tarjeta de débito',
  prepaid_card: 'Tarjeta prepago',
  ticket: 'Pago en efectivo',
  bank_transfer: 'Transferencia bancaria',
  visa: 'Visa',
  master: 'Mastercard',
  amex: 'American Express',
  diners: 'Diners Club',
  yape: 'Yape',
  pagoefectivo_atm: 'PagoEfectivo',
};

function nombreMetodo(m: string | null): string {
  if (!m) return 'Sin dato';
  return METODOS[m.toLowerCase()] ?? m.replace(/_/g, ' ').replace(/^./, (x) => x.toUpperCase());
}

function csv(v: string | number | null | undefined): string {
  const s = v === null || v === undefined ? '' : String(v);
  return /[",;\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/**
 * Finanzas: se calcula todo en el navegador con lo que ya exponen pagos-service
 * (/api/admin/pagos) y matricula-service (/api/admin/inscripciones). No hay endpoints nuevos.
 * Si un servicio no responde, la parte que depende de él se muestra como no disponible.
 */
@Component({
  selector: 'app-finanzas',
  imports: [RouterLink, IconComponent, EstadoComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <header class="page-head row">
      <div><h1>Finanzas</h1><p class="muted">Ingresos, cobros pendientes y medios de pago, calculados con los pagos registrados.</p></div>
      <span class="spacer"></span>
      <button type="button" class="btn btn-ghost btn-sm" [disabled]="cargando()" (click)="cargar()">
        <app-icon name="refresh" [size]="16" /> Actualizar
      </button>
      <button type="button" class="btn btn-primary btn-sm" [disabled]="!filtrados().length" (click)="exportar()">
        <app-icon name="download" [size]="16" /> Exportar CSV
      </button>
    </header>

    <div class="toolbar">
      <div class="seg" role="group" aria-label="Periodo">
        @for (op of periodos; track op.valor) {
          <button type="button" [class.on]="periodo() === op.valor" [attr.aria-pressed]="periodo() === op.valor" (click)="periodo.set(op.valor)">{{ op.texto }}</button>
        }
      </div>
      @if (monedas().length > 1) {
        <div class="seg" role="group" aria-label="Moneda">
          @for (m of monedas(); track m) {
            <button type="button" [class.on]="moneda() === m" [attr.aria-pressed]="moneda() === m" (click)="moneda.set(m)">{{ m }}</button>
          }
        </div>
      }
    </div>

    @if (cargando() && !pagos()) {
      <div class="stack" aria-busy="true"><div class="skel" style="height:120px"></div><div class="skel" style="height:280px"></div></div>
    } @else if (!pagos()) {
      <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar los pagos"
                 texto="El servicio de pagos no respondió. Si estuvo inactivo, puede tardar hasta un minuto en despertar.">
        <button type="button" class="btn btn-brand" (click)="cargar()"><app-icon name="refresh" [size]="16" /> Reintentar</button>
      </app-vacio>
    } @else {
      <dl class="kpis fin-kpis">
        <div class="kpi kpi-destacado">
          <dt>Ingresos confirmados</dt>
          <dd class="num">{{ kpis().ingresos | money: moneda() }}</dd>
          <span class="kpi-sub">{{ kpis().aprobados }} {{ kpis().aprobados === 1 ? 'pago aprobado' : 'pagos aprobados' }}</span>
        </div>
        <div class="kpi">
          <dt>Ticket promedio</dt>
          <dd class="num">{{ kpis().promedio | money: moneda() }}</dd>
          <span class="kpi-sub">por matrícula pagada</span>
        </div>
        <div class="kpi">
          <dt>Por cobrar</dt>
          <dd class="num">
            @if (porCobrar(); as pc) { {{ pc.monto | money: moneda() }} } @else { <span class="muted">No disponible</span> }
          </dd>
          <span class="kpi-sub">
            @if (porCobrar(); as pc) { {{ pc.cupos }} {{ pc.cupos === 1 ? 'cupo reservado' : 'cupos reservados' }} sin pagar } @else { matrícula no respondió }
          </span>
        </div>
        <div class="kpi">
          <dt>Conversión</dt>
          <dd class="num">
            @if (conversion(); as cv) { {{ cv.pct }}% } @else { <span class="muted">—</span> }
          </dd>
          <span class="kpi-sub">
            @if (conversion(); as cv) { {{ cv.pagadas }} de {{ cv.total }} reservas terminaron en pago } @else { sin inscripciones en el periodo }
          </span>
        </div>
      </dl>

      <section class="card" aria-labelledby="h-serie">
        <div class="card-head">
          <h2 id="h-serie">{{ porMes() ? 'Ingresos por mes' : 'Ingresos por día' }}</h2>
          <span class="small muted">{{ etiquetaPeriodo() }}</span>
        </div>
        <div class="card-body">
          @if (kpis().aprobados) {
            <div class="grafico" role="img" [attr.aria-label]="'Gráfico de ingresos: total ' + (kpis().ingresos | money: moneda())">
              @for (b of serie(); track b.clave; let i = $index) {
                <div class="grafico-col" [title]="b.etiqueta + ': ' + (b.total | money: moneda()) + ' · ' + b.pagos + ' pagos'">
                  <span class="grafico-valor">{{ b.total ? (b.total | money: moneda()) : '' }}</span>
                  <span class="grafico-barra" [class.vacia]="!b.total" [style.height.%]="altura(b.total)"></span>
                  <span class="grafico-eje">{{ mostrarEtiqueta(i) ? b.etiqueta : '' }}</span>
                </div>
              }
            </div>
          } @else {
            <p class="muted">Todavía no hay pagos aprobados en este periodo.</p>
          }
        </div>
      </section>

      <div class="dos-col fin-dos">
        <section class="card" aria-labelledby="h-cursos">
          <div class="card-head"><h2 id="h-cursos">Ingresos por curso</h2><a routerLink="/admin/cursos" class="small">Ver cursos</a></div>
          <div class="card-body">
            <ul class="ranking">
              @for (c of porCurso(); track c.curso) {
                <li>
                  <img class="mini-portada" [src]="portada(c.curso)" alt="" loading="lazy" />
                  <div class="ranking-info">
                    <div class="ranking-cab"><strong>{{ c.curso }}</strong><span class="num">{{ c.total | money: moneda() }}</span></div>
                    <div class="mini-barra"><span [style.width.%]="c.pct"></span></div>
                    <span class="small muted">{{ c.pagos }} {{ c.pagos === 1 ? 'matrícula pagada' : 'matrículas pagadas' }}</span>
                  </div>
                </li>
              } @empty { <li class="muted">Sin ventas en este periodo.</li> }
            </ul>
          </div>
        </section>

        <section class="card" aria-labelledby="h-metodos">
          <div class="card-head"><h2 id="h-metodos">Medios de pago</h2></div>
          <div class="card-body">
            <ul class="metodos">
              @for (m of metodos(); track m.nombre) {
                <li>
                  <div class="ranking-cab"><span>{{ m.nombre }}</span><span class="num">{{ m.pct }}%</span></div>
                  <div class="mini-barra barra-sol"><span [style.width.%]="m.pct"></span></div>
                </li>
              } @empty { <li class="muted">Sin pagos aprobados.</li> }
            </ul>

            <h3 class="fin-sub">Estado de los cobros</h3>
            <ul class="estados-pago">
              @for (e of estados(); track e.estado) {
                <li><app-estado tipo="pago" [valor]="e.estado" /><span class="num">{{ e.cantidad }}</span><span class="num muted">{{ e.monto | money: moneda() }}</span></li>
              }
            </ul>
          </div>
        </section>
      </div>

      <section class="card" aria-labelledby="h-mov">
        <div class="card-head">
          <h2 id="h-mov">Movimientos recientes</h2>
          <a routerLink="/admin/pagos" class="small">Ver todos los pagos</a>
        </div>
        <div class="table-wrap">
          <table class="tbl">
            <thead><tr><th>Fecha</th><th>Curso</th><th>Medio de pago</th><th>Estado</th><th class="num">Monto</th></tr></thead>
            <tbody>
              @for (p of recientes(); track p.pagoId) {
                <tr>
                  <td>{{ fecha(p) | fecha: 'hora' }}</td>
                  <td><strong>{{ p.curso }}</strong><span class="cell-sub">Inscripción #{{ p.inscripcionId }}</span></td>
                  <td>{{ metodo(p.metodoPago) }}</td>
                  <td><app-estado tipo="pago" [valor]="p.estado" /></td>
                  <td class="num" [class.monto-neg]="p.estado === 'REEMBOLSADO'">{{ p.monto | money: p.moneda }}</td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="muted">No hay movimientos en este periodo.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
})
export class FinanzasComponent {
  private readonly pagosApi = inject(PagosService);
  private readonly inscripcionesApi = inject(InscripcionesService);

  protected readonly pagos = signal<Pago[] | null>(null);
  protected readonly inscripciones = signal<Inscripcion[] | null>(null);
  protected readonly cargando = signal(false);
  protected readonly periodo = signal<Periodo>('30');
  protected readonly moneda = signal<string>('PEN');

  protected readonly periodos: { valor: Periodo; texto: string }[] = [
    { valor: '7', texto: '7 días' },
    { valor: '30', texto: '30 días' },
    { valor: '90', texto: '90 días' },
    { valor: 'todo', texto: 'Todo' },
  ];

  protected readonly monedas = computed(() => {
    const s = new Set((this.pagos() ?? []).map((p) => p.moneda));
    s.add('PEN');
    return [...s].sort();
  });

  /** Primer día incluido ('YYYY-MM-DD') o null si es "Todo". */
  private readonly desde = computed(() => {
    const p = this.periodo();
    if (p === 'todo') return null;
    const d = new Date();
    d.setDate(d.getDate() - (Number(p) - 1));
    return diaLima(d);
  });

  protected readonly porMes = computed(() => this.periodo() === '90' || this.periodo() === 'todo');

  protected readonly etiquetaPeriodo = computed(() =>
    this.periodo() === 'todo' ? 'Todo el historial' : `Últimos ${this.periodo()} días`);

  /** Pagos del periodo en la moneda elegida (todos los estados). */
  protected readonly filtrados = computed(() => {
    const desde = this.desde();
    const m = this.moneda();
    return (this.pagos() ?? []).filter((p) => p.moneda === m && (!desde || diaLima(fechaDe(p)) >= desde));
  });

  private readonly aprobados = computed(() => this.filtrados().filter((p) => p.estado === 'APROBADO'));

  protected readonly kpis = computed(() => {
    const a = this.aprobados();
    const ingresos = a.reduce((s, p) => s + p.monto, 0);
    return { ingresos, aprobados: a.length, promedio: a.length ? ingresos / a.length : 0 };
  });

  protected readonly porCobrar = computed(() => {
    const l = this.inscripciones();
    if (!l) return null;
    const pend = l.filter((i) => i.estado === 'PENDIENTE_PAGO' && i.moneda === this.moneda());
    return { cupos: pend.length, monto: pend.reduce((s, i) => s + i.monto, 0) };
  });

  protected readonly conversion = computed(() => {
    const l = this.inscripciones();
    if (!l) return null;
    const desde = this.desde();
    const enPeriodo = l.filter((i) => i.estado !== 'CANCELADA' && (!desde || diaLima(i.creadoEn) >= desde));
    if (!enPeriodo.length) return null;
    const pagadas = enPeriodo.filter((i) => i.estado === 'PAGADA').length;
    return { total: enPeriodo.length, pagadas, pct: Math.round((pagadas / enPeriodo.length) * 100) };
  });

  protected readonly serie = computed<Barra[]>(() => {
    const barras = new Map<string, Barra>();
    const hoy = new Date();
    if (this.porMes()) {
      // últimos 3 meses ("90 días") o hasta 12 meses con datos ("Todo")
      let n = 3;
      if (this.periodo() === 'todo') {
        const primero = this.aprobados().map((p) => diaLima(fechaDe(p))).sort()[0];
        if (primero) {
          const [y, m] = primero.split('-').map(Number);
          const [hy, hm] = diaLima(hoy).split('-').map(Number);
          n = Math.min(12, Math.max(3, (hy - y) * 12 + (hm - m) + 1));
        }
      }
      const [hy, hm] = diaLima(hoy).split('-').map(Number);
      for (let k = n - 1; k >= 0; k--) {
        const idx = hy * 12 + (hm - 1) - k;
        const y = Math.floor(idx / 12), m = (idx % 12) + 1;
        const clave = `${y}-${String(m).padStart(2, '0')}`;
        barras.set(clave, { clave, etiqueta: `${MESES[m - 1]} ${String(y).slice(2)}`, total: 0, pagos: 0 });
      }
      for (const p of this.aprobados()) {
        const b = barras.get(diaLima(fechaDe(p)).slice(0, 7));
        if (b) { b.total += p.monto; b.pagos++; }
      }
    } else {
      const n = Number(this.periodo());
      for (let k = n - 1; k >= 0; k--) {
        const d = new Date(hoy);
        d.setDate(d.getDate() - k);
        const clave = diaLima(d);
        const [, m, dd] = clave.split('-').map(Number);
        barras.set(clave, { clave, etiqueta: `${dd} ${MESES[m - 1]}`, total: 0, pagos: 0 });
      }
      for (const p of this.aprobados()) {
        const b = barras.get(diaLima(fechaDe(p)));
        if (b) { b.total += p.monto; b.pagos++; }
      }
    }
    return [...barras.values()];
  });

  private readonly maximo = computed(() => Math.max(0, ...this.serie().map((b) => b.total)));

  protected readonly porCurso = computed(() => {
    const m = new Map<string, { curso: string; total: number; pagos: number }>();
    for (const p of this.aprobados()) {
      const e = m.get(p.curso) ?? { curso: p.curso, total: 0, pagos: 0 };
      e.total += p.monto;
      e.pagos++;
      m.set(p.curso, e);
    }
    const lista = [...m.values()].sort((a, b) => b.total - a.total);
    const max = lista[0]?.total || 1;
    return lista.slice(0, 6).map((e) => ({ ...e, pct: Math.max(3, Math.round((e.total / max) * 100)) }));
  });

  protected readonly metodos = computed(() => {
    const a = this.aprobados();
    const m = new Map<string, number>();
    for (const p of a) {
      const n = nombreMetodo(p.metodoPago);
      m.set(n, (m.get(n) ?? 0) + 1);
    }
    return [...m.entries()]
      .map(([nombre, n]) => ({ nombre, pct: Math.round((n / a.length) * 100) }))
      .sort((x, y) => y.pct - x.pct);
  });

  protected readonly estados = computed(() => {
    const orden = ['APROBADO', 'PENDIENTE', 'EN_PROCESO', 'RECHAZADO', 'REEMBOLSADO'];
    return orden
      .map((estado) => {
        const l = this.filtrados().filter((p) => p.estado === estado);
        return { estado, cantidad: l.length, monto: l.reduce((s, p) => s + p.monto, 0) };
      })
      .filter((e) => e.cantidad > 0);
  });

  protected readonly recientes = computed(() =>
    [...this.filtrados()].sort((a, b) => fechaDe(b).localeCompare(fechaDe(a))).slice(0, 10));

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    const o = <T>(obs: Observable<T>) => obs.pipe(catchError(() => of(null)));
    forkJoin({ pagos: o(this.pagosApi.admin()), ins: o(this.inscripcionesApi.admin()) }).subscribe((r) => {
      this.pagos.set(r.pagos);
      this.inscripciones.set(r.ins);
      this.cargando.set(false);
    });
  }

  protected altura(total: number): number {
    const max = this.maximo();
    return max > 0 ? Math.max(total > 0 ? 4 : 0, Math.round((total / max) * 100)) : 0;
  }

  /** En 30 días se muestra una etiqueta cada 5 barras para que el eje no se amontone. */
  protected mostrarEtiqueta(i: number): boolean {
    const n = this.serie().length;
    if (n <= 12) return true;
    return i % 5 === 0 || i === n - 1;
  }

  protected fecha(p: Pago): string {
    return fechaDe(p);
  }

  protected metodo(m: string | null): string {
    return nombreMetodo(m);
  }

  protected portada(curso: string): string {
    return portadaUrl({ nombre: curso });
  }

  protected exportar(): void {
    const filas = [['fecha', 'inscripcion', 'curso', 'estado', 'medio_pago', 'monto', 'moneda', 'id_mercado_pago']];
    for (const p of this.filtrados()) {
      filas.push([fechaDe(p), String(p.inscripcionId), p.curso, p.estado, nombreMetodo(p.metodoPago), p.monto.toFixed(2), p.moneda, p.mpPaymentId ?? '']);
    }
    const texto = '﻿' + filas.map((f) => f.map(csv).join(';')).join('\r\n');
    guardarBlob(new Blob([texto], { type: 'text/csv;charset=utf-8' }), `finanzas-${diaLima(new Date())}.csv`);
  }
}
