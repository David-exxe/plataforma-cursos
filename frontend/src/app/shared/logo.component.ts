import { Component } from '@angular/core';

/** Marca: un boleto con perforación. El cupo es el producto. */
@Component({
  selector: 'app-logo',
  template: `
    <svg viewBox="0 0 32 32" width="30" height="30" aria-hidden="true" focusable="false">
      <rect x="2" y="6" width="28" height="20" rx="5" fill="#0A6E80" />
      <circle class="logo-notch" cx="2" cy="16" r="4" />
      <circle class="logo-notch" cx="30" cy="16" r="4" />
      <path d="M12 11v10" stroke="#F6B40E" stroke-width="2" stroke-dasharray="2 2.5" stroke-linecap="round" />
      <path d="M17 12h6M17 16h6M17 20h4" stroke="#fff" stroke-width="2" stroke-linecap="round" />
    </svg>
  `,
})
export class LogoComponent {}
