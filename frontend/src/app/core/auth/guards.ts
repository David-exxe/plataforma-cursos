import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ToastService } from '../services/toast.service';
import { AuthService } from './auth.service';

/** Exige sesión vigente; conserva la URL completa (con query) para volver después de ingresar. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.tokenVigente()) return true;
  return inject(Router).createUrlTree(['/ingresar'], { queryParams: { returnUrl: state.url } });
};

/** Solo rol ADMIN (el gateway y cada servicio lo vuelven a validar: esto es solo experiencia de uso). */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.esAdmin()) return true;
  inject(ToastService).error('No tienes permisos para entrar al panel de administración.');
  return inject(Router).createUrlTree(['/cursos']);
};

/** Login y registro no tienen sentido con sesión iniciada. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.tokenVigente()) return true;
  return inject(Router).createUrlTree([auth.esAdmin() ? '/admin' : '/cursos']);
};
