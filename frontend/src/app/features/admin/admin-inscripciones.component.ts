import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { mensajeDe } from '../../core/http/api-error';
import { Inscripcion } from '../../core/models';
import { InscripcionesService } from '../../core/services/inscripciones.service';
import { normalizar } from '../../core/util/texto';
import { EstadoComponent } from '../../shared/estado.component';
import { IconComponent } from '../../shared/icon.component';
import { PaginacionComponent } from '../../shared/paginacion.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

const TAM = 12;
const OPCIONES = [
  { valor: '', texto: 'Todos los estados' },
  { valor: 'PENDIENTE_PAGO', texto: 'Pendiente de pago' },
  { valor: 'PAGADA', texto: 'Pagada' },
  { valor: 'VENCIDA', texto: 'Vencida' },
  { valor: 'CANCELADA', texto: 'Cancelada' },
];

@Component({
  selector: 'app-admin-inscripciones',
  imports: [IconComponent, EstadoComponent, PaginacionComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <header class="page-head"><h1>Inscripciones</h1><p class="muted">Todas las matrículas. Las pendientes vencen a las 72 horas y liberan el cupo.</p></header>

    <div class="toolbar">
      <label class="search">
        <app-icon name="search" [size]="18" />
        <input type="search" placeholder="Buscar por estudiante, correo o curso" [value]="q()" (input)="buscar($any($event.target).value)" aria-label="Buscar inscripciones" />
      </label>
      <select [value]="estado()" (change)="cambiarEstado($any($event.target).value)" aria-label="Filtrar por estado">
        @for (o of opciones; track o.valor) { <option [value]="o.valor" [selected]="o.valor === estado()">{{ o.texto }}</option> }
      </select>
      <span class="spacer"></span>
      <button type="button" class="btn btn-ghost btn-sm" [disabled]="cargando()" (click)="cargar()"><app-icon name="refresh" [size]="16" /> Actualizar</button>
    </div>

    @if (cargando()) {
      <div class="skel" style="height:320px" aria-busy="true"></div>
    } @else if (error()) {
      <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar las inscripciones" [texto]="error()!">
        <button type="button" class="btn btn-brand" (click)="cargar()">Reintentar</button>
      </app-vacio>
    } @else if (filtrados().length === 0) {
      <app-vacio icono="clipboard" titulo="No hay inscripciones con estos filtros" />
    } @else {
      <p class="small muted conteo" aria-live="polite">{{ filtrados().length }} {{ filtrados().length === 1 ? 'inscripción' : 'inscripciones' }}</p>
      <div class="table-wrap">
        <table class="tbl">
          <thead><tr><th>N.º</th><th>Estudiante</th><th>Curso</th><th class="num">Monto</th><th>Estado</th><th>Reservada</th><th>Pagada</th></tr></thead>
          <tbody>
            @for (i of visibles(); track i.id) {
              <tr>
                <td class="mono">#{{ i.id }}</td>
                <td><strong>{{ i.estudiante }}</strong><span class="cell-sub">{{ i.email }}</span></td>
                <td>{{ i.curso }}<span class="cell-sub">{{ i.fechaInicio | fecha: 'corta' }}</span></td>
                <td class="num">{{ i.monto | money: i.moneda }}</td>
                <td><app-estado tipo="inscripcion" [valor]="i.estado" /></td>
                <td>{{ i.creadoEn | fecha: 'hora' }}</td>
                <td>{{ i.fechaPago | fecha: 'hora' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-paginacion [total]="filtrados().length" [tamano]="tam" [(pagina)]="pagina" />
    }
  `,
})
export class AdminInscripcionesComponent {
  private readonly api = inject(InscripcionesService);
  private readonly route = inject(ActivatedRoute);

  protected readonly tam = TAM;
  protected readonly opciones = OPCIONES;
  protected readonly lista = signal<Inscripcion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly q = signal('');
  protected readonly pagina = signal(1);
  protected readonly estado = signal(this.estadoInicial());

  protected readonly filtrados = computed(() => {
    const q = normalizar(this.q());
    if (!q) return this.lista();
    return this.lista().filter((i) => normalizar(`${i.estudiante} ${i.email} ${i.curso} ${i.id}`).includes(q));
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
        this.lista.set([...l].sort((a, b) => b.creadoEn.localeCompare(a.creadoEn)));
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
