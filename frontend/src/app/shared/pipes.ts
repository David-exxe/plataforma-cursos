import { Pipe, PipeTransform } from '@angular/core';
import { etiquetaInicio, fechaCorta, fechaHora, fechaLarga } from '../core/util/fechas';

/** {{ 250 | money:'PEN' }} → "S/ 250.00" */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(valor: number | null | undefined, moneda: string | null | undefined = 'PEN'): string {
    if (valor === null || valor === undefined) return '—';
    try {
      return new Intl.NumberFormat('es-PE', { style: 'currency', currency: moneda || 'PEN' }).format(valor);
    } catch {
      return `${moneda ?? ''} ${valor}`.trim();
    }
  }
}

/** LocalDate: 'corta' | 'larga' | 'relativa'.  Instant: 'hora'. */
@Pipe({ name: 'fecha' })
export class FechaPipe implements PipeTransform {
  transform(valor: string | null | undefined, formato: 'corta' | 'larga' | 'hora' | 'relativa' = 'corta'): string {
    if (!valor) return '—';
    switch (formato) {
      case 'larga': return fechaLarga(valor);
      case 'hora': return fechaHora(valor);
      case 'relativa': return etiquetaInicio(valor);
      default: return fechaCorta(valor);
    }
  }
}
