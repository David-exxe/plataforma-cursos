import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { mensajeDe } from '../../core/http/api-error';
import { Comprobante } from '../../core/models';
import { ComprobantesService } from '../../core/services/comprobantes.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { normalizar } from '../../core/util/texto';
import { EstadoComponent } from '../../shared/estado.component';
import { IconComponent } from '../../shared/icon.component';
import { PaginacionComponent } from '../../shared/paginacion.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

const TAM = 12;
const OPCIONES = [
  { valor: '', texto: 'Todos los estados' },
  { valor: 'PENDIENTE', texto: 'En cola' },
  { valor: 'ENVIADO', texto: 'Enviado' },
  { valor: 'ERROR', texto: 'Reintentando' },
  { valor: 'FALLIDO', texto: 'Falló' },
];

@Component({
  selector: 'app-admin-comprobantes',
  imports: [IconComponent, EstadoComponent, PaginacionComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <header class="page-head">
      <h1>Comprobantes</h1>
      <p class="muted">El pago y la matrícula ya están confirmados aunque el correo falle. Los envíos con error se reintentan a 1, 5, 15 y 60 minutos; después quedan como «Falló» para reenviarlos aquí.</p>
    </header>

    <div class="toolbar">
      <label class="search">
        <app-icon name="search" [size]="18" />
        <input type="search" placeholder="Buscar por número, estudiante, correo o curso" [value]="q()" (input)="buscar($any($event.target).value)" aria-label="Buscar comprobantes" />
      </label>
      <select (change)="cambiarEstado($any($event.target).value)" aria-label="Filtrar por estado de envío">
        @for (o of opciones; track o.valor) { <option [value]="o.valor" [selected]="o.valor === estado()">{{ o.texto }}</option> }
      </select>
      <span class="spacer"></span>
      <button type="button" class="btn btn-ghost btn-sm" [disabled]="cargando()" (click)="cargar()"><app-icon name="refresh" [size]="16" /> Actualizar</button>
    </div>

    @if (cargando()) {
      <div class="skel" style="height:320px" aria-busy="true"></div>
    } @else if (error()) {
      <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar los comprobantes" [texto]="error()!">
        <button type="button" class="btn btn-brand" (click)="cargar()">Reintentar</button>
      </app-vacio>
    } @else if (filtrados().length === 0) {
      <app-vacio icono="file-text" titulo="No hay comprobantes con estos filtros" />
    } @else {
      <p class="small muted conteo" aria-live="polite">{{ filtrados().length }} {{ filtrados().length === 1 ? 'comprobante' : 'comprobantes' }}</p>
      <div class="table-wrap">
        <table class="tbl">
          <thead><tr><th>Número</th><th>Estudiante</th><th>Curso</th><th class="num">Monto</th><th>Emitido</th><th>Envío al correo</th><th><span class="sr-only">Acciones</span></th></tr></thead>
          <tbody>
            @for (c of visibles(); track c.id) {
              <tr>
                <td class="mono">{{ c.numero }}</td>
                <td><strong>{{ c.estudiante }}</strong><span class="cell-sub">{{ c.email }}</span></td>
                <td>{{ c.curso }}</td>
                <td class="num">{{ c.monto | money: c.moneda }}</td>
                <td>{{ c.fechaEmision | fecha: 'hora' }}</td>
                <td>
                  <app-estado tipo="envio" [valor]="c.estadoEnvio" />
                  <span class="cell-sub">{{ c.intentosEnvio }} {{ c.intentosEnvio === 1 ? 'intento' : 'intentos' }}@if (c.enviadoEn) { · {{ c.enviadoEn | fecha: 'hora' }} }</span>
                  @if (c.ultimoError) { <span class="cell-sub cell-error">{{ c.ultimoError }}</span> }
                </td>
                <td class="acciones">
                  <button type="button" class="btn btn-ghost btn-sm btn-cuadrado" [disabled]="trabajando() === c.id" (click)="descargar(c)" aria-label="Descargar PDF" title="Descargar PDF"><app-icon name="download" [size]="16" /></button>
                  <button type="button" class="btn btn-ghost btn-sm" [disabled]="trabajando() === c.id" (click)="reenviar(c)">
                    @if (trabajando() === c.id) { <span class="spinner"></span> } @else { <app-icon name="send" [size]="15" /> } Reenviar
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-paginacion [total]="filtrados().length" [tamano]="tam" [(pagina)]="pagina" />
    }
  `,
})
export class AdminComprobantesComponent {
  private readonly api = inject(ComprobantesService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly route = inject(ActivatedRoute);

  protected readonly tam = TAM;
  protected readonly opciones = OPCIONES;
  protected readonly lista = signal<Comprobante[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly q = signal('');
  protected readonly pagina = signal(1);
  protected readonly trabajando = signal<number | null>(null);
  protected readonly estado = signal(this.estadoInicial());

  protected readonly filtrados = computed(() => {
    const q = normalizar(this.q());
    if (!q) return this.lista();
    return this.lista().filter((c) => normalizar(`${c.numero} ${c.estudiante} ${c.email} ${c.curso}`).includes(q));
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

  protected descargar(c: Comprobante): void {
    this.trabajando.set(c.id);
    this.api.descargar(c).subscribe({
      next: () => this.trabajando.set(null),
      error: (e: unknown) => {
        this.trabajando.set(null);
        this.toast.error(mensajeDe(e));
      },
    });
  }

  protected async reenviar(c: Comprobante): Promise<void> {
    if (c.estadoEnvio === 'ENVIADO') {
      const ok = await this.confirm.preguntar({
        titulo: 'Reenviar comprobante',
        mensaje: `El comprobante ${c.numero} ya se envió a ${c.email}. ¿Quieres enviarlo otra vez?`,
        confirmar: 'Reenviar',
      });
      if (!ok) return;
    }
    this.trabajando.set(c.id);
    this.api.reenviar(c.id).subscribe({
      next: (r) => {
        this.trabajando.set(null);
        this.lista.update((l) => l.map((x) => (x.id === r.comprobante.id ? r.comprobante : x)));
        if (r.reenviado) this.toast.exito(`Comprobante ${c.numero} enviado a ${c.email}.`);
        else this.toast.error('No se pudo enviar el correo. Revisa el error indicado en la fila; el sistema lo seguirá reintentando.');
      },
      error: (e: unknown) => {
        this.trabajando.set(null);
        this.toast.error(mensajeDe(e));
      },
    });
  }
}
