import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { mensajeDe } from '../../core/http/api-error';
import { Curso, Inscripcion } from '../../core/models';
import { CursosService } from '../../core/services/cursos.service';
import { InscripcionesService } from '../../core/services/inscripciones.service';
import { ToastService } from '../../core/services/toast.service';
import { diasHasta } from '../../core/util/fechas';
import { CuposComponent } from '../../shared/cupos.component';
import { IconComponent } from '../../shared/icon.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

@Component({
  selector: 'app-curso-detalle',
  imports: [RouterLink, IconComponent, CuposComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <section class="container page">
      <a routerLink="/cursos" class="volver"><app-icon name="arrow-left" [size]="16" /> Todos los cursos</a>

      @if (cargando()) {
        <div class="detalle" aria-busy="true">
          <div class="stack"><div class="skel" style="height:40px;width:70%"></div><div class="skel" style="height:260px"></div></div>
          <div class="skel" style="height:300px"></div>
        </div>
      } @else if (error()) {
        <app-vacio tono="error" icono="alert-circle" titulo="No pudimos mostrar este curso" [texto]="error()!">
          <a routerLink="/cursos" class="btn btn-brand">Ver todos los cursos</a>
        </app-vacio>
      } @else if (curso(); as c) {
        <div class="detalle">
          <article class="detalle-main">
            <div class="ticket-chips">
              <span class="chip"><app-icon [name]="c.modalidad === 'ONLINE' ? 'video' : 'map-pin'" [size]="14" /> {{ c.modalidad === 'ONLINE' ? 'Online' : 'Presencial' }}</span>
              <span class="chip chip-neutro">{{ c.codigo }}</span>
            </div>
            <h1>{{ c.nombre }}</h1>

            <dl class="facts">
              <div>
                <dt><app-icon name="calendar" [size]="16" /> Inicio</dt>
                <dd>{{ c.fechaInicio | fecha: 'larga' }} <span class="muted">· {{ c.fechaInicio | fecha: 'relativa' }}</span></dd>
              </div>
              <div>
                <dt><app-icon name="clock" [size]="16" /> Horario</dt>
                <dd>{{ c.horario || 'Por confirmar' }}</dd>
              </div>
              <div>
                <dt><app-icon [name]="c.modalidad === 'ONLINE' ? 'video' : 'map-pin'" [size]="16" /> {{ c.modalidad === 'ONLINE' ? 'Cómo entrar' : 'Lugar' }}</dt>
                <dd>
                  @if (c.modalidad === 'ONLINE') { El enlace de la clase llega a tu correo y WhatsApp cuando se confirma el pago. }
                  @else { {{ c.ubicacion || 'Se confirmará antes del inicio' }} }
                </dd>
              </div>
            </dl>

            @if (parrafos().length) {
              <div class="prosa">
                <h2>De qué trata</h2>
                @for (p of parrafos(); track $index) { <p>{{ p }}</p> }
              </div>
            }

            <div class="prosa">
              <h2>Cómo es tu matrícula</h2>
              <ol class="pasos-lista">
                <li><strong>Reservas tu cupo.</strong> Queda guardado {{ horas }} horas mientras pagas; si no pagas, se libera.</li>
                <li><strong>Pagas con Mercado Pago.</strong> Con tarjeta u otros medios habilitados.</li>
                <li><strong>Recibes la confirmación.</strong> Te avisamos por WhatsApp y te enviamos el comprobante en PDF a tu correo.</li>
              </ol>
            </div>
          </article>

          <aside class="compra card" aria-label="Reserva tu cupo">
            <p class="compra-precio num">{{ c.precio | money: c.moneda }}</p>
            <app-cupos [total]="c.cupos" [disponibles]="c.cuposDisponibles" />

            @if (yaInscrita(); as ins) {
              <div class="alert alert-ok" role="status"><app-icon name="check-circle" [size]="18" />
                <div>{{ ins.estado === 'PAGADA' ? 'Ya estás matriculado en este curso.' : 'Tienes un cupo reservado pendiente de pago.' }}</div>
              </div>
              <a routerLink="/mis-inscripciones" class="btn btn-brand btn-block">{{ ins.estado === 'PAGADA' ? 'Ver mi matrícula' : 'Ir a pagar' }}</a>
            } @else if (empezo()) {
              <div class="alert alert-warn"><app-icon name="alert-triangle" [size]="18" /><div>Este curso ya inició y no admite nuevas inscripciones.</div></div>
            } @else if (c.cuposDisponibles <= 0) {
              <button type="button" class="btn btn-block" disabled>Cupos agotados</button>
              <p class="small muted">Revisa otros cursos con fechas cercanas.</p>
            } @else if (!auth.autenticado()) {
              <a routerLink="/ingresar" [queryParams]="{ returnUrl: '/cursos/' + c.id }" class="btn btn-primary btn-block">Ingresar para reservar</a>
              <p class="small muted">¿Primera vez? <a routerLink="/registro" [queryParams]="{ returnUrl: '/cursos/' + c.id }">Crea tu cuenta</a> en un minuto.</p>
            } @else {
              <button type="button" class="btn btn-primary btn-block" [disabled]="inscribiendo()" (click)="inscribir()">
                @if (inscribiendo()) { <span class="spinner"></span> } Reservar mi cupo
              </button>
              <p class="small muted">No se cobra al reservar. Tienes {{ horas }} horas para completar el pago.</p>
            }
          </aside>
        </div>
      }
    </section>
  `,
})
export class CursoDetalleComponent {
  protected readonly auth = inject(AuthService);
  private readonly cursos = inject(CursosService);
  private readonly inscripciones = inject(InscripcionesService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly horas = environment.horasReserva;
  protected readonly curso = signal<Curso | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly inscribiendo = signal(false);
  protected readonly yaInscrita = signal<Inscripcion | null>(null);

  protected readonly parrafos = computed(() => (this.curso()?.descripcion ?? '').split(/\n+/).map((p) => p.trim()).filter(Boolean));
  protected readonly empezo = computed(() => {
    const c = this.curso();
    return !!c && diasHasta(c.fechaInicio) < 0;
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((p) => this.cargar(p.get('id') ?? ''));
  }

  private cargar(id: string): void {
    this.cargando.set(true);
    this.error.set(null);
    this.curso.set(null);
    this.cursos.detalle(id).subscribe({
      next: (c) => {
        this.curso.set(c);
        this.cargando.set(false);
        this.buscarInscripcion(c.id);
      },
      error: (e: unknown) => {
        this.error.set(mensajeDe(e));
        this.cargando.set(false);
      },
    });
  }

  /** Evita ofrecer "Reservar" si ya hay una inscripción viva (el backend respondería 409). */
  private buscarInscripcion(cursoId: number): void {
    this.yaInscrita.set(null);
    if (!this.auth.autenticado()) return;
    this.inscripciones.mias().subscribe({
      next: (l) =>
        this.yaInscrita.set(l.find((i) => i.cursoId === cursoId && (i.estado === 'PENDIENTE_PAGO' || i.estado === 'PAGADA')) ?? null),
      error: () => undefined, // información opcional: si falla, el botón sigue disponible
    });
  }

  protected inscribir(): void {
    const c = this.curso();
    if (!c || this.inscribiendo()) return;
    this.inscribiendo.set(true);
    this.inscripciones.inscribir(c.id).subscribe({
      next: (i) => {
        this.toast.exito('Cupo reservado. Completa el pago para confirmarlo.');
        void this.router.navigate(['/mis-inscripciones'], { queryParams: { nueva: i.id } });
      },
      error: (e: unknown) => {
        this.inscribiendo.set(false);
        this.toast.error(mensajeDe(e));
        // Refresca cupos (puede que alguien tomó el último) sin mostrar el esqueleto de carga
        this.cursos.detalle(c.id).subscribe({ next: (x) => this.curso.set(x), error: () => undefined });
        this.buscarInscripcion(c.id);
      },
    });
  }
}
