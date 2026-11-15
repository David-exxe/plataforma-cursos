import { Routes } from '@angular/router';
import { adminGuard, authGuard, guestGuard } from './core/auth/guards';
import { AdminShellComponent } from './layout/admin-shell.component';
import { PublicShellComponent } from './layout/public-shell.component';

/**
 * Rutas que el backend ya espera (ver GUIA.md y MercadoPagoClient / Mensajes):
 *  - /pago/resultado?estado=aprobado|rechazado|pendiente&inscripcion=ID   (back_urls de Mercado Pago)
 *  - /pago-simulado?inscripcion=ID                                        (link de pago con MP_MODO=mock)
 *  - /mis-inscripciones                                                   (enlace dentro de los WhatsApp)
 */
export const routes: Routes = [
  // Pantallas de acceso: layout propio a pantalla completa
  {
    path: 'ingresar',
    canActivate: [guestGuard],
    title: 'Ingresar',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'registro',
    canActivate: [guestGuard],
    title: 'Crear cuenta',
    loadComponent: () => import('./features/auth/registro.component').then((m) => m.RegistroComponent),
  },

  // Panel de administración (rol ADMIN)
  {
    path: 'admin',
    component: AdminShellComponent,
    canActivate: [authGuard, adminGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'resumen' },
      {
        path: 'resumen',
        title: 'Resumen',
        loadComponent: () => import('./features/admin/resumen.component').then((m) => m.ResumenComponent),
      },
      {
        path: 'cursos',
        title: 'Cursos · Admin',
        loadComponent: () => import('./features/admin/admin-cursos.component').then((m) => m.AdminCursosComponent),
      },
      {
        path: 'inscripciones',
        title: 'Inscripciones · Admin',
        loadComponent: () => import('./features/admin/admin-inscripciones.component').then((m) => m.AdminInscripcionesComponent),
      },
      {
        path: 'pagos',
        title: 'Pagos · Admin',
        loadComponent: () => import('./features/admin/admin-pagos.component').then((m) => m.AdminPagosComponent),
      },
      {
        path: 'comprobantes',
        title: 'Comprobantes · Admin',
        loadComponent: () => import('./features/admin/admin-comprobantes.component').then((m) => m.AdminComprobantesComponent),
      },
      {
        path: 'notificaciones',
        title: 'Notificaciones · Admin',
        loadComponent: () => import('./features/admin/admin-notificaciones.component').then((m) => m.AdminNotificacionesComponent),
      },
      {
        path: 'seguridad',
        title: 'Seguridad · Admin',
        loadComponent: () => import('./features/admin/admin-seguridad.component').then((m) => m.AdminSeguridadComponent),
      },
    ],
  },

  // Sitio del estudiante
  {
    path: '',
    component: PublicShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'cursos' },
      {
        path: 'cursos',
        title: 'Cursos',
        loadComponent: () => import('./features/cursos/catalogo.component').then((m) => m.CatalogoComponent),
      },
      {
        path: 'cursos/:id',
        title: 'Detalle del curso',
        loadComponent: () => import('./features/cursos/curso-detalle.component').then((m) => m.CursoDetalleComponent),
      },
      {
        path: 'mis-inscripciones',
        canActivate: [authGuard],
        title: 'Mis inscripciones',
        loadComponent: () => import('./features/inscripciones/mis-inscripciones.component').then((m) => m.MisInscripcionesComponent),
      },
      {
        path: 'comprobantes',
        canActivate: [authGuard],
        title: 'Mis comprobantes',
        loadComponent: () => import('./features/comprobantes/mis-comprobantes.component').then((m) => m.MisComprobantesComponent),
      },
      {
        path: 'cuenta',
        canActivate: [authGuard],
        title: 'Mi cuenta',
        loadComponent: () => import('./features/cuenta/cuenta.component').then((m) => m.CuentaComponent),
      },
      {
        path: 'pago/resultado',
        canActivate: [authGuard],
        title: 'Resultado del pago',
        loadComponent: () => import('./features/pagos/pago-resultado.component').then((m) => m.PagoResultadoComponent),
      },
      {
        path: 'pago-simulado',
        canActivate: [authGuard],
        title: 'Pago de prueba',
        loadComponent: () => import('./features/pagos/pago-simulado.component').then((m) => m.PagoSimuladoComponent),
      },
      {
        path: '**',
        title: 'Página no encontrada',
        loadComponent: () => import('./features/no-encontrado.component').then((m) => m.NoEncontradoComponent),
      },
    ],
  },
];
