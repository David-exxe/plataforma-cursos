import { AbstractControl, FormGroup, ValidationErrors } from '@angular/forms';
import { ApiError } from '../core/http/api-error';
import { hoyLima } from '../core/util/fechas';

/** Espejo de las validaciones Jakarta del backend (AuthDtos, CursoRequest) para avisar antes de enviar. */
export const Validadores = {
  /** ^\+?[0-9 ]{9,15}$ */
  telefono: (c: AbstractControl): ValidationErrors | null =>
    /^\+?[0-9 ]{9,15}$/.test(String(c.value ?? '')) ? null : { telefono: true },

  /** Opcional. ^[0-9A-Za-z]{8,12}$ */
  documento: (c: AbstractControl): ValidationErrors | null => {
    const v = String(c.value ?? '');
    return v === '' || /^[0-9A-Za-z]{8,12}$/.test(v) ? null : { documento: true };
  },

  /** 8–72 caracteres con al menos una letra y un número. */
  password: (c: AbstractControl): ValidationErrors | null =>
    /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(String(c.value ?? '')) ? null : { password: true },

  /** @FutureOrPresent: hoy (Lima) o posterior. */
  fechaNoPasada: (c: AbstractControl): ValidationErrors | null => {
    const v = String(c.value ?? '');
    return !v || v >= hoyLima() ? null : { pasada: true };
  },

  /** IPv4 o IPv6 exacta (el gateway bloquea por coincidencia exacta). */
  ip: (c: AbstractControl): ValidationErrors | null => {
    const v = String(c.value ?? '').trim();
    const v4 = /^(\d{1,3}\.){3}\d{1,3}$/.test(v) && v.split('.').every((n) => Number(n) <= 255);
    const v6 = /^[0-9a-fA-F:]+$/.test(v) && v.includes(':');
    return v4 || v6 ? null : { ip: true };
  },
};

/** Copia los errores por campo que devuelve el backend (400 con `detalle`) a los controles del formulario. */
export function aplicarErroresServidor(form: FormGroup, e: unknown): boolean {
  if (!(e instanceof ApiError) || !e.detalle) return false;
  let alguno = false;
  for (const [campo, msg] of Object.entries(e.detalle)) {
    const c = form.get(campo);
    if (c) {
      c.setErrors({ servidor: msg });
      c.markAsTouched();
      alguno = true;
    }
  }
  return alguno;
}

/** Mensaje de error del campo (solo si fue tocado). Un único lugar para todos los textos de validación. */
export function errorDeCampo(form: FormGroup, nombre: string): string | null {
  const c = form.get(nombre);
  if (!c || !c.touched || !c.errors) return null;
  const e = c.errors;
  if (e['servidor']) return String(e['servidor']);
  if (e['required']) return 'Este campo es obligatorio.';
  if (e['email']) return 'Escribe un correo válido, por ejemplo nombre@correo.com.';
  if (e['telefono']) return 'Escribe un celular válido (9 a 15 dígitos, puede incluir +).';
  if (e['documento']) return 'El documento debe tener entre 8 y 12 letras o números.';
  if (e['password']) return 'Usa entre 8 y 72 caracteres, con al menos una letra y un número.';
  if (e['pasada']) return 'La fecha de inicio no puede ser anterior a hoy.';
  if (e['ip']) return 'Escribe una dirección IP válida (IPv4 o IPv6).';
  if (e['maxlength']) return `Máximo ${e['maxlength'].requiredLength} caracteres.`;
  if (e['minlength']) return `Mínimo ${e['minlength'].requiredLength} caracteres.`;
  if (e['min']) return `El valor mínimo es ${e['min'].min}.`;
  if (e['max']) return `El valor máximo es ${e['max'].max}.`;
  return 'Valor no válido.';
}
