import { Component, computed, inject, signal } from '@angular/core';
import { mensajeDe } from '../../core/http/api-error';
import { Curso, Modalidad } from '../../core/models';
import { CursosService } from '../../core/services/cursos.service';
import { portadaUrl } from '../../core/util/portadas';
import { normalizar } from '../../core/util/texto';
import { CursoTicketComponent } from '../../shared/curso-ticket.component';
import { IconComponent } from '../../shared/icon.component';
import { VacioComponent } from '../../shared/vacio.component';

type FiltroModalidad = '' | Modalidad;
type Orden = 'fecha' | 'precio' | 'cupos';

@Component({
  selector: 'app-catalogo',
  imports: [CursoTicketComponent, IconComponent, VacioComponent],
  template: `
    <section class="container page">
      <header class="hero">
        <div class="hero-texto">
          <span class="hero-kicker">Inscripciones abiertas</span>
          <h1>Elige tu próximo curso</h1>
          <p class="lead">Online y presenciales. Reserva tu cupo, paga con Mercado Pago y recibe tu comprobante por correo y WhatsApp.</p>
          <ol class="hero-pasos">
            <li><app-icon name="ticket" [size]="16" /> Reserva tu cupo</li>
            <li><app-icon name="credit-card" [size]="16" /> Paga en línea</li>
            <li><app-icon name="file-text" [size]="16" /> Recibe tu boleta</li>
          </ol>
        </div>
        @if (mosaico().length) {
          <div class="hero-mosaico" aria-hidden="true">
            @for (c of mosaico(); track c.id) { <img [src]="portada(c)" alt="" /> }
          </div>
        }
      </header>

      @if (!cargando() && !error() && cursos().length) {
        <dl class="hero-stats">
          <div><dt>Cursos abiertos</dt><dd class="num">{{ stats().total }}</dd></div>
          <div><dt>Online</dt><dd class="num">{{ stats().online }}</dd></div>
          <div><dt>Presenciales</dt><dd class="num">{{ stats().presencial }}</dd></div>
          <div><dt>Cupos libres</dt><dd class="num">{{ stats().cupos }}</dd></div>
        </dl>
      }

      <div class="toolbar">
        <div class="seg" role="group" aria-label="Modalidad">
          @for (op of opciones; track op.valor) {
            <button type="button" [class.on]="modalidad() === op.valor" [attr.aria-pressed]="modalidad() === op.valor" (click)="modalidad.set(op.valor)">{{ op.texto }}</button>
          }
        </div>
        <label class="search">
          <app-icon name="search" [size]="18" />
          <input type="search" placeholder="Buscar por nombre o código" [value]="q()" (input)="q.set($any($event.target).value)" aria-label="Buscar cursos" />
        </label>
        <label class="select-inline">
          <span class="sr-only">Ordenar por</span>
          <select [value]="orden()" (change)="orden.set($any($event.target).value)" aria-label="Ordenar por">
            <option value="fecha">Próximos a empezar</option>
            <option value="precio">Precio: menor a mayor</option>
            <option value="cupos">Más cupos libres</option>
          </select>
        </label>
      </div>

      @if (cargando()) {
        <div class="grid-tickets" aria-busy="true" aria-label="Cargando cursos">
          @for (n of [1, 2, 3, 4, 5, 6]; track n) { <div class="skel skel-ticket"></div> }
        </div>
      } @else if (error()) {
        <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar los cursos" [texto]="error()!">
          <button type="button" class="btn btn-brand" (click)="cargar()"><app-icon name="refresh" [size]="16" /> Reintentar</button>
        </app-vacio>
      } @else if (filtrados().length === 0) {
        @if (hayFiltros()) {
          <app-vacio icono="search" titulo="Ningún curso coincide con tu búsqueda" texto="Prueba con otra palabra o quita los filtros.">
            <button type="button" class="btn btn-ghost" (click)="limpiar()">Quitar filtros</button>
          </app-vacio>
        } @else {
          <app-vacio icono="calendar" titulo="Aún no hay cursos abiertos" texto="Vuelve pronto: publicamos nuevas fechas cada semana." />
        }
      } @else {
        <p class="muted small conteo" aria-live="polite">{{ filtrados().length }} {{ filtrados().length === 1 ? 'curso' : 'cursos' }}</p>
        <div class="grid-tickets">
          @for (c of filtrados(); track c.id; let i = $index) {
            <app-curso-ticket [curso]="c" [destacado]="i === 0 && !hayFiltros() && orden() === 'fecha'" />
          }
        </div>
      }
    </section>
  `,
})
export class CatalogoComponent {
  private readonly api = inject(CursosService);

  protected readonly cursos = signal<Curso[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly modalidad = signal<FiltroModalidad>('');
  protected readonly q = signal('');
  protected readonly orden = signal<Orden>('fecha');

  protected readonly opciones: { valor: FiltroModalidad; texto: string }[] = [
    { valor: '', texto: 'Todos' },
    { valor: 'ONLINE', texto: 'Online' },
    { valor: 'PRESENCIAL', texto: 'Presencial' },
  ];

  /** Tres portadas para el mosaico del encabezado. */
  protected readonly mosaico = computed(() => this.cursos().slice(0, 3));

  protected readonly stats = computed(() => {
    const l = this.cursos();
    return {
      total: l.length,
      online: l.filter((c) => c.modalidad === 'ONLINE').length,
      presencial: l.filter((c) => c.modalidad === 'PRESENCIAL').length,
      cupos: l.reduce((s, c) => s + Math.max(0, c.cuposDisponibles), 0),
    };
  });

  protected readonly hayFiltros = computed(() => this.modalidad() !== '' || this.q().trim() !== '');

  protected readonly filtrados = computed(() => {
    let lista = this.cursos();
    const m = this.modalidad();
    if (m) lista = lista.filter((c) => c.modalidad === m);
    const q = normalizar(this.q());
    if (q) lista = lista.filter((c) => normalizar(`${c.nombre} ${c.codigo} ${c.descripcion ?? ''}`).includes(q));
    const copia = [...lista]; // el backend ya ordena por fecha de inicio
    if (this.orden() === 'precio') copia.sort((a, b) => a.precio - b.precio);
    if (this.orden() === 'cupos') copia.sort((a, b) => b.cuposDisponibles - a.cuposDisponibles);
    return copia;
  });

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.catalogo().subscribe({
      next: (l) => {
        this.cursos.set(l);
        this.cargando.set(false);
      },
      error: (e: unknown) => {
        this.error.set(mensajeDe(e));
        this.cargando.set(false);
      },
    });
  }

  protected portada(c: Curso): string {
    return portadaUrl(c);
  }

  protected limpiar(): void {
    this.modalidad.set('');
    this.q.set('');
  }
}
