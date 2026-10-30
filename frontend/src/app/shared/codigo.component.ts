import { Component, ElementRef, afterNextRender, input, model, output, viewChild } from '@angular/core';

/** Campo de 6 dígitos para códigos TOTP (Google/Microsoft Authenticator). Emite `completo` al llegar a 6. */
@Component({
  selector: 'app-codigo',
  template: `
    <input #campo class="codigo" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6"
           placeholder="000000" [value]="valor()" [disabled]="deshabilitado()" [attr.aria-label]="etiqueta()"
           (input)="alEscribir($event)" />
  `,
})
export class CodigoComponent {
  readonly valor = model('');
  readonly etiqueta = input('Código de 6 dígitos');
  readonly deshabilitado = input(false);
  readonly autoenfocar = input(false);
  readonly completo = output<string>();

  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');

  constructor() {
    afterNextRender(() => {
      if (this.autoenfocar()) this.campo()?.nativeElement.focus();
    });
  }

  protected alEscribir(ev: Event): void {
    const el = ev.target as HTMLInputElement;
    const limpio = el.value.replace(/\D/g, '').slice(0, 6);
    el.value = limpio;
    this.valor.set(limpio);
    if (limpio.length === 6) this.completo.emit(limpio);
  }
}
