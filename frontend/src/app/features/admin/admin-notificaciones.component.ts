import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { mensajeDe } from '../../core/http/api-error';
import { Notificacion } from '../../core/models';
import { NotificacionesService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { InscripcionesService } from '../../core/services/inscripciones.service';
import { ToastService } from '../../core/services/toast.service';
import { normalizar } from '../../core/util/texto';
import { EstadoComponent, etiquetaTipoNotificacion } from '../../shared/estado.component';
import { IconComponent } from '../../shared/icon.component';
import { ModalComponent } from '../../shared/modal.component';
import { PaginacionComponent } from '../../shared/paginacion.component';
import { FechaPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

const TAM = 12;
const OPCIONES = [
  { valor: '', texto: 'Todos los estados' },
  { valor: 'PENDIENTE', texto: 'En cola' },
  { valor: 'ENVIADO', texto: 'Enviado' },
  { valor: 'ENTREGADO', texto: 'Entregado' },
  { valor: 'LEIDO', texto: 'Leído' },
  { valor: 'FALLIDO', texto: 'Falló' },
];

@Component({
  selector: 'app-admin-notificaciones',
  imports: [IconComponent, EstadoComponent, ModalComponent, PaginacionComponent, VacioComponent, FechaPipe],
  template: `
    <header class="page-head row">
      <div>
        <h1>Notificaciones</h1>
        <p class="muted">Mensajes de WhatsApp. Los fallos se reintentan a 1, 2, 4 y 8 minutos; luego quedan como «Falló» para reintentarlos aquí.</p>
      </div>
      <span class="spacer"></span>
      <button type="button" class="btn btn-ghost" [disabled]="generando()" (click)="generarRecordatorios()">
        @if (generando()) { <span class="spinner"></span> } @else { <app-icon name="bell" [size]="16" /> } Generar recordatorios
      </button>
    </header>

    <div class="toolbar">
      <label class="search">
        <app-icon name="search" [size]="18" />
        <input type="search" placeholder="Buscar por teléfono, tipo o texto" [value]="q()" (input)="buscar($any($event.target).value)" aria-label="Buscar notificaciones" />
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
      <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar las notificaciones" [texto]="error()!">
        <button type="button" class="btn btn-brand" (click)="cargar()">Reintentar</button>
      </app-vacio>
    } @else if (filtrados().length === 0) {
      <app-vacio icono="message" titulo="No hay notificaciones con estos filtros" />
    } @else {
      <p class="small muted conteo" aria-live="polite">Mostrando las {{ lista().length }} más recientes · {{ filtrados().length }} coinciden</p>
      <div class="table-wrap">
        <table class="tbl">
          <thead><tr><th>Creada</th><th>Tipo</th><th>Teléfono</th><th>Estado</th><th>Mensaje</th><th><span class="sr-only">Acciones</span></th></tr></thead>
          <tbody>
            @for (n of visibles(); track n.id) {
              <tr>
                <td>{{ n.creadoEn | fecha: 'hora' }}</td>
                <td>{{ tipo(n.tipo) }}</td>
                <td class="mono">{{ n.telefono }}</td>
                <td>
                  <app-estado tipo="notificacion" [valor]="n.estado" />
                  <span class="cell-sub">{{ n.intentos }} {{ n.intentos === 1 ? 'intento' : 'intentos' }}</span>
                  @if (n.error) { <span class="cell-sub cell-error">{{ n.error }}</span> }
                </td>
                <td class="truncar">{{ n.mensaje }}</td>
                <td class="acciones">
                  <button type="button" class="btn btn-ghost btn-sm" (click)="detalle.set(n)"><app-icon name="eye" [size]="15" /> Ver</button>
                  @if (n.estado === 'FALLIDO') {
                    <button type="button" class="btn btn-ghost btn-sm" [disabled]="trabajando() === n.id" (click)="reintentar(n)">
                      @if (trabajando() === n.id) { <span class="spinner"></span> } @else { <app-icon name="refresh" [size]="15" /> } Reintentar
                    </button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-paginacion [total]="filtrados().length" [tamano]="tam" [(pagina)]="pagina" />
    }

    <app-modal [abierto]="detalle() !== null" [titulo]="detalle() ? tipo(detalle()!.tipo) : ''" (cerrar)="detalle.set(null)">
      @if (detalle(); as n) {
        <div class="stack">
          <div class="burbuja">{{ n.mensaje }}</div>
          <dl class="datos">
            <div><dt>Teléfono</dt><dd class="mono">{{ n.telefono }}</dd></div>
            <div><dt>Estado</dt><dd><app-estado tipo="notificacion" [valor]="n.estado" /></dd></div>
            <div><dt>Creada</dt><dd>{{ n.creadoEn | fecha: 'hora' }}</dd></div>
            <div><dt>Enviada</dt><dd>{{ n.enviadoEn | fecha: 'hora' }}</dd></div>
            @if (n.plantilla) { <div><dt>Plantilla</dt><dd class="mono">{{ n.plantilla }}</dd></div> }
            @if (n.error) { <div><dt>Último error</dt><dd class="cell-error">{{ n.error }}</dd></div> }
          </dl>
        </div>
      }
    </app-modal>
  `,
})
export class AdminNotificacionesComponent {
  private readonly api = inject(NotificacionesService);
  private readonly inscripciones = inject(InscripcionesService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly route = inject(ActivatedRoute);

  protected readonly tam = TAM;
  protected readonly opciones = OPCIONES;
  protected readonly lista = signal<Notificacion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly q = signal('');
  protected readonly pagina = signal(1);
  protected readonly trabajando = signal<number | null>(null);
  protected readonly generando = signal(false);
  protected readonly detalle = signal<Notificacion | null>(null);
  protected readonly estado = signal(this.estadoInicial());

  protected readonly filtrados = computed(() => {
    const q = normalizar(this.q());
    if (!q) return this.lista();
    return this.lista().filter((n) => normalizar(`${n.telefono} ${etiquetaTipoNotificacion(n.tipo)} ${n.mensaje}`).includes(q));
  });
  protected readonly visibles = computed(() => this.filtrados().slice((this.pagina() - 1) * TAM, this.pagina() * TAM));

  constructor() {
    this.cargar();
  }

  private estadoInicial(): string {
    const e = this.route.snapshot.queryParamMap.get('estado') ?? '';
    return OPCIONES.some((o) => o.valor === e) ? e : '';
  }

  protected tipo(t: string): string {
    return etiquetaTipoNotificacion(t);
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.listar(this.estado()).subscribe({
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

  protected reintentar(n: Notificacion): void {
    this.trabajando.set(n.id);
    this.api.reintentar(n.id).subscribe({
      next: () => {
        this.trabajando.set(null);
        this.toast.info('Notificación en cola: el sistema la reenviará en unos segundos.');
        this.cargar();
      },
      error: (e: unknown) => {
        this.trabajando.set(null);
        this.toast.error(mensajeDe(e));
      },
    });
  }

  /** Mismo efecto que el cron de las 9:00 a. m.: avisa a quienes empiezan mañana. */
  protected async generarRecordatorios(): Promise<void> {
    const ok = await this.confirm.preguntar({
      titulo: 'Generar recordatorios',
      mensaje: 'Se enviará un WhatsApp a los estudiantes con pago confirmado cuyo curso empieza mañana. Normalmente esto ocurre solo a las 9:00 a. m.',
      confirmar: 'Generar ahora',
    });
    if (!ok) return;
    this.generando.set(true);
    this.inscripciones.ejecutarRecordatorios().subscribe({
      next: (r) => {
        this.generando.set(false);
        this.toast.exito(r.recordatoriosGenerados === 0 ? 'No hay recordatorios pendientes para mañana.'
          : `Se generaron ${r.recordatoriosGenerados} ${r.recordatoriosGenerados === 1 ? 'recordatorio' : 'recordatorios'}.`);
        this.cargar();
      },
      error: (e: unknown) => {
        this.generando.set(false);
        this.toast.error(mensajeDe(e));
      },
    });
  }
}
