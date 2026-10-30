import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, exhaustMap, forkJoin, map, of, takeWhile, tap, timer } from 'rxjs';
import { Comprobante, Inscripcion, Pago } from '../../core/models';
import { ComprobantesService } from '../../core/services/comprobantes.service';
import { InscripcionesService } from '../../core/services/inscripciones.service';
import { PagosService } from '../../core/services/pagos.service';
import { ToastService } from '../../core/services/toast.service';
import { mensajeDe } from '../../core/http/api-error';
import { IconComponent } from '../../shared/icon.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

interface Resultado {
  pago: Pago;
  inscripcion: Inscripcion | null;
  comprobante: Comprobante | null;
}

/** status_detail de Mercado Pago → texto para personas. */
const MOTIVOS: Record<string, string> = {
  cc_rejected_insufficient_amount: 'La tarjeta no tiene fondos suficientes.',
  cc_rejected_bad_filled_security_code: 'El código de seguridad es incorrecto.',
  cc_rejected_bad_filled_date: 'La fecha de vencimiento es incorrecta.',
  cc_rejected_bad_filled_other: 'Algún dato de la tarjeta es incorrecto.',
  cc_rejected_call_for_authorize: 'Debes autorizar este pago llamando a tu banco.',
  cc_rejected_card_disabled: 'La tarjeta está inactiva. Pídele a tu banco que la active.',
  cc_rejected_max_attempts: 'Superaste el máximo de intentos. Prueba con otra tarjeta.',
  cc_rejected_duplicated_payment: 'Ya hiciste un pago por este mismo monto.',
  cc_rejected_high_risk: 'El pago fue rechazado por seguridad. Prueba con otro medio de pago.',
};

const MAX_CONSULTAS = 20; // 20 × 3 s = 60 s
const CADA_MS = 3000;

/**
 * Página de retorno de Mercado Pago (/pago/resultado?estado=...&inscripcion=ID).
 * El resultado llega al backend por webhook y se propaga por eventos (pago → matrícula → comprobante),
 * así que esta pantalla consulta cada 3 s hasta que todo quede confirmado.
 */
