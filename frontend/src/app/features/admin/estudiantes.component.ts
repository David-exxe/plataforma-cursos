import { Component, computed, inject, signal } from '@angular/core';
import { Inscripcion } from '../../core/models';
import { InscripcionesService } from '../../core/services/inscripciones.service';
import { mensajeDe } from '../../core/http/api-error';
import { portadaUrl } from '../../core/util/portadas';
import { normalizar } from '../../core/util/texto';
import { EstadoComponent } from '../../shared/estado.component';
import { IconComponent } from '../../shared/icon.component';
import { VacioComponent } from '../../shared/vacio.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';

interface Estudiante {
  email: string;
  nombre: string;
  inscripciones: Inscripcion[];
  pagadas: number;
  pendientes: number;
  invertido: number;
  moneda: string;
  ultima: string;
}

type Orden = 'reciente' | 'invertido' | 'nombre';

/**
 * Estudiantes: vista agrupada de /api/admin/inscripciones (matricula-service).
 * Cada fila es una persona con todo lo que compró; no se usa ningún endpoint nuevo.
 */
@Component({
  selector: 'app-estudiantes',
  imports: [IconComponent, EstadoComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <header class="page-head row">
      <div><h1>Estudiantes</h1><p class="muted">Quién se inscribió, en qué cursos y cuánto ha pagado.</p></div>
      <span class="spacer"></span>
      <button type="button" class="btn btn-ghost btn-sm" [disabled]="cargando()" (click)="cargar()">
        <app-icon name="refresh" [size]="16" /> Actualizar
      </button>
    </header>

    @if (cargando() && !lista()) {
      <div class="stack" aria-busy="true"><div class="skel" style="height:90px"></div><div class="skel" style="height:320px"></div></div>
    } @else if (error()) {
      <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar los estudiantes" [texto]="error()!">
        <button type="button" class="btn btn-brand" (click)="cargar()"><app-icon name="refresh" [size]="16" /> Reintentar</button>
      </app-vacio>
    } @else {
      <dl class="kpis">
        <div class="kpi"><dt>Estudiantes</dt><dd class="num">{{ resumen().total }}</dd><span class="kpi-sub">con al menos una inscripción</span></div>
        <div class="kpi"><dt>Con matrícula pagada</dt><dd class="num">{{ resumen().conPago }}</dd><span class="kpi-sub">{{ resumen().pctPago }}% del total</span></div>
        <div class="kpi"><dt>Recurrentes</dt><dd class="num">{{ resumen().recurrentes }}</dd><span class="kpi-sub">pagaron 2 cursos o más</span></div>
      </dl>

      <div class="toolbar">
        <label class="search">
          <app-icon name="search" [size]="18" />
          <input type="search" placeholder="Buscar por nombre o correo" [value]="q()" (input)="q.set($any($event.target).value)" aria-label="Buscar estudiantes" />
        </label>
        <label class="select-inline">
          <span class="sr-only">Ordenar por</span>
          <select [value]="orden()" (change)="orden.set($any($event.target).value)" aria-label="Ordenar por">
            <option value="reciente">Actividad más reciente</option>
            <option value="invertido">Mayor monto pagado</option>
            <option value="nombre">Nombre (A-Z)</option>
          </select>
        </label>
      </div>

      @if (filtrados().length === 0) {
        <app-vacio icono="users" titulo="No hay estudiantes que mostrar" [texto]="q() ? 'Prueba con otro nombre o correo.' : 'Cuando alguien se inscriba aparecerá aquí.'" />
      } @else {
        <ul class="estudiantes">
          @for (e of filtrados(); track e.email) {
            <li class="card estudiante" [class.abierto]="abierto() === e.email">
              <button type="button" class="estudiante-cab" (click)="alternar(e.email)" [attr.aria-expanded]="abierto() === e.email">
                <span class="avatar-ini" aria-hidden="true">{{ iniciales(e.nombre) }}</span>
                <span class="estudiante-id"><strong>{{ e.nombre }}</strong><span class="cell-sub">{{ e.email }}</span></span>
                <span class="estudiante-dato oculto-movil"><span class="num">{{ e.inscripciones.length }}</span><span class="cell-sub">{{ e.inscripciones.length === 1 ? 'curso' : 'cursos' }}</span></span>
                <span class="estudiante-dato"><span class="num">{{ e.invertido | money: e.moneda }}</span><span class="cell-sub">pagado</span></span>
                <span class="estudiante-dato oculto-movil"><span>{{ e.ultima | fecha: 'hora' }}</span><span class="cell-sub">última actividad</span></span>
                <app-icon class="estudiante-flecha" name="chevron-right" [size]="18" />
              </button>
              @if (abierto() === e.email) {
                <ul class="estudiante-cursos">
                  @for (i of e.inscripciones; track i.id) {
                    <li>
                      <img class="mini-portada" [src]="portada(i)" alt="" loading="lazy" />
                      <span class="estudiante-id"><strong>{{ i.curso }}</strong><span class="cell-sub">Inscrito {{ i.creadoEn | fecha: 'corta' }} · inicia {{ i.fechaInicio | fecha: 'corta' }}</span></span>
                      <app-estado tipo="inscripcion" [valor]="i.estado" />
                      <span class="num">{{ i.monto | money: i.moneda }}</span>
                    </li>
                  }
                </ul>
              }
            </li>
          }
        </ul>
      }
    }
  `,
})
export class EstudiantesComponent {
  private readonly api = inject(InscripcionesService);

  protected readonly lista = signal<Inscripcion[] | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly q = signal('');
  protected readonly orden = signal<Orden>('reciente');
  protected readonly abierto = signal<string | null>(null);

  private readonly estudiantes = computed<Estudiante[]>(() => {
    const m = new Map<string, Estudiante>();
    for (const i of this.lista() ?? []) {
      const clave = (i.email ?? i.estudiante ?? `#${i.id}`).toLowerCase();
      let e = m.get(clave);
      if (!e) {
        e = { email: i.email ?? 'sin correo', nombre: i.estudiante ?? 'Sin nombre', inscripciones: [], pagadas: 0, pendientes: 0, invertido: 0, moneda: i.moneda, ultima: i.creadoEn };
        m.set(clave, e);
      }
      e.inscripciones.push(i);
      if (i.estado === 'PAGADA') { e.pagadas++; e.invertido += i.monto; }
      if (i.estado === 'PENDIENTE_PAGO') e.pendientes++;
      const act = i.fechaPago ?? i.creadoEn;
      if (act > e.ultima) e.ultima = act;
    }
    for (const e of m.values()) e.inscripciones.sort((a, b) => b.creadoEn.localeCompare(a.creadoEn));
    return [...m.values()];
  });

  protected readonly resumen = computed(() => {
    const l = this.estudiantes();
    const conPago = l.filter((e) => e.pagadas > 0).length;
    return {
      total: l.length,
      conPago,
      pctPago: l.length ? Math.round((conPago / l.length) * 100) : 0,
      recurrentes: l.filter((e) => e.pagadas >= 2).length,
    };
  });

  protected readonly filtrados = computed(() => {
    const q = normalizar(this.q());
    const l = q ? this.estudiantes().filter((e) => normalizar(`${e.nombre} ${e.email}`).includes(q)) : [...this.estudiantes()];
    const o = this.orden();
    if (o === 'reciente') l.sort((a, b) => b.ultima.localeCompare(a.ultima));
    if (o === 'invertido') l.sort((a, b) => b.invertido - a.invertido);
    if (o === 'nombre') l.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
    return l;
  });

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.admin().subscribe({
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

  protected alternar(email: string): void {
    this.abierto.set(this.abierto() === email ? null : email);
  }

  protected iniciales(nombre: string): string {
    return nombre.trim().split(/\s+/).slice(0, 2).map((p) => p[0]?.toUpperCase() ?? '').join('') || '?';
  }

  protected portada(i: Inscripcion): string {
    return portadaUrl({ nombre: i.curso, modalidad: i.modalidad });
  }
}
