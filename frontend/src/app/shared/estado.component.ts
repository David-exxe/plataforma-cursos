import { Component, computed, input } from '@angular/core';

type Tono = 'ok' | 'warn' | 'bad' | 'info' | 'neutral';
type Familia = 'inscripcion' | 'pago' | 'envio' | 'notificacion';

const MAPA: Record<Familia, Record<string, [string, Tono]>> = {
  inscripcion: {
    PENDIENTE_PAGO: ['Pendiente de pago', 'warn'],
    PAGADA: ['Pagada', 'ok'],
    VENCIDA: ['Vencida', 'neutral'],
    CANCELADA: ['Cancelada', 'neutral'],
  },
  pago: {
    PENDIENTE: ['Pendiente', 'warn'],
    EN_PROCESO: ['En proceso', 'info'],
    APROBADO: ['Aprobado', 'ok'],
    RECHAZADO: ['Rechazado', 'bad'],
    REEMBOLSADO: ['Reembolsado', 'neutral'],
  },
  envio: {
    PENDIENTE: ['En cola', 'info'],
    ENVIADO: ['Enviado', 'ok'],
    ERROR: ['Reintentando', 'warn'],
    FALLIDO: ['Falló', 'bad'],
  },
  notificacion: {
    PENDIENTE: ['En cola', 'info'],
    ENVIADO: ['Enviado', 'ok'],
    ENTREGADO: ['Entregado', 'ok'],
    LEIDO: ['Leído', 'ok'],
    FALLIDO: ['Falló', 'bad'],
  },
};

/** Tipos de notificación que genera notificaciones-service (ver Mensajes.java y BotService). */
const TIPOS_NOTIFICACION: Record<string, string> = {
  INSCRIPCION_RECIBIDA: 'Inscripción recibida',
  PAGO_RECIBIDO: 'Pago recibido',
  PAGO_RECHAZADO: 'Pago rechazado',
  RECORDATORIO_INICIO: 'Recordatorio de inicio',
  COMPROBANTE_ENVIADO: 'Comprobante enviado',
  ALERTA_COMPROBANTE: 'Alerta al administrador',
  DERIVACION_ASESOR: 'Derivación a asesor',
};

export function etiquetaTipoNotificacion(tipo: string): string {
  if (TIPOS_NOTIFICACION[tipo]) return TIPOS_NOTIFICACION[tipo];
  const t = tipo.toLowerCase().replace(/_/g, ' ');
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/** Etiqueta de estado con color y texto (nunca solo color). */
@Component({
  selector: 'app-estado',
  template: `<span class="badge" [class]="'badge badge-' + info().tono">{{ info().texto }}</span>`,
})
export class EstadoComponent {
  readonly tipo = input.required<Familia>();
  readonly valor = input.required<string>();

  protected readonly info = computed(() => {
    const v = this.valor();
    const par = MAPA[this.tipo()][v];
    return { texto: par ? par[0] : v, tono: par ? par[1] : ('neutral' as Tono) };
  });
}
