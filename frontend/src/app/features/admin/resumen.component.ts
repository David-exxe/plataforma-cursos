import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, catchError, forkJoin, of } from 'rxjs';
import { Comprobante, CursoAdmin, Inscripcion, IpBloqueada, Notificacion, Pago } from '../../core/models';
import { ComprobantesService } from '../../core/services/comprobantes.service';
import { CursosService } from '../../core/services/cursos.service';
import { IpsService, NotificacionesService } from '../../core/services/admin.service';
import { InscripcionesService } from '../../core/services/inscripciones.service';
import { PagosService } from '../../core/services/pagos.service';
import { hoyLima } from '../../core/util/fechas';
import { EstadoComponent } from '../../shared/estado.component';
import { IconComponent } from '../../shared/icon.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';

/** null = ese microservicio no respondió (cada servicio es independiente: el resto del panel sigue funcionando). */
interface Datos {
  ins: Inscripcion[] | null;
  pagos: Pago[] | null;
  comps: Comprobante[] | null;
  notifs: Notificacion[] | null;
  cursos: CursoAdmin[] | null;
  ips: IpBloqueada[] | null;
}

interface Alerta {
  tono: 'bad' | 'warn' | 'info';
  icono: string;
  texto: string;
  ruta?: string;
  estado?: string;
}

const plural = (n: number, uno: string, varios: string) => `${n} ${n === 1 ? uno : varios}`;

