import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, forkJoin, of } from 'rxjs';
import { mensajeDe } from '../../core/http/api-error';
import { CursoAdmin, CursoRequest, Inscripcion, Modalidad, Moneda } from '../../core/models';
import { CursosService } from '../../core/services/cursos.service';
import { InscripcionesService } from '../../core/services/inscripciones.service';
import { ToastService } from '../../core/services/toast.service';
import { hoyLima } from '../../core/util/fechas';
import { normalizar } from '../../core/util/texto';
import { Validadores, aplicarErroresServidor, errorDeCampo } from '../../shared/forms';
import { IconComponent } from '../../shared/icon.component';
import { ModalComponent } from '../../shared/modal.component';
import { PaginacionComponent } from '../../shared/paginacion.component';
import { FechaPipe, MoneyPipe } from '../../shared/pipes';
import { VacioComponent } from '../../shared/vacio.component';

type FiltroCurso = '' | 'publicados' | 'ocultos';
const TAM = 10;

@Component({
  selector: 'app-admin-cursos',
  imports: [ReactiveFormsModule, IconComponent, ModalComponent, PaginacionComponent, VacioComponent, MoneyPipe, FechaPipe],
  template: `
    <header class="page-head row">
      <div><h1>Cursos</h1><p class="muted">Crea y edita el catálogo. Solo los cursos activos y por empezar aparecen en el sitio.</p></div>
      <span class="spacer"></span>
      <button type="button" class="btn btn-brand" (click)="nuevo()"><app-icon name="plus" [size]="16" /> Nuevo curso</button>
    </header>

    <div class="toolbar">
      <label class="search">
        <app-icon name="search" [size]="18" />
        <input type="search" placeholder="Buscar por nombre o código" [value]="q()" (input)="buscar($any($event.target).value)" aria-label="Buscar cursos" />
      </label>
      <div class="seg" role="group" aria-label="Filtrar por publicación">
        @for (f of filtros; track f.valor) {
          <button type="button" [class.on]="filtro() === f.valor" [attr.aria-pressed]="filtro() === f.valor" (click)="cambiarFiltro(f.valor)">{{ f.texto }}</button>
        }
      </div>
      <span class="spacer"></span>
      <button type="button" class="btn btn-ghost btn-sm" [disabled]="cargando()" (click)="cargar()"><app-icon name="refresh" [size]="16" /> Actualizar</button>
    </div>

    @if (cargando()) {
      <div class="skel" style="height:320px" aria-busy="true"></div>
    } @else if (error()) {
      <app-vacio tono="error" icono="alert-circle" titulo="No pudimos cargar los cursos" [texto]="error()!">
        <button type="button" class="btn btn-brand" (click)="cargar()">Reintentar</button>
      </app-vacio>
    } @else if (filtrados().length === 0) {
      <app-vacio icono="book-open" titulo="No hay cursos para mostrar" texto="Crea el primero o cambia los filtros.">
        <button type="button" class="btn btn-brand" (click)="nuevo()">Nuevo curso</button>
      </app-vacio>
    } @else {
      <div class="table-wrap">
        <table class="tbl">
          <thead>
            <tr><th>Código</th><th>Curso</th><th>Inicio</th><th class="num">Precio</th><th>Cupos ocupados</th><th>Publicación</th><th><span class="sr-only">Acciones</span></th></tr>
          </thead>
          <tbody>
            @for (c of visibles(); track c.id) {
              <tr>
                <td class="mono">{{ c.codigo }}</td>
                <td><strong>{{ c.nombre }}</strong><span class="cell-sub">{{ c.modalidad === 'ONLINE' ? 'Online' : 'Presencial' }} · {{ c.horario || 'Sin horario' }}</span></td>
                <td>{{ c.fechaInicio | fecha: 'corta' }}</td>
                <td class="num">{{ c.precio | money: c.moneda }}</td>
                <td>
                  <div class="mini-barra" [class.llena]="ocupadosDe(c.id) >= c.cupos"><span [style.width.%]="pct(c)"></span></div>
                  <span class="cell-sub num">{{ ocupadosDe(c.id) }} de {{ c.cupos }}</span>
                </td>
                <td>
                  <span class="badge" [class]="'badge badge-' + publicacion(c).tono">{{ publicacion(c).texto }}</span>
                </td>
                <td class="acciones"><button type="button" class="btn btn-ghost btn-sm" (click)="editar(c)"><app-icon name="edit" [size]="15" /> Editar</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-paginacion [total]="filtrados().length" [tamano]="tam" [(pagina)]="pagina" />
    }

    <app-modal [abierto]="abierto()" [titulo]="editando() ? 'Editar curso' : 'Nuevo curso'" [lateral]="true" (cerrar)="abierto.set(false)">
      <form [formGroup]="form" (ngSubmit)="guardar()" novalidate class="stack">
        @if (errorForm()) { <div class="alert alert-bad" role="alert"><app-icon name="alert-circle" [size]="18" /><div>{{ errorForm() }}</div></div> }

        <div class="form-grid">
          <div class="field">
            <label for="c-codigo">Código</label>
            <input id="c-codigo" type="text" formControlName="codigo" maxlength="30" placeholder="SPR-02" [class.invalid]="!!err('codigo')" />
            @if (err('codigo'); as m) { <span class="error-msg">{{ m }}</span> } @else { <span class="hint">Único. Se guarda en mayúsculas.</span> }
          </div>
          <div class="field">
            <label for="c-modalidad">Modalidad</label>
            <select id="c-modalidad" formControlName="modalidad">
              <option value="ONLINE">Online</option>
              <option value="PRESENCIAL">Presencial</option>
            </select>
          </div>
        </div>

        <div class="field">
          <label for="c-nombre">Nombre del curso</label>
          <input id="c-nombre" type="text" formControlName="nombre" maxlength="150" [class.invalid]="!!err('nombre')" />
          @if (err('nombre'); as m) { <span class="error-msg">{{ m }}</span> }
        </div>

        <div class="field">
          <label for="c-desc">Descripción</label>
          <textarea id="c-desc" rows="4" formControlName="descripcion" [class.invalid]="!!err('descripcion')"></textarea>
          <span class="hint">Qué aprenderá el estudiante. Puedes separar párrafos con una línea en blanco.</span>
        </div>

        <div class="form-grid">
          <div class="field">
            <label for="c-precio">Precio</label>
            <input id="c-precio" type="number" step="0.01" min="0.01" formControlName="precio" inputmode="decimal" [class.invalid]="!!err('precio')" />
            @if (err('precio'); as m) { <span class="error-msg">{{ m }}</span> }
          </div>
          <div class="field">
            <label for="c-moneda">Moneda</label>
            <select id="c-moneda" formControlName="moneda"><option value="PEN">Soles (PEN)</option><option value="USD">Dólares (USD)</option></select>
          </div>
        </div>

        <div class="form-grid">
          <div class="field">
            <label for="c-fecha">Fecha de inicio</label>
            <input id="c-fecha" type="date" formControlName="fechaInicio" [min]="hoy" [class.invalid]="!!err('fechaInicio')" />
            @if (err('fechaInicio'); as m) { <span class="error-msg">{{ m }}</span> }
          </div>
          <div class="field">
            <label for="c-cupos">Cupos</label>
            <input id="c-cupos" type="number" min="1" step="1" formControlName="cupos" inputmode="numeric" [class.invalid]="!!err('cupos')" />
            @if (err('cupos'); as m) { <span class="error-msg">{{ m }}</span> }
            @else if (editando() && ocupadosDe(editando()!.id) > 0) { <span class="hint">Ocupados ahora: {{ ocupadosDe(editando()!.id) }}.</span> }
          </div>
        </div>

        <div class="field">
          <label for="c-horario">Horario</label>
          <input id="c-horario" type="text" formControlName="horario" maxlength="120" placeholder="Lun y Mié 7:00 pm - 10:00 pm" [class.invalid]="!!err('horario')" />
          @if (err('horario'); as m) { <span class="error-msg">{{ m }}</span> }
        </div>

        <div class="field">
          <label for="c-ubic">{{ form.controls.modalidad.value === 'ONLINE' ? 'Enlace de la clase' : 'Aula o sede' }}</label>
          <input id="c-ubic" type="text" formControlName="ubicacion" maxlength="255"
                 [placeholder]="form.controls.modalidad.value === 'ONLINE' ? 'https://meet.google.com/…' : 'Sede Centro - Aula 204'" [class.invalid]="!!err('ubicacion')" />
          <span class="hint">{{ form.controls.modalidad.value === 'ONLINE' ? 'Solo se envía a quien ya pagó.' : 'Se muestra en el sitio.' }}</span>
          @if (err('ubicacion'); as m) { <span class="error-msg">{{ m }}</span> }
        </div>

        <label class="check"><input type="checkbox" formControlName="activo" /> Curso activo (visible en el catálogo)</label>

        <div class="modal-actions">
          <button type="button" class="btn btn-ghost" (click)="abierto.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-brand" [disabled]="guardando()">
            @if (guardando()) { <span class="spinner"></span> } {{ editando() ? 'Guardar cambios' : 'Crear curso' }}
          </button>
        </div>
      </form>
    </app-modal>
  `,
})
export class AdminCursosComponent {
  private readonly cursos = inject(CursosService);
  private readonly inscripciones = inject(InscripcionesService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly tam = TAM;
  protected readonly hoy = hoyLima();
  protected readonly lista = signal<CursoAdmin[]>([]);
  private readonly ocupados = signal<Map<number, number>>(new Map());
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly q = signal('');
  protected readonly filtro = signal<FiltroCurso>('');
  protected readonly pagina = signal(1);
  protected readonly filtros: { valor: FiltroCurso; texto: string }[] = [
    { valor: '', texto: 'Todos' },
    { valor: 'publicados', texto: 'Publicados' },
    { valor: 'ocultos', texto: 'Ocultos' },
  ];

  protected readonly filtrados = computed(() => {
    const q = normalizar(this.q());
    const f = this.filtro();
    return this.lista().filter((c) => {
      const publicado = c.activo && c.fechaInicio >= this.hoy;
      if (f === 'publicados' && !publicado) return false;
      if (f === 'ocultos' && publicado) return false;
      return !q || normalizar(`${c.nombre} ${c.codigo}`).includes(q);
    });
  });
  protected readonly visibles = computed(() => this.filtrados().slice((this.pagina() - 1) * TAM, this.pagina() * TAM));

  protected readonly abierto = signal(false);
  protected readonly editando = signal<CursoAdmin | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(30)]],
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    descripcion: [''],
    modalidad: ['ONLINE' as Modalidad, [Validators.required]],
    precio: [null as number | null, [Validators.required, Validators.min(0.01)]],
    moneda: ['PEN' as Moneda, [Validators.required]],
    fechaInicio: ['', [Validators.required, Validadores.fechaNoPasada]],
    horario: ['', [Validators.maxLength(120)]],
    ubicacion: ['', [Validators.maxLength(255)]],
    cupos: [20, [Validators.required, Validators.min(1)]],
    activo: [true],
  });

  constructor() {
    this.cargar();
  }

  protected err(campo: string): string | null {
    return errorDeCampo(this.form, campo);
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    forkJoin({
      cursos: this.cursos.adminListar(),
      // La ocupación sale de las inscripciones; si ese listado falla, se muestra 0
      ins: this.inscripciones.admin().pipe(catchError(() => of([] as Inscripcion[]))),
    }).subscribe({
      next: ({ cursos, ins }) => {
        const m = new Map<number, number>();
        for (const i of ins) if (i.estado === 'PENDIENTE_PAGO' || i.estado === 'PAGADA') m.set(i.cursoId, (m.get(i.cursoId) ?? 0) + 1);
        this.ocupados.set(m);
        this.lista.set([...cursos].sort((a, b) => b.fechaInicio.localeCompare(a.fechaInicio)));
        this.cargando.set(false);
      },
      error: (e: unknown) => {
        this.error.set(mensajeDe(e));
        this.cargando.set(false);
      },
    });
  }

  protected ocupadosDe(id: number): number {
    return this.ocupados().get(id) ?? 0;
  }

  protected pct(c: CursoAdmin): number {
    return c.cupos > 0 ? Math.min(100, Math.round((this.ocupadosDe(c.id) / c.cupos) * 100)) : 0;
  }

  protected publicacion(c: CursoAdmin): { texto: string; tono: string } {
    if (!c.activo) return { texto: 'Oculto', tono: 'neutral' };
    if (c.fechaInicio < this.hoy) return { texto: 'Ya inició', tono: 'info' };
    return { texto: 'Publicado', tono: 'ok' };
  }

  protected buscar(v: string): void {
    this.q.set(v);
    this.pagina.set(1);
  }

  protected cambiarFiltro(v: FiltroCurso): void {
    this.filtro.set(v);
    this.pagina.set(1);
  }

  protected nuevo(): void {
    this.editando.set(null);
    this.errorForm.set(null);
    this.form.reset({
      codigo: '', nombre: '', descripcion: '', modalidad: 'ONLINE', precio: null, moneda: 'PEN',
      fechaInicio: '', horario: '', ubicacion: '', cupos: 20, activo: true,
    });
    this.abierto.set(true);
  }

  protected editar(c: CursoAdmin): void {
    this.editando.set(c);
    this.errorForm.set(null);
    this.form.reset({
      codigo: c.codigo, nombre: c.nombre, descripcion: c.descripcion ?? '', modalidad: c.modalidad, precio: c.precio,
      moneda: c.moneda, fechaInicio: c.fechaInicio, horario: c.horario ?? '', ubicacion: c.ubicacion ?? '',
      cupos: c.cupos, activo: c.activo,
    });
    this.abierto.set(true);
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const req: CursoRequest = {
      codigo: v.codigo.trim(),
      nombre: v.nombre.trim(),
      descripcion: v.descripcion.trim() || null,
      modalidad: v.modalidad,
      precio: Number(v.precio),
      moneda: v.moneda,
      fechaInicio: v.fechaInicio,
      horario: v.horario.trim() || null,
      ubicacion: v.ubicacion.trim() || null,
      cupos: Number(v.cupos),
      activo: v.activo,
    };
    const editando = this.editando();
    const peticion = editando ? this.cursos.adminActualizar(editando.id, req) : this.cursos.adminCrear(req);
    this.guardando.set(true);
    this.errorForm.set(null);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.abierto.set(false);
        this.toast.exito(editando ? 'Curso actualizado.' : 'Curso creado.');
        this.cargar();
      },
      error: (e: unknown) => {
        this.guardando.set(false);
        if (!aplicarErroresServidor(this.form, e)) this.errorForm.set(mensajeDe(e));
      },
    });
  }
}
