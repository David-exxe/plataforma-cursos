import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { mensajeDe } from '../../core/http/api-error';
import { IpBloqueada } from '../../core/models';
import { IpsService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { tiempoRestante } from '../../core/util/fechas';
import { Validadores, errorDeCampo } from '../../shared/forms';
import { IconComponent } from '../../shared/icon.component';
import { FechaPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

/** Administración manual de IPs del gateway (GET/POST /gateway/admin/ips, DELETE /gateway/admin/ips/{ip}). */
@Component({
  selector: 'app-admin-seguridad',
  imports: [ReactiveFormsModule, IconComponent, VacioComponent, FechaPipe],
  template: `
    <header class="page-head"><h1>Seguridad</h1><p class="muted">Direcciones IP bloqueadas por el gateway antes de llegar a los servicios.</p></header>

    <div class="dos-col dos-col-ancha">
      <section class="card" aria-labelledby="h-ips">
        <div class="card-head">
          <h2 id="h-ips">IP bloqueadas</h2>
          <button type="button" class="btn btn-ghost btn-sm" [disabled]="cargando()" (click)="cargar()"><app-icon name="refresh" [size]="16" /> Actualizar</button>
        </div>
        @if (cargando()) {
          <div class="card-body"><div class="skel" style="height:140px" aria-busy="true"></div></div>
        } @else if (error()) {
          <div class="card-body">
            <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar las IP" [texto]="error()!">
              <button type="button" class="btn btn-brand" (click)="cargar()">Reintentar</button>
            </app-vacio>
          </div>
        } @else if (lista().length === 0) {
          <div class="card-body"><app-vacio icono="shield" titulo="No hay IP bloqueadas" texto="Cuando alguien exceda los límites o falle varios inicios de sesión, aparecerá aquí." /></div>
        } @else {
          <div class="table-wrap table-plano">
            <table class="tbl">
              <thead><tr><th>IP</th><th>Motivo</th><th>Bloqueada hasta</th><th><span class="sr-only">Acciones</span></th></tr></thead>
              <tbody>
                @for (b of lista(); track b.ip) {
                  <tr>
                    <td class="mono">{{ b.ip }}</td>
                    <td>{{ b.motivo || '—' }}</td>
                    <td>
                      @if (permanente(b)) { Permanente <span class="cell-sub">Lista negra de configuración</span> }
                      @else { {{ b.hasta | fecha: 'hora' }} <span class="cell-sub">{{ restante(b) }}</span> }
                    </td>
                    <td class="acciones">
                      @if (!permanente(b)) {
                        <button type="button" class="btn btn-ghost btn-sm" [disabled]="trabajando() === b.ip" (click)="desbloquear(b)">
                          @if (trabajando() === b.ip) { <span class="spinner"></span> } Desbloquear
                        </button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <div class="stack">
        <section class="card card-pad stack" aria-labelledby="h-bloquear">
          <h2 id="h-bloquear">Bloquear una IP</h2>
          <form [formGroup]="form" (ngSubmit)="bloquear()" novalidate class="stack">
            <div class="field">
              <label for="ip">Dirección IP</label>
              <input id="ip" type="text" formControlName="ip" placeholder="203.0.113.7" autocomplete="off" [class.invalid]="!!err('ip')" />
              @if (err('ip'); as m) { <span class="error-msg">{{ m }}</span> }
            </div>
            <div class="form-grid">
              <div class="field">
                <label for="minutos">Minutos</label>
                <input id="minutos" type="number" min="1" max="10080" formControlName="minutos" [class.invalid]="!!err('minutos')" />
                @if (err('minutos'); as m) { <span class="error-msg">{{ m }}</span> } @else { <span class="hint">Hasta 7 días.</span> }
              </div>
              <div class="field">
                <label for="motivo">Motivo <span class="muted">(opcional)</span></label>
                <input id="motivo" type="text" formControlName="motivo" maxlength="200" [class.invalid]="!!err('motivo')" />
              </div>
            </div>
            <button type="submit" class="btn btn-brand" [disabled]="bloqueando()">
              @if (bloqueando()) { <span class="spinner"></span> } @else { <app-icon name="slash" [size]="16" /> } Bloquear IP
            </button>
          </form>
        </section>

        <section class="card card-pad stack" aria-labelledby="h-reglas">
          <h2 id="h-reglas">Reglas automáticas</h2>
          <ul class="reglas">
            <li><strong>Exceso de peticiones.</strong> Más de 120 por minuto desde una IP la bloquea 30 minutos.</li>
            <li><strong>Fuerza bruta.</strong> 5 respuestas «no autorizado» en el login o en el código 2FA en 15 minutos bloquean la IP 30 minutos.</li>
            <li><strong>Cuentas.</strong> 5 intentos fallidos bloquean la cuenta del usuario 15 minutos.</li>
          </ul>
          <p class="small muted">Si usas Docker, todas las peticiones llegan con la misma IP del puente: bloquearla deja sin acceso a todos.</p>
        </section>
      </div>
    </div>
  `,
})
export class AdminSeguridadComponent {
  private readonly api = inject(IpsService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly lista = signal<IpBloqueada[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly trabajando = signal<string | null>(null);
  protected readonly bloqueando = signal(false);
  private readonly ahora = signal(Date.now());
  protected readonly hayLista = computed(() => this.lista().length > 0);

  protected readonly form = this.fb.nonNullable.group({
    ip: ['', [Validators.required, Validadores.ip]],
    minutos: [60, [Validators.required, Validators.min(1), Validators.max(10080)]],
    motivo: ['', [Validators.maxLength(200)]],
  });

  constructor() {
    this.cargar();
  }

  protected err(campo: string): string | null {
    return errorDeCampo(this.form, campo);
  }

  protected permanente(b: IpBloqueada): boolean {
    return b.hasta === 'permanente';
  }

  protected restante(b: IpBloqueada): string {
    const r = tiempoRestante(new Date(b.hasta).getTime(), this.ahora());
    return r ? `Quedan ${r}` : 'Vence en instantes';
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.listar().subscribe({
      next: (l) => {
        this.ahora.set(Date.now());
        // Permanentes primero, luego por fecha de vencimiento
        this.lista.set([...l].sort((a, b) => (a.hasta === 'permanente' ? -1 : b.hasta === 'permanente' ? 1 : a.hasta.localeCompare(b.hasta))));
        this.cargando.set(false);
      },
      error: (e: unknown) => {
        this.error.set(mensajeDe(e));
        this.cargando.set(false);
      },
    });
  }

  protected async bloquear(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const ip = v.ip.trim();
    const ok = await this.confirm.preguntar({
      titulo: 'Bloquear dirección IP',
      mensaje: `Se bloqueará ${ip} por ${v.minutos} minutos. Si es una IP compartida (oficina, red del instituto o el puente de Docker), todas las personas que la usen perderán el acceso.`,
      confirmar: 'Bloquear',
      peligro: true,
    });
    if (!ok) return;
    this.bloqueando.set(true);
    this.api.bloquear({ ip, minutos: Number(v.minutos), motivo: v.motivo.trim() || null }).subscribe({
      next: () => {
        this.bloqueando.set(false);
        this.toast.exito(`IP ${ip} bloqueada.`);
        this.form.reset({ ip: '', minutos: 60, motivo: '' });
        this.cargar();
      },
      error: (e: unknown) => {
        this.bloqueando.set(false);
        this.toast.error(mensajeDe(e));
      },
    });
  }

  protected async desbloquear(b: IpBloqueada): Promise<void> {
    const ok = await this.confirm.preguntar({
      titulo: 'Desbloquear dirección IP',
      mensaje: `${b.ip} podrá volver a usar la plataforma de inmediato.`,
      confirmar: 'Desbloquear',
    });
    if (!ok) return;
    this.trabajando.set(b.ip);
    this.api.desbloquear(b.ip).subscribe({
      next: () => {
        this.trabajando.set(null);
        this.toast.exito(`IP ${b.ip} desbloqueada.`);
        this.cargar();
      },
      error: (e: unknown) => {
        this.trabajando.set(null);
        this.toast.error(mensajeDe(e));
      },
    });
  }
}