@Component({
  selector: 'app-resumen',
  imports: [RouterLink, IconComponent, EstadoComponent, MoneyPipe, FechaPipe],
  template: `
    <header class="page-head row">
      <div><h1>Resumen</h1><p class="muted">Estado de la operación en tiempo real.</p></div>
      <span class="spacer"></span>
      <button type="button" class="btn btn-ghost btn-sm" [disabled]="cargando()" (click)="cargar()">
        <app-icon name="refresh" [size]="16" /> Actualizar
      </button>
    </header>

    @if (!datos()) {
      <div class="stack" aria-busy="true"><div class="skel" style="height:120px"></div><div class="skel" style="height:140px"></div><div class="skel" style="height:260px"></div></div>
    } @else {
      <section class="card card-pad" aria-labelledby="h-atencion">
        <h2 id="h-atencion">Requiere atención</h2>
        <ul class="alertas">
          @for (a of alertas(); track a.texto) {
            <li class="alerta" [class]="'alerta alerta-' + a.tono">
              <app-icon [name]="a.icono" [size]="18" />
              <span>{{ a.texto }}</span>
              @if (a.ruta) {
                <a [routerLink]="a.ruta" [queryParams]="a.estado ? { estado: a.estado } : null" class="btn btn-ghost btn-sm">Revisar</a>
              }
            </li>
          } @empty {
            <li class="alerta alerta-ok"><app-icon name="check-circle" [size]="18" /><span>Todo en orden: no hay envíos fallidos ni pagos por revisar.</span></li>
          }
        </ul>
      </section>

      <dl class="kpis">
        <div class="kpi">
          <dt>Ingresos confirmados</dt>
          <dd class="num">
            @if (ingresos(); as lista) {
              @for (i of lista; track i.moneda) { <span class="kpi-linea">{{ i.total | money: i.moneda }}</span> } @empty { {{ 0 | money: 'PEN' }} }
            } @else { <span class="muted">No disponible</span> }
          </dd>
          <span class="kpi-sub">Suma de pagos aprobados</span>
        </div>
        <div class="kpi">
          <dt>Matrículas confirmadas</dt>
          <dd class="num">{{ resumenIns()?.pagadas ?? '—' }}</dd>
          <span class="kpi-sub">de {{ resumenIns()?.total ?? '—' }} inscripciones</span>
        </div>
        <div class="kpi">
          <dt>Por cobrar</dt>
          <dd class="num">{{ resumenIns()?.pendientes ?? '—' }}</dd>
          <span class="kpi-sub">cupos reservados sin pagar</span>
        </div>
        <div class="kpi">
          <dt>Cursos publicados</dt>
          <dd class="num">{{ ocupacion()?.total ?? '—' }}</dd>
          <span class="kpi-sub">activos y por empezar</span>
        </div>
      </dl>

      <div class="dos-col">
        <section class="card" aria-labelledby="h-ocupacion">
          <div class="card-head"><h2 id="h-ocupacion">Ocupación de cursos</h2><a routerLink="/admin/cursos" class="small">Ver cursos</a></div>
          <div class="card-body">
            @if (ocupacion(); as o) {
              <ul class="ocupacion">
                @for (r of o.filas; track r.curso.id) {
                  <li>
                    <div class="ocupacion-cab"><strong>{{ r.curso.nombre }}</strong><span class="small muted num">{{ r.ocupados }} / {{ r.curso.cupos }}</span></div>
                    <div class="mini-barra" [class.llena]="r.ocupados >= r.curso.cupos"><span [style.width.%]="pct(r.ocupados, r.curso.cupos)"></span></div>
                    <span class="small muted">{{ r.curso.fechaInicio | fecha: 'corta' }} · {{ r.curso.fechaInicio | fecha: 'relativa' }}</span>
                  </li>
                } @empty { <li class="muted">No hay cursos próximos publicados.</li> }
              </ul>
            } @else { <p class="muted">No pudimos cargar los cursos.</p> }
          </div>
        </section>

        <section class="card" aria-labelledby="h-ultimas">
          <div class="card-head"><h2 id="h-ultimas">Últimas inscripciones</h2><a routerLink="/admin/inscripciones" class="small">Ver todas</a></div>
          <div class="card-body">
            @if (ultimas(); as u) {
              <ul class="ultimas">
                @for (i of u; track i.id) {
                  <li>
                    <div><strong>{{ i.estudiante }}</strong><span class="cell-sub">{{ i.curso }}</span></div>
                    <app-estado tipo="inscripcion" [valor]="i.estado" />
                  </li>
                } @empty { <li class="muted">Todavía no hay inscripciones.</li> }
              </ul>
            } @else { <p class="muted">No pudimos cargar las inscripciones.</p> }
          </div>
        </section>
      </div>
    }
  `,
})
export class ResumenComponent {
  private readonly inscripciones = inject(InscripcionesService);
  private readonly pagos = inject(PagosService);
  private readonly comprobantes = inject(ComprobantesService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly cursos = inject(CursosService);
  private readonly ips = inject(IpsService);

  protected readonly datos = signal<Datos | null>(null);
  protected readonly cargando = signal(false);

  protected readonly ingresos = computed(() => {
    const p = this.datos()?.pagos;
    if (!p) return null;
    const m = new Map<string, number>();
    for (const x of p) if (x.estado === 'APROBADO') m.set(x.moneda, (m.get(x.moneda) ?? 0) + x.monto);
    return [...m.entries()].map(([moneda, total]) => ({ moneda, total }));
  });

  protected readonly resumenIns = computed(() => {
    const l = this.datos()?.ins;
    if (!l) return null;
    return {
      total: l.length,
      pagadas: l.filter((i) => i.estado === 'PAGADA').length,
      pendientes: l.filter((i) => i.estado === 'PENDIENTE_PAGO').length,
    };
  });

  protected readonly ultimas = computed(() => {
    const l = this.datos()?.ins;
    return l ? [...l].sort((a, b) => b.creadoEn.localeCompare(a.creadoEn)).slice(0, 6) : null;
  });

  protected readonly ocupacion = computed(() => {
    const d = this.datos();
    if (!d?.cursos) return null;
    const ocupados = new Map<number, number>();
    for (const i of d.ins ?? []) {
      if (i.estado === 'PENDIENTE_PAGO' || i.estado === 'PAGADA') ocupados.set(i.cursoId, (ocupados.get(i.cursoId) ?? 0) + 1);
    }
    const hoy = hoyLima();
    const vigentes = d.cursos.filter((c) => c.activo && c.fechaInicio >= hoy).sort((a, b) => a.fechaInicio.localeCompare(b.fechaInicio));
    return { total: vigentes.length, filas: vigentes.slice(0, 6).map((c) => ({ curso: c, ocupados: ocupados.get(c.id) ?? 0 })) };
  });

  protected readonly alertas = computed<Alerta[]>(() => {
    const d = this.datos();
    if (!d) return [];
    const a: Alerta[] = [];

    const caidos = [
      !d.ins || !d.cursos ? 'Matrícula' : '',
      !d.pagos ? 'Pagos' : '',
      !d.comps ? 'Comprobantes' : '',
      !d.notifs ? 'Notificaciones' : '',
    ].filter(Boolean);
    if (caidos.length) {
      a.push({ tono: 'bad', icono: 'alert-triangle', texto: `No respondieron: ${caidos.join(', ')}. Verifica que esos servicios estén en ejecución.` });
    }

    const fallidos = d.comps?.filter((c) => c.estadoEnvio === 'FALLIDO').length ?? 0;
    if (fallidos) {
      a.push({ tono: 'bad', icono: 'mail', texto: `${plural(fallidos, 'comprobante no se pudo', 'comprobantes no se pudieron')} enviar por correo. Reenvíalos a mano.`, ruta: '/admin/comprobantes', estado: 'FALLIDO' });
    }
    const enError = d.comps?.filter((c) => c.estadoEnvio === 'ERROR').length ?? 0;
    if (enError) {
      a.push({ tono: 'warn', icono: 'refresh', texto: `${plural(enError, 'comprobante', 'comprobantes')} con error de envío; el sistema los reintenta automáticamente.`, ruta: '/admin/comprobantes', estado: 'ERROR' });
    }
    const wa = d.notifs?.filter((n) => n.estado === 'FALLIDO').length ?? 0;
    if (wa) {
      a.push({ tono: 'bad', icono: 'message', texto: `${plural(wa, 'mensaje de WhatsApp falló', 'mensajes de WhatsApp fallaron')}. Revisa el token o el número.`, ruta: '/admin/notificaciones', estado: 'FALLIDO' });
    }
    const proceso = d.pagos?.filter((p) => p.estado === 'EN_PROCESO').length ?? 0;
    if (proceso) {
      a.push({ tono: 'info', icono: 'clock', texto: `${plural(proceso, 'pago está', 'pagos están')} en revisión por Mercado Pago.`, ruta: '/admin/pagos', estado: 'EN_PROCESO' });
    }
    const bloqueadas = d.ips?.length ?? 0;
    if (bloqueadas) {
      a.push({ tono: 'info', icono: 'shield', texto: `${plural(bloqueadas, 'dirección IP bloqueada', 'direcciones IP bloqueadas')} en el gateway.`, ruta: '/admin/seguridad' });
    }
    return a;
  });

  constructor() {
    this.cargar();
  }

  protected pct(ocupados: number, cupos: number): number {
    return cupos > 0 ? Math.min(100, Math.round((ocupados / cupos) * 100)) : 0;
  }

  protected cargar(): void {
    this.cargando.set(true);
    const o = <T>(obs: Observable<T>) => obs.pipe(catchError(() => of(null)));
    forkJoin({
      ins: o(this.inscripciones.admin()),
      pagos: o(this.pagos.admin()),
      comps: o(this.comprobantes.admin()),
      notifs: o(this.notificaciones.listar()),
      cursos: o(this.cursos.adminListar()),
      ips: o(this.ips.listar()),
    }).subscribe((d) => {
      this.datos.set(d as Datos);
      this.cargando.set(false);
    });
  }
}