@Component({
  selector: 'app-pago-resultado',
  imports: [RouterLink, IconComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <section class="container page page-estrecha">
      @if (!idValido) {
        <app-vacio tono="error" icono="alert-circle" titulo="Falta la inscripción" texto="El enlace no indica qué pago consultar.">
          <a routerLink="/mis-inscripciones" class="btn btn-brand">Ir a mis inscripciones</a>
        </app-vacio>
      } @else {
        <div class="resultado card">
          @switch (vista()) {
            @case ('cargando') {
              <div class="resultado-cab"><span class="spinner spinner-lg"></span><h1>Consultando tu pago…</h1></div>
            }
            @case ('error') {
              <div class="resultado-cab resultado-bad">
                <app-icon name="alert-circle" [size]="40" />
                <h1>No pudimos consultar tu pago</h1>
                <p class="muted">{{ error() }}</p>
              </div>
              <div class="resultado-acciones">
                <a routerLink="/mis-inscripciones" class="btn btn-brand">Ir a mis inscripciones</a>
              </div>
            }
            @case ('esperando') {
              <div class="resultado-cab resultado-info">
                @if (sondeando()) { <span class="spinner spinner-lg"></span> } @else { <app-icon name="clock" [size]="40" /> }
                <h1>{{ sondeando() ? 'Estamos confirmando tu pago' : 'Tu pago sigue en proceso' }}</h1>
                <p class="muted">
                  @if (estadoQuery === 'pendiente') { Mercado Pago aún no lo acredita (por ejemplo, si elegiste pagar en efectivo o por transferencia). }
                  @else { Mercado Pago nos avisa en unos segundos. No cierres esta página. }
                </p>
              </div>
              <div class="resultado-acciones">
                <a routerLink="/mis-inscripciones" class="btn btn-ghost">Ver mis inscripciones</a>
                @if (resultado()?.pago?.linkPago; as link) {
                  <button type="button" class="btn btn-ghost" (click)="reintentarPago(link)">Volver a intentar el pago</button>
                }
              </div>
              @if (agotado()) {
                <p class="resultado-nota small muted">Te avisaremos por correo y WhatsApp apenas se confirme. Puedes salir de esta página.</p>
              }
            }
            @case ('aprobado') {
              <div class="resultado-cab resultado-ok">
                <app-icon name="check-circle" [size]="44" />
                <h1>¡Pago recibido!</h1>
                <p class="muted">Tu cupo en <strong>{{ resultado()!.pago.curso }}</strong> está asegurado.</p>
              </div>

              <ol class="seguimiento" aria-label="Avance de tu matrícula">
                <li class="hecho">
                  <app-icon name="check-circle" [size]="22" />
                  <div><strong>Pago confirmado</strong>
                    <span>{{ resultado()!.pago.monto | money: resultado()!.pago.moneda }} · {{ resultado()!.pago.fechaAprobacion | fecha: 'hora' }}</span></div>
                </li>
                <li [class.hecho]="matriculada()">
                  @if (matriculada()) { <app-icon name="check-circle" [size]="22" /> } @else if (sondeando()) { <span class="spinner"></span> } @else { <app-icon name="clock" [size]="22" /> }
                  <div><strong>Matrícula confirmada</strong>
                    <span>{{ matriculada() ? 'Ya figuras como inscrito en el curso.' : 'Actualizando tu inscripción…' }}</span></div>
                </li>
                <li [class.hecho]="!!comprobante()">
                  @if (comprobante()) { <app-icon name="check-circle" [size]="22" /> } @else if (sondeando()) { <span class="spinner"></span> } @else { <app-icon name="clock" [size]="22" /> }
                  <div><strong>Comprobante</strong>
                    <span>{{ comprobante() ? comprobante()!.numero + ' · enviado a ' + comprobante()!.email : 'Generándolo y enviándolo a tu correo…' }}</span></div>
                </li>
              </ol>

              <div class="resultado-acciones">
                <a routerLink="/mis-inscripciones" class="btn btn-brand">Ver mi clase</a>
                @if (comprobante(); as c) {
                  <button type="button" class="btn btn-ghost" [disabled]="descargando()" (click)="descargar(c)">
                    <app-icon name="download" [size]="16" /> Descargar comprobante
                  </button>
                }
              </div>
              @if (agotado()) {
                <p class="resultado-nota small muted">Estamos terminando de procesar tu matrícula. Revisa «Mis inscripciones» en unos minutos; tu pago ya está registrado.</p>
              }
            }
            @case ('rechazado') {
              <div class="resultado-cab resultado-bad">
                <app-icon name="alert-circle" [size]="44" />
                <h1>Tu pago no fue aprobado</h1>
                <p class="muted">{{ motivo() }}</p>
              </div>
              <div class="resultado-acciones">
                @if (resultado()?.pago?.linkPago; as link) {
                  <button type="button" class="btn btn-primary" (click)="reintentarPago(link)">Intentar de nuevo</button>
                }
                <a routerLink="/mis-inscripciones" class="btn btn-ghost">Ver mis inscripciones</a>
              </div>
              <p class="resultado-nota small muted">Tu cupo sigue reservado hasta que venza el plazo de pago.</p>
            }
            @case ('reembolsado') {
              <div class="resultado-cab resultado-info">
                <app-icon name="info" [size]="40" />
                <h1>Este pago fue reembolsado</h1>
                <p class="muted">El monto volvió a tu medio de pago.</p>
              </div>
              <div class="resultado-acciones"><a routerLink="/cursos" class="btn btn-brand">Ver cursos</a></div>
            }
          }
        </div>
      }
    </section>
  `,
})
export class PagoResultadoComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly pagos = inject(PagosService);
  private readonly inscripciones = inject(InscripcionesService);
  private readonly comprobantes = inject(ComprobantesService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly params = this.route.snapshot.queryParamMap;
  protected readonly id = Number(this.params.get('inscripcion'));
  protected readonly idValido = Number.isInteger(this.id) && this.id > 0;
  /** Lo que informó Mercado Pago en la URL (solo orientativo: la verdad es el estado guardado por el backend). */
  protected readonly estadoQuery = this.params.get('estado');

  protected readonly resultado = signal<Resultado | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly sondeando = signal(true);
  protected readonly agotado = signal(false);
  protected readonly descargando = signal(false);

  protected readonly vista = computed(() => {
    if (this.error()) return 'error';
    const r = this.resultado();
    if (!r) return 'cargando';
    switch (r.pago.estado) {
      case 'APROBADO': return 'aprobado';
      case 'RECHAZADO': return 'rechazado';
      case 'REEMBOLSADO': return 'reembolsado';
      default: return 'esperando';
    }
  });

  protected readonly matriculada = computed(() => this.resultado()?.inscripcion?.estado === 'PAGADA');
  protected readonly comprobante = computed(() => this.resultado()?.comprobante ?? null);
  protected readonly motivo = computed(() => {
    const d = this.resultado()?.pago.estadoDetalle ?? '';
    return MOTIVOS[d] ?? 'El medio de pago no autorizó la operación. Prueba con otra tarjeta u otro medio.';
  });

  constructor() {
    if (this.idValido) this.sondear();
  }

  private terminado(r: Resultado | null): boolean {
    if (!r) return false;
    const e = r.pago.estado;
    if (e === 'RECHAZADO' || e === 'REEMBOLSADO') return true;
    return e === 'APROBADO' && r.inscripcion?.estado === 'PAGADA' && !!r.comprobante;
  }

  private sondear(): void {
    let consultas = 0;
    let fallos = 0;
    timer(0, CADA_MS)
      .pipe(
        exhaustMap(() =>
          forkJoin({
            pago: this.pagos.estado(this.id),
            // Estos dos son complementarios: si fallan no impiden mostrar el estado del pago
            inscripcion: this.inscripciones.detalle(this.id).pipe(catchError(() => of(null))),
            comprobante: this.comprobantes.mios().pipe(
              map((l) => l.find((c) => c.inscripcionId === this.id) ?? null),
              catchError(() => of(null)),
            ),
          }).pipe(catchError(() => of(null))),
        ),
        tap((r) => {
          consultas++;
          if (r) {
            fallos = 0;
            this.resultado.set(r);
          } else if (++fallos >= 3) {
            this.error.set('No pudimos consultar el estado del pago. Revisa «Mis inscripciones» en unos minutos.');
          }
        }),
        takeWhile((r) => !this.terminado(r) && consultas < MAX_CONSULTAS && this.error() === null, true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        complete: () => {
          this.sondeando.set(false);
          if (!this.terminado(this.resultado()) && this.error() === null) this.agotado.set(true);
        },
      });
  }

  protected reintentarPago(link: string): void {
    this.pagos.abrirLink(link);
  }

  protected descargar(c: Comprobante): void {
    this.descargando.set(true);
    this.comprobantes.descargar(c).subscribe({
      next: () => this.descargando.set(false),
      error: (e: unknown) => {
        this.descargando.set(false);
        this.toast.error(mensajeDe(e));
      },
    });
  }
}
